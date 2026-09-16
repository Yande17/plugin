package me.w2n.w2nsmp.stats;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.bounty.BountyEntry;
import me.w2n.w2nsmp.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class StatisticsService {
   private static final int MAX_TRACKED = 10000;
   private final W2NSMP plugin;
   private final StatsStorage storage;
   private final Object writeLock = new Object();
   private final AtomicLong writeVersion = new AtomicLong();
   private final AtomicLong writtenVersion = new AtomicLong();
   private final Map<UUID, PlayerStats> cache = new HashMap<>();
   private final Map<UUID, String> lastKnownNames = new HashMap<>();
   private final Map<UUID, Long> sessionStart = new HashMap<>();
   private StatsStorage.Mode mode = StatsStorage.Mode.YAML;
   private BukkitTask autosaveTask;
   private BukkitTask highestMoneyTask;
   private BukkitTask playtimeTask;
   private boolean loaded;
   private boolean writeQueued;
   private volatile boolean dirty;
   private final Object rankLock = new Object();
   private final Map<TopCategory, List<LeaderboardEntry>> topCache = new EnumMap<>(TopCategory.class);
   private final Map<TopCategory, Map<UUID, Integer>> rankCache = new EnumMap<>(TopCategory.class);
   private int dataVersion;
   private long cachedAt;

   // Cache saldo untuk pemain OFFLINE. Membaca saldo pemain offline lewat Vault/Essentials
   // bisa menyentuh disk, jadi hasilnya disimpan dan hanya disegarkan saat pemain online/keluar.
   private final Map<UUID, Long> moneyCache = new ConcurrentHashMap<>();

   // Jejak versi data yang sudah berhasil ditulis (dipakai mode SQLITE untuk tulis parsial).
   private final Map<UUID, Long> writtenStamps = new ConcurrentHashMap<>();
   private final Set<UUID> pendingDeletes = ConcurrentHashMap.newKeySet();
   private volatile boolean wipeRequested;

   public StatisticsService(W2NSMP plugin) {
      super();
      this.plugin = plugin;
      this.storage = new StatsStorage(plugin);
   }

   private void bumpDataVersion() {
      this.dataVersion++;
   }

   public boolean load() {
      this.cache.clear();
      this.lastKnownNames.clear();
      this.sessionStart.clear();
      if (!this.plugin.config().statisticsEnabled()) {
         this.loaded = false;
         this.plugin.getLogger().info("Statistik pemain dimatikan lewat config.yml (statistics.enabled: false).");
         return false;
      }

      StatsStorage.Mode configured = StatsStorage.Mode.fromConfig(this.plugin.config().statisticsStorage());
      if (configured == null) {
         this.plugin.getLogger().warning("statistics.storage berisi nilai tidak dikenal (" + this.plugin.config().statisticsStorage() + "); memakai YAML.");
      }

      this.mode = configured == null ? StatsStorage.Mode.YAML : configured;
      if (this.mode == StatsStorage.Mode.SQLITE && !this.storage.sqliteDriverAvailable()) {
         this.plugin.getLogger().warning("statistics.storage=SQLITE tetapi driver SQLite tidak tersedia di classpath; plugin memakai YAML (statistics.yml).");
         this.mode = StatsStorage.Mode.YAML;
      }

      int count;
      if (this.mode == StatsStorage.Mode.SQLITE) {
         count = this.loadSqlite();
         if (count < 0) {
            this.plugin.getLogger().warning("Gagal membaca w2nsmp.db; data lama tidak dimuat, statistik baru akan tetap disimpan.");
            count = 0;
         }
      } else {
         Map<UUID, PlayerStats> data = this.storage.loadYaml();
         this.cache.putAll(data);
         count = data.size();
      }

      this.loaded = true;
      this.bumpDataVersion();
      this.plugin
         .getLogger()
         .info("Statistik: " + count + " pemain dimuat dari " + this.storage.fileFor(this.mode).getName() + " (storage=" + this.mode.name() + ").");
      return true;
   }

   public void reload() {
      this.saveBlocking("reload");
      this.load();
      this.startTasks();
   }

   public void startTasks() {
      this.stopTasks();
      if (this.isEnabled()) {
         int minutes = this.plugin.config().statisticsAutosaveMinutes();
         if (minutes > 0) {
            long ticks = minutes * 60L * 20L;
            this.autosaveTask = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
               if (this.dirty) {
                  this.saveAsync();
               }
            }, ticks, ticks);
         }

         int seconds = this.plugin.config().statisticsHighestMoneySeconds();
         if (seconds > 0) {
            long ticks = seconds * 20L;
            this.highestMoneyTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::trackHighestMoneyAll, ticks, ticks);
         }

         int flushSeconds = this.plugin.config().statisticsPlaytimeFlushSeconds();
         if (flushSeconds > 0 && this.plugin.config().statisticsTrackPlaytime()) {
            long ticks = flushSeconds * 20L;
            this.playtimeTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::flushPlaytime, ticks, ticks);
         }
      }
   }

   public void stopTasks() {
      if (this.playtimeTask != null) {
         this.playtimeTask.cancel();
         this.playtimeTask = null;
      }

      if (this.highestMoneyTask != null) {
         this.highestMoneyTask.cancel();
         this.highestMoneyTask = null;
      }

      if (this.autosaveTask != null) {
         this.autosaveTask.cancel();
         this.autosaveTask = null;
      }
   }

   public void shutdown() {
      for (Player player : Bukkit.getOnlinePlayers()) {
         this.endSession(player);
      }

      this.stopTasks();
      this.saveBlocking("shutdown");
   }

   public boolean autosaveRunning() {
      return this.autosaveTask != null && !this.autosaveTask.isCancelled();
   }

   public boolean isEnabled() {
      return this.loaded && this.plugin.config().statisticsEnabled();
   }

   public StatsStorage.Mode mode() {
      return this.mode;
   }

   public File file() {
      return this.storage.fileFor(this.mode);
   }

   public File fileFor(StatsStorage.Mode mode) {
      return this.storage.fileFor(mode);
   }

   public int size() {
      return this.cache.size();
   }

   public Map<UUID, String> knownNames() {
      return new LinkedHashMap<>(this.lastKnownNames);
   }

   public void onJoin(Player player) {
      if (this.isEnabled()) {
         long now = System.currentTimeMillis();
         PlayerStats stats = this.data(player.getUniqueId());
         stats.name(player.getName());
         if (stats.firstSeen() <= 0L) {
            stats.firstSeen(now);
         }

         stats.lastSeen(now);
         this.lastKnownNames.put(player.getUniqueId(), player.getName());
         this.sessionStart.put(player.getUniqueId(), now);
         this.moneyCache.remove(player.getUniqueId());
         this.dirty = true;
         this.bumpDataVersion();
         this.trackHighestMoney(player);
      }
   }

   public void onQuit(Player player) {
      if (this.isEnabled()) {
         this.endSession(player);
         this.trackHighestMoney(player);
         PlayerStats stats = this.data(player.getUniqueId());
         stats.name(player.getName());
         stats.lastSeen(System.currentTimeMillis());
         this.dirty = true;
         this.bumpDataVersion();
         this.saveAsync();
      }
   }

   private void endSession(Player player) {
      UUID uniqueId = player.getUniqueId();
      Long start = this.sessionStart.remove(uniqueId);
      if (start != null && this.plugin.config().statisticsTrackPlaytime()) {
         long seconds = Math.max(0L, (System.currentTimeMillis() - start) / 1000L);
         this.add(uniqueId, StatType.PLAYTIME, seconds);
      }
   }

   public void add(Player player, StatType type, long amount) {
      if (player != null) {
         if (this.isEnabled()) {
            this.lastKnownNames.put(player.getUniqueId(), player.getName());
            PlayerStats stats = this.data(player.getUniqueId());
            stats.name(player.getName());
            if (stats.firstSeen() <= 0L) {
               stats.firstSeen(System.currentTimeMillis());
            }

            stats.add(type, amount);
            this.dirty = true;
            this.bumpDataVersion();
         }
      }
   }

   public void set(Player player, StatType type, long value) {
      if (player != null && type != null && this.isEnabled()) {
         PlayerStats stats = this.data(player.getUniqueId());
         stats.name(player.getName());
         if (stats.firstSeen() <= 0L) {
            stats.firstSeen(System.currentTimeMillis());
         }

         stats.value(type, value);
         this.lastKnownNames.put(player.getUniqueId(), player.getName());
         this.dirty = true;
         this.bumpDataVersion();
      }
   }

   public void add(UUID uniqueId, StatType type, long amount) {
      if (uniqueId != null && this.isEnabled()) {
         PlayerStats stats = this.data(uniqueId);
         if (this.name(uniqueId) != null) {
            stats.name(this.name(uniqueId));
         }

         stats.add(type, amount);
         this.dirty = true;
         this.bumpDataVersion();
      }
   }

   public void flushPlaytime() {
      if (this.isEnabled() && this.plugin.config().statisticsTrackPlaytime()) {
         long now = System.currentTimeMillis();

         for (Player player : Bukkit.getOnlinePlayers()) {
            Long start = this.sessionStart.get(player.getUniqueId());
            if (start != null && now - start >= 1000L) {
               this.sessionStart.put(player.getUniqueId(), now);
               PlayerStats stats = this.data(player.getUniqueId());
               stats.add(StatType.PLAYTIME, (now - start) / 1000L);
               stats.lastSeen(now);
               this.dirty = true;
               this.bumpDataVersion();
            }
         }
      }
   }

   public boolean trackHighestMoney(Player player) {
      if (player != null && this.isEnabled() && this.plugin.economy() != null && this.plugin.economy().isEnabled()) {
         double balance = this.plugin.economy().balance(player);
         if (!Double.isNaN(balance)) {
            this.moneyCache.put(player.getUniqueId(), (long)Math.floor(Math.max(0.0D, balance)));
         }

         if (!Double.isNaN(balance) && !(balance <= 0.0)) {
            long value = (long)Math.floor(balance);
            if (value <= this.value(player.getUniqueId(), StatType.HIGHEST_MONEY)) {
               return false;
            }

            this.set(player, StatType.HIGHEST_MONEY, value);
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public int trackHighestMoneyAll() {
      if (!this.isEnabled()) {
         return 0;
      }

      int updated = 0;

      for (Player player : Bukkit.getOnlinePlayers()) {
         if (this.trackHighestMoney(player)) {
            updated++;
         }
      }

      return updated;
   }

   public boolean highestMoneyTaskRunning() {
      return this.highestMoneyTask != null && !this.highestMoneyTask.isCancelled();
   }

   public PlayerStats data(UUID uniqueId) {
      return this.cache.computeIfAbsent(uniqueId, PlayerStats::new);
   }

   public UUID findUniqueId(String name) {
      if (name != null && !name.isBlank()) {
         Player online = Bukkit.getPlayerExact(name);
         if (online != null) {
            return online.getUniqueId();
         }

         for (Entry<UUID, String> entry : this.lastKnownNames.entrySet()) {
            if (entry.getValue() != null && entry.getValue().equalsIgnoreCase(name)) {
               return entry.getKey();
            }
         }

         for (Entry<UUID, PlayerStats> entry : this.cache.entrySet()) {
            if (entry.getValue().name() != null && entry.getValue().name().equalsIgnoreCase(name)) {
               return entry.getKey();
            }
         }

         // getOfflinePlayer(String) versi lama bisa memicu lookup nama->UUID yang memblokir
         // main thread. Versi "IfCached" hanya memakai cache server dan langsung kembali null.
         OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(name);
         if (offline == null) {
            this.plugin.debug("Statistik: nama '" + name + "' tidak ada di cache server; lookup blocking dilewati.");
            return null;
         }

         return offline.hasPlayedBefore() ? offline.getUniqueId() : null;
      } else {
         return null;
      }
   }

   public String name(UUID uniqueId) {
      if (uniqueId == null) {
         return null;
      }

      Player online = Bukkit.getPlayer(uniqueId);
      if (online != null) {
         return online.getName();
      }

      String cached = this.lastKnownNames.get(uniqueId);
      if (cached != null) {
         return cached;
      }

      String stored = this.data(uniqueId).name();
      return stored != null ? stored : shortName(uniqueId);
   }

   public static String shortName(UUID uniqueId) {
      String raw = uniqueId.toString().replace("-", "");
      return raw.substring(0, 8);
   }

   public long value(UUID uniqueId, StatType type) {
      PlayerStats stats = this.cache.get(uniqueId);
      long stored = stats == null ? 0L : stats.value(type);
      // Playtime pemain yang masih online ditambah sesi berjalan yang belum di-flush, supaya
      // sidebar/profil/peringkat tidak menampilkan angka yang berhenti naik selama bermain.
      return type == StatType.PLAYTIME ? stored + this.sessionSeconds(uniqueId) : stored;
   }

   /** Detik sesi berjalan yang belum disimpan (0 bila pemain offline atau fitur mati). */
   private long sessionSeconds(UUID uniqueId) {
      Long start = uniqueId == null ? null : this.sessionStart.get(uniqueId);
      if (start == null || !this.plugin.config().statisticsTrackPlaytime()) {
         return 0L;
      }

      return Math.max(0L, (System.currentTimeMillis() - start.longValue()) / 1000L);
   }

   /** Detik playtime tersimpan saja, tanpa sesi berjalan (dipakai saat menulis data). */
   public long storedPlaytime(UUID uniqueId) {
      PlayerStats stats = this.cache.get(uniqueId);
      return stats == null ? 0L : stats.value(StatType.PLAYTIME);
   }

   public boolean playtimeTaskRunning() {
      return this.playtimeTask != null && !this.playtimeTask.isCancelled();
   }

   public long value(UUID uniqueId, TopCategory category) {
      if (category == null) {
         return 0L;
      } else if (category.isBounty()) {
         return this.plugin.bounty() == null ? 0L : this.plugin.bounty().bounty(uniqueId);
      } else if (category.isMoney()) {
         return !this.plugin.config().statisticsEnabled() ? 0L : (long)Math.floor(this.money(uniqueId));
      } else {
         return this.value(uniqueId, category.statType());
      }
   }

   public double money(UUID uuid) {
      EconomyManager economy = this.plugin.economy();
      if (economy == null || !economy.isEnabled() || uuid == null) {
         return 0.0;
      }

      // Pemain online selalu dibaca langsung (murah) sekaligus menyegarkan cache.
      // Pemain offline memakai cache; hanya dibaca sungguhan sekali (belum ada di cache).
      if (Bukkit.getPlayer(uuid) == null && this.plugin.config().leaderboardCacheOfflineBalance()) {
         Long cached = this.moneyCache.get(uuid);
         if (cached != null) {
            return cached.doubleValue();
         }
      }

      return this.readBalance(economy, uuid);
   }

   private double readBalance(EconomyManager economy, UUID uuid) {
      try {
         double balance = economy.balance(Bukkit.getOfflinePlayer(uuid));
         if (Double.isNaN(balance)) {
            return 0.0;
         }

         this.moneyCache.put(uuid, (long)Math.floor(Math.max(0.0D, balance)));
         return balance;
      } catch (RuntimeException exception) {
         this.plugin.debug("Statistik: gagal membaca saldo " + uuid + " (" + exception.getMessage() + ").");
         return 0.0;
      }
   }

   /** Buang cache saldo seorang pemain (dipakai setelah transaksi/reset di luar W2NSMP). */
   public void invalidateBalance(UUID uniqueId) {
      if (uniqueId != null) {
         this.moneyCache.remove(uniqueId);
      }
   }

   public boolean reset(UUID uniqueId) {
      boolean removed = this.cache.remove(uniqueId) != null;
      boolean sessionCleared = this.sessionStart.remove(uniqueId) != null;
      if (uniqueId != null) {
         this.pendingDeletes.add(uniqueId);
         this.writtenStamps.remove(uniqueId);
         this.moneyCache.remove(uniqueId);
      }

      this.invalidateLeaderboards();
      if (removed || sessionCleared) {
         this.dirty = true;
         this.bumpDataVersion();
         this.saveAsync();
      }

      return removed || sessionCleared;
   }

   public int resetAll() {
      int count = this.cache.size();
      this.cache.clear();
      this.sessionStart.clear();
      this.writtenStamps.clear();
      this.moneyCache.clear();
      this.wipeRequested = true;
      this.invalidateLeaderboards();
      this.dirty = true;
      this.bumpDataVersion();
      this.saveAsync();
      return count;
   }

   public List<LeaderboardEntry> top(TopCategory category) {
      if (category != null && this.plugin.config().statisticsEnabled()) {
         synchronized (this.rankLock) {
            long now = System.currentTimeMillis();
            // Sebelumnya cache dibuang setiap kali satu statistik bertambah (bumpDataVersion),
            // sehingga papan peringkat dihitung ulang terus-menerus di main thread - cukup
            // satu blok ditambang untuk membatalkannya. Kini murni berdasarkan umur cache.
            long ttlMillis = this.plugin.config().leaderboardCacheSeconds() * 1000L;
            if (now - this.cachedAt > ttlMillis) {
               this.topCache.clear();
               this.rankCache.clear();
               this.cachedAt = now;
            }

            List<LeaderboardEntry> cached = this.topCache.get(category);
            if (cached != null) {
               return cached;
            }

            List<LeaderboardEntry> built = List.copyOf(this.buildTop(category));
            Map<UUID, Integer> ranks = new HashMap<>(Math.max(16, built.size() * 2));

            for (LeaderboardEntry entry : built) {
               ranks.put(entry.uniqueId(), entry.rank());
            }

            this.topCache.put(category, built);
            this.rankCache.put(category, ranks);
            return built;
         }
      } else {
         return List.of();
      }
   }

   /** Buang cache papan peringkat supaya perubahan data langsung terlihat (reset, admin). */
   public void invalidateLeaderboards() {
      synchronized (this.rankLock) {
         this.topCache.clear();
         this.rankCache.clear();
         this.cachedAt = 0L;
      }
   }

   public long cacheAgeMillis() {
      synchronized (this.rankLock) {
         return this.cachedAt <= 0L ? -1L : System.currentTimeMillis() - this.cachedAt;
      }
   }

   private List<LeaderboardEntry> buildTop(TopCategory category) {
      List<LeaderboardEntry> result = new ArrayList<>();
      if (category == null || !this.plugin.config().statisticsEnabled()) {
         return result;
      }

      if (!category.isBounty()) {
         this.flushPlaytime();
         // LinkedHashSet: tidak ada pemain yang "hilang" karena batas 10.000 entri pertama
         // (HashMap tidak punya urutan yang pasti), dan pengecekan pemain online jadi O(1).
         Set<UUID> ids = new LinkedHashSet<>(this.cache.size() + Bukkit.getOnlinePlayers().size() + 16);

         for (Entry<UUID, PlayerStats> entry : this.cache.entrySet()) {
            if (!entry.getValue().isEmpty()) {
               ids.add(entry.getKey());
            }
         }

         for (Player online : Bukkit.getOnlinePlayers()) {
            ids.add(online.getUniqueId());
         }

         List<LeaderboardEntry> rows = new ArrayList<>(ids.size());

         for (UUID uniqueId : ids) {
            long value = this.value(uniqueId, category);
            if (value > 0L) {
               rows.add(new LeaderboardEntry(0, uniqueId, this.name(uniqueId), value));
            }
         }

         rows.sort(
            Comparator.comparingLong(LeaderboardEntry::value)
               .reversed()
               .thenComparing(entryx -> entryx.name() == null ? "" : entryx.name().toLowerCase(Locale.ROOT))
         );

         // Batasi jumlah baris yang disimpan; pemain di luar batas dianggap "belum peringkat".
         int limit = Math.min(Math.max(1, this.plugin.config().leaderboardMaxEntries()), rows.size());

         for (int index = 0; index < limit; index++) {
            LeaderboardEntry entry = rows.get(index);
            result.add(new LeaderboardEntry(index + 1, entry.uniqueId(), entry.name(), entry.value()));
         }

         return result;
      } else {
         if (this.plugin.bounty() == null) {
            return result;
         }

         int rank = 1;

         for (BountyEntry entry : this.plugin.bounty().top()) {
            result.add(new LeaderboardEntry(rank++, entry.uniqueId(), entry.displayName(entry.uniqueId().toString()), entry.amount()));
         }

         return result;
      }
   }

   public int rankOf(UUID uniqueId, TopCategory category) {
      if (uniqueId != null && category != null) {
         this.top(category);
         synchronized (this.rankLock) {
            Map<UUID, Integer> ranks = this.rankCache.get(category);
            return ranks == null ? 0 : ranks.getOrDefault(uniqueId, 0);
         }
      } else {
         return 0;
      }
   }

   public int rankedCount(TopCategory category) {
      return this.top(category).size();
   }

   public List<LeaderboardEntry> page(TopCategory category, int page, int perPage) {
      List<LeaderboardEntry> all = this.top(category);
      int size = Math.max(1, perPage);
      int pages = Math.max(1, (int)Math.ceil((double)all.size() / size));
      int safePage = Math.min(Math.max(1, page), pages);
      int from = (safePage - 1) * size;
      int to = Math.min(all.size(), from + size);
      return from >= to ? List.of() : new ArrayList<>(all.subList(from, to));
   }

   public int pages(TopCategory category, int perPage) {
      int size = Math.max(1, perPage);
      return Math.max(1, (int)Math.ceil((double)this.top(category).size() / size));
   }

   public String[] scoreboardPlaceholders(Player player) {
      return this.scoreboardPlaceholders(player, null);
   }

   public String[] scoreboardPlaceholders(Player player, Set<String> wanted) {
      Map<String, String> placeholders = new LinkedHashMap<>();
      placeholders.put("player", player.getName());
      placeholders.put("world", player.getWorld().getName());
      placeholders.put("online", Integer.toString(Bukkit.getOnlinePlayers().size()));
      placeholders.put(
         "money",
         this.plugin.economy() != null && this.plugin.economy().isEnabled() ? this.plugin.economy().format(this.plugin.economy().balance(player)) : "-"
      );
      placeholders.put("ping", Integer.toString(player.getPing()));
      placeholders.put("bounty", this.plugin.bounty() == null ? "-" : this.plugin.bounty().formatted(player.getUniqueId()));
      boolean tagged = this.plugin.combat() != null && this.plugin.combat().isTagged(player);
      placeholders.put("combat", tagged ? Integer.toString(this.plugin.combat().remainingSeconds(player)) : "-");

      for (StatType type : StatType.values()) {
         placeholders.put(type.key(), Long.toString(this.value(player.getUniqueId(), type)));
      }

      placeholders.put("playtime", this.formatPlaytime(this.value(player.getUniqueId(), StatType.PLAYTIME)));

      String unranked = this.plugin.config().leaderboardUnrankedText();

      for (TopCategory category : TopCategory.values()) {
         String key = "rank-" + category.key();
         if (wanted == null || wanted.contains(key)) {
            int rank = this.rankOf(player.getUniqueId(), category);
            placeholders.put(key, rank > 0 ? Integer.toString(rank) : unranked);
         }
      }

      List<String> pairs = new ArrayList<>(placeholders.size() * 2);

      for (Entry<String, String> entry : placeholders.entrySet()) {
         pairs.add(entry.getKey());
         pairs.add(entry.getValue());
      }

      return pairs.toArray(new String[0]);
   }

   public String formatPlaytime(long seconds) {
      if (seconds <= 0L) {
         return "0m";
      } else {
         long hours = seconds / 3600L;
         long minutes = seconds % 3600L / 60L;
         if (hours > 0L) {
            return hours + "h " + minutes + "m";
         } else {
            return minutes > 0L ? minutes + "m" : seconds + "s";
         }
      }
   }

   public void markDirty() {
      this.dirty = true;
   }

   public boolean isDirty() {
      return this.dirty;
   }

   public void saveAsync() {
      if (this.isEnabled() && !this.writeQueued) {
         this.writeQueued = true;
         Bukkit.getScheduler().runTask(this.plugin, () -> {
            this.writeQueued = false;
            StatisticsService.Snapshot snapshot = this.snapshot(false);
            long version = this.writeVersion.incrementAndGet();
            this.dirty = false;
            Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
               synchronized (this.writeLock) {
                  if (version >= this.writtenVersion.get()) {
                     if (this.persist(snapshot)) {
                        this.writtenVersion.set(version);
                     } else {
                        this.dirty = true;
                     }
                  }
               }
            });
         });
      }
   }

   public boolean saveBlocking(String reason) {
      if (!this.isEnabled() && this.cache.isEmpty()) {
         return true;
      }

      StatisticsService.Snapshot snapshot = this.snapshot(true);
      long version = this.writeVersion.incrementAndGet();
      synchronized (this.writeLock) {
         boolean success = this.persist(snapshot);
         if (success) {
            this.writtenVersion.set(version);
            this.dirty = false;
         }

         this.plugin
            .debug(
               "Statistik: simpan ("
                  + reason
                  + ") "
                  + snapshot.rows().size()
                  + " pemain -> "
                  + this.storage.fileFor(this.mode).getName()
                  + " = "
                  + success
                  + "."
            );
         return success;
      }
   }

   private StatisticsService.Snapshot snapshot(boolean full) {
      Map<UUID, PlayerStats> copy = new LinkedHashMap<>();
      boolean partial = !full && this.mode == StatsStorage.Mode.SQLITE;

      for (Entry<UUID, PlayerStats> entry : this.cache.entrySet()) {
         PlayerStats source = entry.getValue();
         if (partial && !this.needsWrite(source)) {
            continue;
         }

         PlayerStats target = new PlayerStats(source.uniqueId());
         target.name(source.name());
         target.firstSeen(source.firstSeen());
         target.lastSeen(source.lastSeen());

         for (Entry<StatType, Long> value : source.values().entrySet()) {
            target.value(value.getKey(), value.getValue() == null ? 0L : value.getValue());
         }

         if (target.name() == null) {
            target.name(this.lastKnownNames.get(source.uniqueId()));
         }

         // stamp disalin terakhir: pemanggilan name()/value() di atas menaikkan stamp salinan.
         target.stamp(source.stamp());
         copy.put(entry.getKey(), target);
      }

      Set<UUID> deletes = partial ? new HashSet<>(this.pendingDeletes) : Set.of();
      boolean wipe = partial && this.wipeRequested;
      // Serialisasi YAML dilewati saat mode SQLITE: membangun string YAML seluruh data pemain
      // adalah pekerjaan sia-sia yang dulu dilakukan di main thread setiap autosave.
      String yaml = partial ? null : this.storage.serializeYaml(copy);
      return new StatisticsService.Snapshot(copy, yaml, deletes, wipe, partial);
   }

   private boolean needsWrite(PlayerStats stats) {
      Long written = this.writtenStamps.get(stats.uniqueId());
      return written == null || written.longValue() != stats.stamp();
   }

   private boolean persist(StatisticsService.Snapshot snapshot) {
      boolean success = this.mode == StatsStorage.Mode.SQLITE
         ? this.persistSqlite(snapshot)
         : this.storage.writeYaml(this.mode, snapshot.yaml());
      if (success) {
         for (PlayerStats stats : snapshot.rows().values()) {
            this.writtenStamps.put(stats.uniqueId(), stats.stamp());
         }

         if (snapshot.partial()) {
            this.pendingDeletes.removeAll(snapshot.deletes());
            if (snapshot.wipe()) {
               this.wipeRequested = false;
            }
         } else {
            this.pendingDeletes.clear();
            this.wipeRequested = false;
         }
      }

      return success;
   }

   private int loadSqlite() {
      int count = 0;

      try (Connection connection = this.connect()) {
         this.prepareSchema(connection);

         try (
            Statement statement = connection.createStatement();
            ResultSet rows = statement.executeQuery("SELECT uuid, name, first_seen, last_seen, stats FROM w2nsmp_stats");
         ) {
            while (rows.next()) {
               UUID uniqueId;
               try {
                  uniqueId = UUID.fromString(rows.getString("uuid"));
               } catch (IllegalArgumentException exception) {
                  this.plugin.getLogger().warning("Lewati baris statistik dengan UUID tidak valid: " + rows.getString("uuid"));
                  continue;
               }

               PlayerStats stats = new PlayerStats(uniqueId);
               stats.name(rows.getString("name"));
               stats.firstSeen(rows.getLong("first_seen"));
               stats.lastSeen(rows.getLong("last_seen"));
               this.readStatsJson(stats, rows.getString("stats"));
               this.cache.put(uniqueId, stats);
               count++;
            }
         }

         return count;
      } catch (SQLException exception) {
         this.plugin.getLogger().log(Level.WARNING, "Gagal membaca w2nsmp.db: " + exception.getMessage());
         return -1;
      }
   }

   private boolean persistSqlite(StatisticsService.Snapshot snapshot) {
      try (Connection connection = this.connect()) {
         this.prepareSchema(connection);
         connection.setAutoCommit(false);

         if (snapshot.wipe()) {
            try (Statement delete = connection.createStatement()) {
               delete.executeUpdate("DELETE FROM w2nsmp_stats");
            }
         }

         try (
            PreparedStatement insert = connection.prepareStatement(
               "INSERT OR REPLACE INTO w2nsmp_stats (uuid, name, first_seen, last_seen, stats) VALUES (?, ?, ?, ?, ?)"
            );
            PreparedStatement delete = connection.prepareStatement("DELETE FROM w2nsmp_stats WHERE uuid = ?");
         ) {
            for (PlayerStats stats : snapshot.rows().values()) {
               if (!stats.isEmpty() || !stats.values().isEmpty()) {
                  insert.setString(1, stats.uniqueId().toString());
                  insert.setString(2, stats.name());
                  insert.setLong(3, stats.firstSeen());
                  insert.setLong(4, stats.lastSeen());
                  insert.setString(5, this.writeStatsJson(stats));
                  insert.addBatch();
               }
            }

            insert.executeBatch();

            for (UUID uniqueId : snapshot.deletes()) {
               delete.setString(1, uniqueId.toString());
               delete.addBatch();
            }

            if (!snapshot.deletes().isEmpty()) {
               delete.executeBatch();
            }
         }

         connection.commit();
         return true;
      } catch (SQLException exception) {
         this.plugin.getLogger().log(Level.WARNING, "Gagal menulis w2nsmp.db: " + exception.getMessage());
         return false;
      }
   }

   private Connection connect() throws SQLException {
      return DriverManager.getConnection(this.storage.jdbcUrl());
   }

   private void prepareSchema(Connection connection) throws SQLException {
      try (Statement statement = connection.createStatement()) {
         statement.executeUpdate(
            "CREATE TABLE IF NOT EXISTS w2nsmp_stats (uuid TEXT PRIMARY KEY,name TEXT,first_seen INTEGER DEFAULT 0,last_seen INTEGER DEFAULT 0,stats TEXT)"
         );
         statement.executeUpdate("CREATE TABLE IF NOT EXISTS w2nsmp_meta (key TEXT PRIMARY KEY, value TEXT)");
      }

      try (PreparedStatement insert = connection.prepareStatement("INSERT OR REPLACE INTO w2nsmp_meta (key, value) VALUES ('schema', ?)")) {
         insert.setString(1, Integer.toString(1));
         insert.executeUpdate();
      }
   }

   private void readStatsJson(PlayerStats stats, String raw) {
      if (raw != null && !raw.isBlank()) {
         for (String part : raw.split(";")) {
            int separator = part.indexOf(61);
            if (separator > 0) {
               StatType type = StatType.fromKey(part.substring(0, separator));
               if (type != null) {
                  try {
                     stats.value(type, Long.parseLong(part.substring(separator + 1).trim()));
                  } catch (NumberFormatException exception) {
                     this.plugin.debug("Statistik: nilai tidak valid untuk " + type.key() + " (" + part + ").");
                  }
               }
            }
         }
      }
   }

   private String writeStatsJson(PlayerStats stats) {
      StringBuilder builder = new StringBuilder();

      for (Entry<StatType, Long> entry : stats.values().entrySet()) {
         if (entry.getValue() != null && entry.getValue() > 0L) {
            if (builder.length() > 0) {
               builder.append(';');
            }

            builder.append(entry.getKey().key()).append('=').append(entry.getValue());
         }
      }

      return builder.toString();
   }

   private record Snapshot(Map<UUID, PlayerStats> rows, String yaml, Set<UUID> deletes, boolean wipe, boolean partial) {
   }
}
