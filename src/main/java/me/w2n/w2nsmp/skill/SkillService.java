package me.w2n.w2nsmp.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.listener.SkillFishingListener;
import me.w2n.w2nsmp.listener.SkillGuiListener;
import me.w2n.w2nsmp.listener.SkillListener;
import me.w2n.w2nsmp.player.PlayerSettingsService;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitTask;

/**
 * Layanan skill: XP, level, buff, dan penyimpanan.
 *
 * <p>Prinsip desain:
 * <ul>
 *   <li><b>Tidak menyentuh fitur lama.</b> Layanan ini hanya membaca event (lewat
 *       {@code SkillListener}) dan menambah data sendiri di {@code skills.yml}. Statistik,
 *       ekonomi, scoreboard, dan nametag tidak diubah.</li>
 *   <li><b>Anti error.</b> Semua API yang tidak dijamin ada di setiap versi server diperiksa
 *       lebih dulu oleh {@link SkillApiProbe}; bagian yang API-nya hilang dinonaktifkan sendiri.</li>
 *   <li><b>Murah di jalur panas.</b> Setting dibaca sekali ke array/enum-set, level dihitung dari
 *       tabel kumulatif, dan XP pergerakan diakumulasi sebelum benar-benar dicatat.</li>
 * </ul>
 */
public final class SkillService {
   /**
    * Penyebab damage yang bisa ditahan buff block. Damage lingkungan (jatuh, api, tenggelam, ...)
    * sengaja tidak ikut: itu wilayah buff pengurangan damage lingkungan.
    */
   private static final Set<String> BLOCKABLE_CAUSES = Set.of(
      "ENTITY_ATTACK", "ENTITY_SWEEP_ATTACK", "PROJECTILE", "ENTITY_EXPLOSION", "THORNS"
   );

   private final W2NSMP plugin;
   private final SkillStorage storage;
   private final SkillApiProbe probe;
   private SkillTop top;
   private final SkillDiagnostics diagnostics;
   private final Map<UUID, SkillProfile> profiles = new ConcurrentHashMap<>();
   private final Map<UUID, SkillService.RuntimeState> runtime = new ConcurrentHashMap<>();
   private final Map<UUID, SkillService.WalkSpeed> walkSpeeds = new HashMap<>();
   private final Map<UUID, Long> projectileHits = new HashMap<>();
   private final SkillSettings[] settings = new SkillSettings[SkillType.count()];
   private final Random random = new Random();
   private SkillCurve curve = new SkillCurve(50, 25.0D, 1.25D);
   private double globalMultiplier = 1.0D;
   private Set<String> allowedWorlds = Set.of();
   private Set<String> blockedWorlds = Set.of();
   private Set<String> skippedGameModes = Set.of("CREATIVE", "SPECTATOR");
   private boolean enabled;
   private boolean buffsEnabled = true;
   private boolean notifyLevelUp = true;
   private boolean notifyExtraDrop;
   private boolean notifyCrit = true;
   private int topLimit = 10;
   private boolean actionbarXp;
   private String levelUpSound = "minecraft:entity.player.levelup";
   private int potionRefreshSeconds = 10;
   private int healIntervalSeconds = 5;
   private double healCap = 20.0D;
   private int saveIntervalSeconds = 300;
   private BukkitTask tickTask;
   private BukkitTask saveTask;
   private BukkitTask watchdogTask;
   private SkillGuiListener guiListener;
   private SkillListener xpListener;
   private SkillFishingListener fishingListener;
   private boolean dirty;

   public SkillService(W2NSMP plugin) {
      this.plugin = plugin;
      this.storage = new SkillStorage(plugin);
      this.probe = new SkillApiProbe(plugin);
      this.diagnostics = new SkillDiagnostics(plugin);

      for (SkillType type : SkillType.values()) {
         this.settings[type.ordinal()] = SkillSettings.load(plugin, type, SkillMenuSlots.fallback(type));
      }
   }

   public void load() {
      this.probe.probe();
      this.reloadSettings();
      this.profiles.clear();
      this.profiles.putAll(this.storage.load());
      this.plugin
         .getLogger()
         .info(
            "Skill: "
               + this.profiles.size()
               + " profil dimuat dari "
               + this.storage.file().getName()
               + " | maks level "
               + this.curve.maxLevel()
               + " | "
               + this.enabledSkillCount()
               + "/"
               + SkillType.count()
               + " skill aktif."
         );

      for (Player player : Bukkit.getOnlinePlayers()) {
         this.onJoin(player);
      }
   }

   public void reload() {
      this.probe.probe();
      this.reloadSettings();

      for (Player player : Bukkit.getOnlinePlayers()) {
         this.removeBuffs(player);
         this.applyBuffs(player);
      }
   }

   private void reloadSettings() {
      FileConfiguration config = this.plugin.config().raw();
      this.enabled = config.getBoolean("skills.enabled", true);
      this.globalMultiplier = Math.max(0.0D, config.getDouble("skills.curve.global-multiplier", 1.0D));
      this.curve = new SkillCurve(
         Math.max(1, config.getInt("skills.max-level", 50)),
         Math.max(1.0D, config.getDouble("skills.curve.base-xp", 25.0D)),
         Math.max(1.0D, config.getDouble("skills.curve.exponent", 1.25D))
      );
      this.notifyLevelUp = config.getBoolean("skills.notify-level-up", true);
      this.notifyExtraDrop = config.getBoolean("skills.notify-extra-drop", false);
      this.notifyCrit = config.getBoolean("skills.notify-crit", true);
      this.topLimit = Math.max(1, Math.min(50, config.getInt("skills.top-limit", 10)));
      this.actionbarXp = config.getBoolean("skills.xp-gain-actionbar", false);
      this.levelUpSound = config.getString("skills.level-up-sound", "minecraft:entity.player.levelup");
      this.saveIntervalSeconds = config.getInt("skills.save-interval-seconds", 300);
      this.buffsEnabled = config.getBoolean("skills.buffs.enabled", true);
      this.potionRefreshSeconds = Math.max(1, config.getInt("skills.buffs.potion-refresh-seconds", 10));
      this.healIntervalSeconds = Math.max(1, config.getInt("skills.buffs.heal-interval-seconds", 5));
      this.healCap = Math.max(1.0D, config.getDouble("skills.buffs.heal-cap", 20.0D));
      this.allowedWorlds = readUpperList(config, "skills.worlds");
      this.blockedWorlds = readUpperList(config, "skills.blacklisted-worlds");
      this.skippedGameModes = readUpperList(config, "skills.skip-gamemodes");
      if (this.skippedGameModes.isEmpty()) {
         this.skippedGameModes = Set.of("CREATIVE", "SPECTATOR");
      }

      for (SkillType type : SkillType.values()) {
         this.settings[type.ordinal()] = SkillSettings.load(this.plugin, type, SkillMenuSlots.fallback(type));
      }

      if (!this.enabled) {
         this.plugin.getLogger().info("Skill: fitur dimatikan lewat config.yml (skills.enabled: false).");
      }
   }

   private static Set<String> readUpperList(FileConfiguration config, String path) {
      List<String> values = config.getStringList(path);
      if (values == null || values.isEmpty()) {
         return Set.of();
      }

      Set<String> result = new LinkedHashSet<>(values.size());

      for (String value : values) {
         if (value != null && !value.isBlank()) {
            result.add(value.trim().toUpperCase(Locale.ROOT));
         }
      }

      return result;
   }

   /** Diagnostik fitur (laporan pendaftaran listener + kesalahan terakhir). */
   public SkillDiagnostics diagnostics() {
      return this.diagnostics;
   }

   /**
    * Daftarkan listener skill ke server. Dipanggil {@code ListenerManager} saat enable dan oleh
    * watchdog bila pendaftaran hilang. Aman dipanggil berulang: instance yang sudah ada tidak
    * didaftarkan dua kali (XP tidak akan pernah terhitung ganda).
    *
    * @return ringkasan singkat hasil pendaftaran untuk log
    */
   public synchronized String registerListeners() {
      PluginManager manager = Bukkit.getPluginManager();
      List<String> notes = new ArrayList<>(3);
      if (this.guiListener == null) {
         this.guiListener = new SkillGuiListener(this.plugin);
         this.register(manager, this.guiListener, "GUI", notes);
      }

      if (this.xpListener == null) {
         this.xpListener = new SkillListener(this.plugin);
         this.register(manager, this.xpListener, "XP & buff", notes);
      }

      if (this.fishingListener == null) {
         if (this.probe.fishingEventApi()) {
            this.fishingListener = new SkillFishingListener(this.plugin);
            this.register(manager, this.fishingListener, "memancing", notes);
         } else {
            notes.add("memancing dilewati (PlayerFishEvent tidak ada di server ini)");
         }
      }

      return String.join(", ", notes);
   }

   private void register(PluginManager manager, Listener listener, String label, List<String> notes) {
      try {
         manager.registerEvents(listener, this.plugin);
         notes.add(label + " terdaftar");
      } catch (Throwable throwable) {
         notes.add(label + " GAGAL: " + throwable);
         this.plugin.getLogger().severe("Skill: listener " + label + " gagal didaftarkan -> " + throwable);
         this.diagnostics.noteError("pendaftaran-listener-" + label, throwable);
      }
   }

   /**
    * Watchdog: periksa ke {@code HandlerList} server apakah listener skill masih terdaftar, lalu
    * pasang ulang yang hilang. Ini pengaman terhadap kejadian yang tidak terlihat dari luar -
    * plugin lain yang membersihkan handler, atau reload server yang tidak rapi - yang membuat
    * fitur "hidup tapi tuli".
    */
   public synchronized void ensureListeners() {
      Set<String> missing = this.diagnostics.inspect();
      if (missing.isEmpty()) {
         return;
      }

      this.plugin.getLogger().severe("Skill: listener " + missing + " tidak terdaftar di server - dipasang ulang otomatis.");
      if (missing.contains("SkillGuiListener")) {
         this.guiListener = null;
      }

      if (missing.contains("SkillListener")) {
         this.xpListener = null;
      }

      if (missing.contains("SkillFishingListener")) {
         this.fishingListener = null;
      }

      String result = this.registerListeners();
      if (this.diagnostics.inspect().isEmpty()) {
         this.plugin.getLogger().info("Skill: perbaikan listener berhasil (" + result + ").");
      } else {
         this.plugin.getLogger().severe("Skill: listener masih hilang setelah dipasang ulang (" + result + ") - restart server diperlukan.");
      }
   }

   public void startTasks() {
      this.stopTasks();

      // Pendaftaran listener diverifikasi sesudah server selesai meng-enable plugin (2 detik),
      // lalu diperiksa ulang tiap menit. Murah: hanya membaca HandlerList beberapa event.
      this.watchdogTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::watchdog, 40L, 1200L);
      if (!this.enabled) {
         return;
      }

      this.tickTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 20L, 20L);
      if (this.saveIntervalSeconds > 0) {
         long ticks = this.saveIntervalSeconds * 20L;
         this.saveTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::saveIfDirty, ticks, ticks);
      }
   }

   /** Tugas watchdog: verifikasi + laporan sekali di awal, lalu diam bila semua beres. */
   private void watchdog() {
      try {
         this.ensureListeners();
      } catch (Throwable throwable) {
         this.diagnostics.noteError("watchdog", throwable);
      }
   }

   public void stopTasks() {
      if (this.watchdogTask != null) {
         this.watchdogTask.cancel();
         this.watchdogTask = null;
      }

      if (this.tickTask != null) {
         this.tickTask.cancel();
         this.tickTask = null;
      }

      if (this.saveTask != null) {
         this.saveTask.cancel();
         this.saveTask = null;
      }
   }

   public void shutdown() {
      this.stopTasks();

      for (Player player : Bukkit.getOnlinePlayers()) {
         this.removeBuffs(player);
      }

      this.runtime.clear();
      this.walkSpeeds.clear();
      this.projectileHits.clear();
      if (this.dirty) {
         this.storage.saveNow(this.profiles);
         this.dirty = false;
      }
   }

   // ------------------------------------------------------------------ //
   //  Status & data
   // ------------------------------------------------------------------ //

   public boolean enabled() {
      return this.enabled;
   }

   /** Kirim pesan saat buff drop/hasil ganda berhasil (bawaan: mati, supaya tidak berisik). */
   public boolean notifyExtraDrop() {
      return this.notifyExtraDrop;
   }

   /** Tampilkan actionbar saat serangan menjadi critical (bawaan: aktif). */
   public boolean notifyCrit() {
      return this.notifyCrit;
   }

   /** Jumlah baris peringkat yang ditampilkan {@code /skill top} (1..50, bawaan 10). */
   public int topLimit() {
      return this.topLimit;
   }

   public boolean buffsEnabled() {
      return this.enabled && this.buffsEnabled;
   }

   public SkillCurve curve() {
      return this.curve;
   }

   public int maxLevel() {
      return this.curve.maxLevel();
   }

   public SkillApiProbe probe() {
      return this.probe;
   }

   public java.io.File file() {
      return this.storage.file();
   }

   public boolean storageFailed() {
      return this.storage.loadFailed();
   }

   public SkillSettings settings(SkillType type) {
      return type == null ? null : this.settings[type.ordinal()];
   }

   public List<SkillSettings> settingsList() {
      List<SkillSettings> result = new ArrayList<>(this.settings.length);

      for (SkillSettings value : this.settings) {
         result.add(value);
      }

      return result;
   }

   public int enabledSkillCount() {
      int count = 0;

      for (SkillSettings value : this.settings) {
         if (value != null && value.enabled()) {
            count++;
         }
      }

      return count;
   }

   public int trackedPlayers() {
      return this.profiles.size();
   }

   public boolean isDirty() {
      return this.dirty || this.storage.isDirty();
   }

   public SkillProfile profile(UUID uniqueId) {
      SkillProfile profile = this.profiles.get(uniqueId);
      if (profile == null) {
         profile = new SkillProfile(uniqueId);
         SkillProfile existing = this.profiles.putIfAbsent(uniqueId, profile);
         if (existing != null) {
            profile = existing;
         }
      }

      return profile;
   }

   public double xp(UUID uniqueId, SkillType type) {
      return uniqueId == null ? 0.0D : this.profile(uniqueId).xp(type);
   }

   public double xp(Player player, SkillType type) {
      return player == null ? 0.0D : this.xp(player.getUniqueId(), type);
   }

   public int level(UUID uniqueId, SkillType type) {
      return this.curve.levelFor(this.xp(uniqueId, type));
   }

   public int level(Player player, SkillType type) {
      return player == null ? 1 : this.level(player.getUniqueId(), type);
   }

   public double progress(Player player, SkillType type) {
      return player == null ? 0.0D : this.curve.progress(this.xp(player, type));
   }

   public double xpToNextLevel(Player player, SkillType type) {
      return player == null ? 0.0D : this.curve.xpToNextLevel(this.xp(player, type));
   }

   public double totalXp(Player player) {
      return player == null ? 0.0D : this.profile(player.getUniqueId()).totalXp();
   }

   public int totalLevel(Player player) {
      return player == null ? 0 : this.profile(player.getUniqueId()).totalLevel(this.curve);
   }

   /** Level tertinggi dari semua skill (dipakai untuk "skill terbaik" di GUI). */
   public SkillType bestSkill(Player player) {
      SkillType best = null;
      double bestXp = -1.0D;

      for (SkillType type : SkillType.values()) {
         double xp = this.xp(player, type);
         if (xp > bestXp) {
            bestXp = xp;
            best = type;
         }
      }

      return best;
   }

   public boolean worldAllowed(World world) {
      if (world == null) {
         return true;
      }

      String name = world.getName();
      if (name == null) {
         return true;
      }

      String upper = name.toUpperCase(Locale.ROOT);
      if (this.blockedWorlds.contains(upper)) {
         return false;
      }

      return this.allowedWorlds.isEmpty() || this.allowedWorlds.contains(upper);
   }

   public boolean gameModeAllowed(Player player) {
      if (player == null || this.skippedGameModes.isEmpty()) {
         return true;
      }

      try {
         return player.getGameMode() == null || !this.skippedGameModes.contains(player.getGameMode().name().toUpperCase(Locale.ROOT));
      } catch (RuntimeException exception) {
         return true;
      }
   }

   /** Peluang buff utama skill (extra-drop/extra-catch) terpenuhi untuk pemain ini. */
   public boolean rollChance(Player player, SkillType type) {
      double percent = this.buffValue(player, type);
      return percent > 0.0D && this.random.nextDouble() * 100.0D < percent;
   }

   /** Peluang buff dengan jenis tertentu terpenuhi (dipakai buff milestone seperti loot mob). */
   public boolean rollChance(Player player, SkillType type, BuffKind kind) {
      double percent = this.buffValue(player, type, kind);
      return percent > 0.0D && this.random.nextDouble() * 100.0D < percent;
   }

   /** Nilai buff utama skill dalam persen (0 bila belum terbuka / buff mati / API tidak tersedia). */
   public double buffValue(Player player, SkillType type) {
      return type == null ? 0.0D : this.buffValue(player, type, type.buff());
   }

   /**
    * Nilai buff jenis tertentu pada satu skill (0 bila belum terbuka, skill/buff dimatikan, atau API
    * yang dibutuhkan tidak ada di server ini).
    */
   public double buffValue(Player player, SkillType type, BuffKind kind) {
      if (player == null || type == null || kind == null || !this.buffsEnabled() || !this.kindAvailable(kind)) {
         return 0.0D;
      }

      return this.settings(type).buffValue(this.level(player, type), kind);
   }

   /**
    * Jumlah nilai buff jenis tertentu dari <b>semua</b> skill pemain - misalnya pengurangan damage
    * lingkungan (endurance + agility) atau peluang block (defense + endurance).
    */
   public double buffValueAll(Player player, BuffKind kind) {
      if (player == null || kind == null || !this.buffsEnabled() || !this.kindAvailable(kind)) {
         return 0.0D;
      }

      double total = 0.0D;

      for (SkillType type : SkillType.values()) {
         total += this.settings(type).buffValue(this.level(player, type), kind);
      }

      return total > 0.0D ? total : 0.0D;
   }

   /** Apakah server ini mendukung buff jenis itu (hasil probing API saat startup). */
   public boolean kindAvailable(BuffKind kind) {
      if (kind == null) {
         return false;
      }

      return switch (kind) {
         case MELEE_DAMAGE, PROJECTILE_DAMAGE, CRIT_CHANCE, DAMAGE_REDUCTION, BLOCK_CHANCE -> this.probe.damageApi();
         case ENVIRONMENT_REDUCTION -> this.probe.damageCauseApi();
         case WALK_SPEED -> this.probe.walkSpeedApi();
         case EXTRA_DROP -> this.probe.blockDropsApi();
         case EXTRA_CATCH -> this.probe.fishingEventApi();
         case MOB_LOOT -> this.probe.mobLootApi();
         case VANILLA_XP -> this.probe.blockExpApi();
         case REGEN_BOOST, PASSIVE_HEAL -> this.probe.healthApi();
         case HASTE -> this.probe.hasteApi();
         case POTION -> this.probe.potionApi();
         case DOUBLE_XP -> true;
      };
   }

   /** Pengali damage (1.0 = tidak ada buff) untuk serangan pemain, belum termasuk critical. */
   public double damageMultiplier(Player attacker, SkillType type) {
      double percent = this.buffValue(attacker, type);
      return percent <= 0.0D ? 1.0D : 1.0D + percent / 100.0D;
   }

   /** Benarkah serangan ini critical (peluang dari buff {@code CRIT_CHANCE} skill penyerang)? */
   public boolean rollCrit(Player attacker, SkillType type) {
      return this.rollChance(attacker, type, BuffKind.CRIT_CHANCE);
   }

   /** Pengali tambahan saat critical (1.5 berarti damage +50%); 1.0 bila {@code power} tidak diisi. */
   public double critMultiplier(Player attacker, SkillType type) {
      if (attacker == null || type == null || !this.buffsEnabled() || !this.kindAvailable(BuffKind.CRIT_CHANCE)) {
         return 1.0D;
      }

      double power = this.settings(type).buffPower(this.level(attacker, type), BuffKind.CRIT_CHANCE);
      return power <= 0.0D ? 1.0D : 1.0D + power / 100.0D;
   }

   /**
    * Pengali damage yang diterima (1.0 = tidak ada pengurangan). Menggabungkan tiga hal:
    * pengurangan damage biasa (defense), pengurangan damage lingkungan (endurance/agility, hanya
    * untuk penyebab yang cocok), lalu peluang menahan sebagian damage (buff block).
    */
   public double damageTakenMultiplier(Player victim, String causeName) {
      double reduction = this.buffValue(victim, SkillType.DEFENSE) + this.environmentReduction(victim, causeName);
      double multiplier = reduction <= 0.0D ? 1.0D : 1.0D - Math.min(90.0D, reduction) / 100.0D;
      return multiplier * this.blockMultiplier(victim, causeName);
   }

   /** Pengurangan damage lingkungan (persen) dari semua skill untuk penyebab tertentu. */
   public double environmentReduction(Player victim, String causeName) {
      if (victim == null || causeName == null || !this.buffsEnabled()
         || !this.kindAvailable(BuffKind.ENVIRONMENT_REDUCTION)) {
         return 0.0D;
      }

      double total = 0.0D;

      for (SkillType type : SkillType.values()) {
         total += this.settings(type).environmentReduction(this.level(victim, type), causeName);
      }

      return total > 0.0D ? total : 0.0D;
   }

   /**
    * Pengali dari buff block: 1.0 bila peluang tidak terpenuhi, selain itu {@code 1 - power/100}
    * (mis. power 50 berarti damage pukulan itu tinggal setengah). Peluang dibatasi 75% supaya
    * config yang kelewat besar tidak membuat pemain kebal.
    *
    * <p>Block hanya berlaku untuk damage yang memang bisa ditahan (pukulan, sapuan, panah, ledakan
    * makhluk) - damage lingkungan seperti jatuh, api, atau tenggelam sudah diurusi buff
    * {@code ENVIRONMENT_REDUCTION}, jadi tidak pernah dikurangi dua kali.
    */
   public double blockMultiplier(Player victim, String causeName) {
      if (causeName != null && !BLOCKABLE_CAUSES.contains(causeName.trim().toUpperCase(Locale.ROOT))) {
         return 1.0D;
      }

      double chance = Math.min(75.0D, this.buffValueAll(victim, BuffKind.BLOCK_CHANCE));
      if (chance <= 0.0D || this.random.nextDouble() * 100.0D >= chance) {
         return 1.0D;
      }

      double power = this.blockPower(victim);
      return power <= 0.0D ? 1.0D : Math.max(0.0D, 1.0D - Math.min(100.0D, power) / 100.0D);
   }

   /** Persen damage yang ditahan saat block berhasil (buff block terbaik yang dimiliki pemain). */
   public double blockPower(Player victim) {
      if (victim == null || !this.buffsEnabled() || !this.kindAvailable(BuffKind.BLOCK_CHANCE)) {
         return 0.0D;
      }

      double best = 0.0D;

      for (SkillType type : SkillType.values()) {
         best = Math.max(best, this.settings(type).buffPower(this.level(victim, type), BuffKind.BLOCK_CHANCE));
      }

      return best;
   }

   public int hasteAmplifier(Player player) {
      if (player == null || !this.buffsEnabled() || !this.probe.hasteApi()) {
         return -1;
      }

      return this.settings(SkillType.MINING).hasteAmplifier(this.level(player, SkillType.MINING));
   }

   /** Jumlah heart yang dipulihkan buff heal pasif (dari semua skill) tiap interval. */
   public double healAmount(Player player) {
      return this.buffValueAll(player, BuffKind.PASSIVE_HEAL);
   }

   /** Jeda (detik) setelah terkena damage sebelum heal pasif boleh jalan lagi. */
   public int healDelaySeconds(Player player) {
      if (player == null || !this.buffsEnabled() || !this.probe.healthApi()) {
         return 10;
      }

      int best = Integer.MAX_VALUE;

      for (SkillType type : SkillType.values()) {
         SkillSettings settings = this.settings(type);
         if (!settings.enabled()) {
            continue;
         }

         SkillBuff buff = settings.firstBuff(BuffKind.PASSIVE_HEAL);
         if (buff != null && buff.unlocked(this.level(player, type))) {
            best = Math.min(best, buff.delaySeconds());
         }
      }

      return best == Integer.MAX_VALUE ? 10 : best;
   }

   /** Indeks acak 0..(size-1) memakai sumber acak service (untuk memilih drop mob yang digandakan). */
   public int randomIndex(int size) {
      return size <= 0 ? -1 : this.random.nextInt(size);
   }

   // ------------------------------------------------------------------ //
   //  XP
   // ------------------------------------------------------------------ //

   /**
    * Tambahkan XP untuk pemain. Dipanggil dari listener; semua pemeriksaan (skill aktif, world,
    * game mode, API) dilakukan di sini supaya pemanggil tetap sederhana.
    */
   public void addXp(Player player, SkillType type, double amount) {
      if (player == null || type == null || !this.enabled || !(amount > 0.0D)) {
         return;
      }

      SkillSettings settings = this.settings(type);
      if (!settings.enabled()) {
         return;
      }

      if (!this.worldAllowed(player.getWorld()) || !this.gameModeAllowed(player)) {
         this.hintSkipped(player);
         return;
      }

      double total = amount * settings.xpMultiplier() * this.globalMultiplier;
      if (!(total > 0.0D) || Double.isNaN(total) || Double.isInfinite(total)) {
         return;
      }

      // Buff DOUBLE_XP: peluang XP yang masuk menjadi dua kali lipat (murni perhitungan, tanpa API).
      double doubleChance = this.buffValue(player, type, BuffKind.DOUBLE_XP);
      boolean doubled = doubleChance > 0.0D && this.random.nextDouble() * 100.0D < doubleChance;
      if (doubled) {
         total *= 2.0D;
      }

      SkillProfile profile = this.profile(player.getUniqueId());
      int before = this.curve.levelFor(profile.xp(type));
      double nowXp = profile.addXp(type, total);
      profile.name(player.getName());
      this.dirty = true;
      int after = this.curve.levelFor(nowXp);
      if (after > before) {
         this.onLevelUp(player, type, before, after);
      } else if (this.actionbarXp && player.isOnline()) {
         String key = doubled && this.plugin.messages().has("skill.actionbar-double") ? "skill.actionbar-double" : "skill.actionbar";
         player.sendActionBar(this.plugin.messages().component(key, "skill", this.label(type), "xp", format(total), "level", Integer.toString(after)));
      }
   }

   /**
    * Beri tahu pemain sekali per sesi kenapa XP-nya tidak bertambah (mode game atau world yang
    * dilewati). Tanpa pesan ini, admin yang menguji sambil berada di mode kreatif melihat fitur
    * seolah mati tanpa penjelasan apa pun.
    */
   private void hintSkipped(Player player) {
      SkillService.RuntimeState state = this.state(player.getUniqueId());
      if (state.skipHinted) {
         return;
      }

      state.skipHinted = true;

      try {
         if (!this.gameModeAllowed(player)) {
            String mode = player.getGameMode() == null ? "-" : player.getGameMode().name();
            this.plugin
               .messages()
               .send(player, "skill.hint-gamemode", "gamemode", mode, "list", String.join(", ", this.skippedGameModes));
         } else {
            String world = player.getWorld() == null ? "-" : player.getWorld().getName();
            this.plugin
               .messages()
               .send(player, "skill.hint-world", "world", world, "list", String.join(", ", this.allowedWorlds));
         }
      } catch (Throwable throwable) {
         this.diagnostics.noteError("hint-xp-dilewati", throwable);
      }
   }

   private void onLevelUp(Player player, SkillType type, int before, int after) {
      if (this.notifyLevelUp) {
         for (int level = before + 1; level <= after; level++) {
            this.plugin
               .messages()
               .send(
                  player,
                  "skill.level-up",
                  "skill",
                  this.label(type),
                  "level",
                  Integer.toString(level),
                  "max",
                  Integer.toString(this.curve.maxLevel()),
                  "buff",
                  this.buffDisplay(player, type)
               );
            this.announceNewBuffs(player, type, level);
         }
      }

      this.playSound(player, this.levelUpSound);
      this.applyBuffs(player);
   }

   /**
    * Umumkan buff yang baru terbuka tepat di level ini. Pemain jadi tahu hadiah apa yang baru
    * didapat tanpa harus membuka menu progres (pesan hanya dikirim bila kuncinya ada di
    * messages.yml, jadi admin bisa mematikannya dengan menghapus kunci itu).
    */
   private void announceNewBuffs(Player player, SkillType type, int level) {
      if (!this.buffsEnabled || !this.plugin.messages().has("skill.level-up-buff")) {
         return;
      }

      for (SkillBuff buff : this.settings(type).buffs()) {
         if (buff.unlockLevel() != level || !this.kindAvailable(buff.kind())) {
            continue;
         }

         this.plugin.messages().send(player, "skill.level-up-buff",
            "skill", this.label(type),
            "level", Integer.toString(level),
            "buff", this.buffName(buff),
            "buff-value", this.buffDisplay(player, type, buff),
            "desc", this.buffDescription(buff));
      }
   }

   /** Label skill dari messages.yml ({@code skill.name.<key>}), jatuh ke key bila belum diterjemahkan. */
   public String label(SkillType type) {
      if (type == null) {
         return "-";
      }

      return this.plugin.messages().has(type.nameKey()) ? this.plugin.messages().raw(type.nameKey()) : type.key();
   }

   /** Nilai buff utama siap tampil untuk skill (mis. "+12.5%", "Haste II", "+0.8 heart/5s"). */
   public String buffDisplay(Player player, SkillType type) {
      if (type == null) {
         return this.plugin.messages().raw("skill.buff-value.none");
      }

      SkillSettings settings = this.settings(type);
      if (!settings.enabled()) {
         return this.plugin.messages().raw("skill.buff-value.disabled");
      }

      SkillBuff primary = settings.primaryBuff();
      if (primary == null) {
         return this.plugin.messages().raw("skill.buff-value.none");
      }

      return this.buffDisplay(player, type, primary);
   }

   /**
    * Nilai satu buff siap tampil. Buff yang belum terbuka ditulis "(buka di Lv. X)" supaya pemain
    * tahu kapan buff berikutnya datang; buff yang API-nya tidak ada di server ini ditulis tersendiri.
    */
   public String buffDisplay(Player player, SkillType type, SkillBuff buff) {
      if (type == null || buff == null) {
         return this.plugin.messages().raw("skill.buff-value.none");
      }

      SkillSettings settings = this.settings(type);
      if (!settings.enabled()) {
         return this.plugin.messages().raw("skill.buff-value.disabled");
      }

      int level = player == null ? 1 : this.level(player, type);
      if (!buff.unlocked(level)) {
         return this.plugin.messages().raw("skill.buff-value.locked", "level", Integer.toString(buff.unlockLevel()));
      }

      if (!this.kindAvailable(buff.kind())) {
         return this.plugin.messages().has("skill.buff-value.unavailable")
            ? this.plugin.messages().raw("skill.buff-value.unavailable")
            : this.plugin.messages().raw("skill.buff-value.none");
      }

      String value = switch (buff.kind()) {
         case HASTE -> {
            int amplifier = buff.amplifier(level);
            yield amplifier < 0
               ? this.plugin.messages().raw("skill.buff-value.none")
               : this.plugin.messages().raw("skill.buff-value.haste", "level", Integer.toString(amplifier + 1));
         }
         case POTION -> {
            int amplifier = buff.amplifier(level);
            yield amplifier < 0
               ? this.plugin.messages().raw("skill.buff-value.none")
               : this.plugin.messages().raw("skill.buff-value.potion", "name", this.buffName(buff),
                  "level", toRoman(amplifier + 1));
         }
         case PASSIVE_HEAL -> {
            double amount = buff.value(level);
            yield amount <= 0.0D
               ? this.plugin.messages().raw("skill.buff-value.none")
               : this.plugin.messages().raw("skill.buff-value.heal", "amount", format(amount),
                  "seconds", Integer.toString(this.healIntervalSeconds));
         }
         default -> {
            double percent = buff.value(level);
            if (percent <= 0.0D) {
               yield this.plugin.messages().raw("skill.buff-value.none");
            }

            yield buff.power() > 0.0D && this.plugin.messages().has("skill.buff-value.power")
               ? this.plugin.messages().raw("skill.buff-value.power", "value", format(percent) + "%",
                  "power", format(buff.power()) + "%")
               : format(percent) + "%";
         }
      };

      return this.plugin.messages().raw("skill.buff-value.format", "value", value);
   }

   /**
    * Baris lore untuk <b>semua</b> buff skill ini (urut config): yang sudah terbuka menampilkan
    * nilainya, yang belum menampilkan level pembukaannya.
    */
   public List<String> buffLines(Player player, SkillType type) {
      List<String> lines = new ArrayList<>();
      if (type == null) {
         return lines;
      }

      SkillSettings settings = this.settings(type);
      int level = player == null ? 1 : this.level(player, type);

      for (SkillBuff buff : settings.buffs()) {
         boolean active = settings.enabled() && buff.unlocked(level);
         String key = active ? "skill.buff-line.active" : "skill.buff-line.locked";
         if (!this.plugin.messages().has(key)) {
            key = active ? "skill.detail-buff" : "skill.detail-next-buff";
         }

         lines.add(this.plugin.messages().raw(key,
            "name", this.buffName(buff),
            "value", this.buffDisplay(player, type, buff),
            "desc", this.buffDescription(buff),
            "level", Integer.toString(buff.unlockLevel()),
            "unlock", Integer.toString(buff.unlockLevel())));
      }

      if (lines.isEmpty()) {
         lines.add(this.plugin.messages().raw("skill.buff-value.none"));
      }

      return lines;
   }

   /** Nama pendek satu buff untuk lore (mis. "Damage melee", "Peluang critical", "Regeneration"). */
   public String buffName(SkillBuff buff) {
      if (buff == null || buff.kind() == null) {
         return "-";
      }

      if (buff.kind() == BuffKind.POTION) {
         String key = buff.potionKey() == null ? "" : buff.potionKey().trim().toLowerCase(Locale.ROOT);
         if (!key.isEmpty()) {
            String messageKey = "skill.potion-name." + key;
            if (this.plugin.messages().has(messageKey)) {
               return this.plugin.messages().raw(messageKey);
            }

            return prettify(key);
         }
      }

      String messageKey = "skill.buff-short." + buff.kind().key();
      return this.plugin.messages().has(messageKey)
         ? this.plugin.messages().raw(messageKey)
         : prettify(buff.kind().key());
   }

   /** Penjelasan buff utama (satu baris) untuk lore GUI/chat detail. */
   public String buffDescription(SkillType type) {
      if (type == null) {
         return "";
      }

      String key = type.buffKey();
      return this.plugin.messages().has(key) ? this.plugin.messages().raw(key) : type.buff().key();
   }

   /** Penjelasan satu buff (satu baris) untuk lore GUI/chat detail. */
   public String buffDescription(SkillBuff buff) {
      if (buff == null || buff.kind() == null) {
         return "";
      }

      String key = buff.kind() == BuffKind.POTION ? "skill.buff.potion" : "skill.buff." + buff.kind().key();
      if (this.plugin.messages().has(key)) {
         return this.plugin.messages().raw(key);
      }

      return this.buffDescription(buff.kind() == BuffKind.POTION ? SkillType.VITALITY : skillOf(buff.kind()));
   }

   /** Skill pemilik jenis buff itu (untuk pesan fallback); null bila tidak ada. */
   private static SkillType skillOf(BuffKind kind) {
      for (SkillType type : SkillType.values()) {
         if (type.buff() == kind) {
            return type;
         }
      }

      return null;
   }

   /** "slow_falling" -&gt; "Slow Falling" (dipakai bila messages.yml belum punya namanya). */
   private static String prettify(String key) {
      if (key == null || key.isEmpty()) {
         return "-";
      }

      StringBuilder result = new StringBuilder(key.length() + 4);

      for (String part : key.split("[_\\- ]")) {
         if (part.isEmpty()) {
            continue;
         }

         if (result.length() > 0) {
            result.append(' ');
         }

         result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
      }

      return result.length() == 0 ? key : result.toString();
   }

   /** Angka 1..10 menjadi angka Romawi (tingkat efek potion: I, II, III, ...). */
   private static String toRoman(int value) {
      String[] roman = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
      return value >= 1 && value < roman.length ? roman[value] : Integer.toString(value);
   }

   // ------------------------------------------------------------------ //
   //  Buff
   // ------------------------------------------------------------------ //

   public void applyBuffs(Player player) {
      if (player == null || !this.enabled || !this.buffsEnabled) {
         return;
      }

      this.applyWalkSpeed(player);
      this.applyPotions(player);
   }

   public void removeBuffs(Player player) {
      if (player == null) {
         return;
      }

      this.restoreWalkSpeed(player);
      this.removePotions(player);
   }

   private void applyWalkSpeed(Player player) {
      if (!this.probe.walkSpeedApi()) {
         return;
      }

      double percent = this.buffValue(player, SkillType.AGILITY);
      if (percent <= 0.0D) {
         this.restoreWalkSpeed(player);
         return;
      }

      SkillService.WalkSpeed state = this.walkSpeeds.get(player.getUniqueId());
      if (state == null) {
         state = new SkillService.WalkSpeed(this.probe.walkSpeed(player));
         this.walkSpeeds.put(player.getUniqueId(), state);
      }

      float target = (float) (state.base * (1.0D + percent / 100.0D));
      if (this.probe.walkSpeed(player, target)) {
         state.applied = target;
      }
   }

   private void restoreWalkSpeed(Player player) {
      SkillService.WalkSpeed state = this.walkSpeeds.remove(player.getUniqueId());
      if (state == null || !this.probe.walkSpeedApi()) {
         return;
      }

      float current = this.probe.walkSpeed(player);
      // Hanya kembalikan bila kecepatan sekarang masih hasil buff kita. Bila plugin lain sudah
      // mengubahnya, kita tidak boleh menimpa keputusan plugin itu.
      if (state.applied > 0.0F && Math.abs(current - state.applied) > 0.0001F) {
         return;
      }

      this.probe.walkSpeed(player, state.base);
   }

   /**
    * Pasang semua efek potion dari buff (Haste mining + buff {@code POTION} generik seperti
    * absorption, fire resistance, slow falling, health boost). Efek yang tidak lagi berhak dimiliki
    * pemain - level turun, skill dimatikan, atau config berubah - dihapus di sini juga, jadi buff
    * tidak pernah "nyangkut" setelah reload.
    */
   private void applyPotions(Player player) {
      Map<String, Integer> wanted = this.activePotions(player);
      SkillService.RuntimeState state = this.state(player.getUniqueId());
      boolean appliedAny = false;

      for (Map.Entry<String, Integer> entry : wanted.entrySet()) {
         if (this.probe.applyPotion(player, entry.getKey(), entry.getValue().intValue(), this.potionRefreshSeconds + 5)) {
            appliedAny = true;
         }
      }

      for (String key : state.appliedPotions) {
         if (!wanted.containsKey(key)) {
            this.probe.removePotion(player, key);
         }
      }

      state.appliedPotions.clear();
      state.appliedPotions.addAll(wanted.keySet());
      if (appliedAny) {
         state.lastPotionAt = System.currentTimeMillis();
      }
   }

   /** Hapus semua efek potion yang dipasang skill (pemain keluar, skill dimatikan, plugin disable). */
   private void removePotions(Player player) {
      SkillService.RuntimeState state = this.state(player.getUniqueId());

      for (String key : state.appliedPotions) {
         this.probe.removePotion(player, key);
      }

      state.appliedPotions.clear();
   }

   /** Efek potion yang seharusnya aktif untuk pemain ini sekarang (kunci efek -&gt; amplifier). */
   private Map<String, Integer> activePotions(Player player) {
      Map<String, Integer> result = new LinkedHashMap<>();
      if (player == null || !this.buffsEnabled || !this.probe.potionApi()) {
         return result;
      }

      for (SkillType type : SkillType.values()) {
         SkillSettings settings = this.settings(type);
         if (!settings.enabled()) {
            continue;
         }

         int level = this.level(player, type);

         for (SkillBuff buff : settings.buffs()) {
            if (!buff.kind().isPotion() || !buff.unlocked(level)) {
               continue;
            }

            String key = buff.potionKey();
            if (key == null || key.trim().isEmpty()) {
               continue;
            }

            if (buff.kind() == BuffKind.HASTE && !this.probe.hasteApi()) {
               continue;
            }

            int amplifier = buff.amplifier(level);
            if (amplifier < 0) {
               continue;
            }

            String normalized = key.trim().toLowerCase(Locale.ROOT);
            Integer current = result.get(normalized);
            if (current == null || amplifier > current.intValue()) {
               result.put(normalized, Integer.valueOf(amplifier));
            }
         }
      }

      return result;
   }

   // ------------------------------------------------------------------ //
   //  Sesi pemain
   // ------------------------------------------------------------------ //

   public void onJoin(Player player) {
      if (player == null) {
         return;
      }

      SkillProfile profile = this.profile(player.getUniqueId());
      profile.name(player.getName());
      SkillService.RuntimeState state = this.state(player.getUniqueId());
      state.reset();
      if (this.probe.healthApi()) {
         try {
            state.lastHealth = player.getHealth();
         } catch (RuntimeException exception) {
            state.lastHealth = 0.0D;
         }
      }

      this.applyBuffs(player);
   }

   public void onQuit(Player player) {
      if (player == null) {
         return;
      }

      this.removeBuffs(player);
      this.runtime.remove(player.getUniqueId());
      SkillProfile profile = this.profiles.get(player.getUniqueId());
      if (profile != null) {
         profile.name(player.getName());
         profile.updated(System.currentTimeMillis());
      }

      this.dirty = true;
      this.storage.saveAsync(this.profiles);
   }

   public void onRespawnOrHealReset(Player player) {
      if (player != null) {
         this.state(player.getUniqueId()).lastHealth = 0.0D;
      }
   }

   private SkillService.RuntimeState state(UUID uniqueId) {
      return this.runtime.computeIfAbsent(uniqueId, ignored -> new SkillService.RuntimeState());
   }

   /**
    * Catat pergerakan untuk XP agility.
    *
    * <p>{@code PlayerMoveEvent} bisa terpicu puluhan kali per detik per pemain, jadi jaraknya
    * diakumulasi lebih dulu dan XP baru dicatat setelah melewati {@code xp.min-distance}.
    * Lompatan besar (teleport, pindah world, naik kendaraan cepat) diabaikan supaya pemain
    * tidak bisa menaikkan agility dengan /rtp atau teleport plugin lain.
    */
   public void onMove(Player player, Location to) {
      if (player == null || to == null || !this.enabled) {
         return;
      }

      SkillSettings settings = this.settings(SkillType.AGILITY);
      if (!settings.enabled() || settings.xpPerBlockTravelled() <= 0.0D) {
         return;
      }

      SkillService.RuntimeState state = this.state(player.getUniqueId());
      Location previous = state.lastLocation;
      state.lastLocation = to;
      if (previous == null || !this.worldAllowed(player.getWorld()) || !this.gameModeAllowed(player)) {
         state.pendingDistance = 0.0D;
         return;
      }

      World fromWorld = previous.getWorld();
      World toWorld = to.getWorld();
      if (fromWorld == null || toWorld == null || !String.valueOf(fromWorld.getName()).equals(String.valueOf(toWorld.getName()))) {
         state.pendingDistance = 0.0D;
         return;
      }

      double distance;
      try {
         distance = Math.sqrt(Math.max(0.0D, previous.distanceSquared(to)));
      } catch (IllegalArgumentException exception) {
         // distanceSquared menolak beda world; sudah diperiksa di atas, tapi tetap dijaga.
         return;
      }

      if (!(distance > 0.0D) || distance > 10.0D) {
         return;
      }

      state.pendingDistance += distance;
      if (state.pendingDistance >= Math.max(0.1D, settings.xpMinDistance())) {
         double blocks = state.pendingDistance;
         state.pendingDistance = 0.0D;
         this.addXp(player, SkillType.AGILITY, blocks * settings.xpPerBlockTravelled());
      }
   }

   /**
    * Tandai bahwa korban terakhir kali diserang lewat proyektil, supaya XP kill dihitung ke
    * archery (bukan fighting). Catatannya dihapus saat korban mati dan dibersihkan tiap detik.
    */
   public void noteProjectileHit(UUID victimId) {
      if (victimId != null) {
         if (this.projectileHits.size() > 4096) {
            this.projectileHits.clear();
         }

         this.projectileHits.put(victimId, Long.valueOf(System.currentTimeMillis()));
      }
   }

   /** Benarkah kematian korban ini diakhiri oleh proyektil (dalam 5 detik terakhir)? */
   public boolean wasProjectileKill(UUID victimId) {
      Long at = victimId == null ? null : this.projectileHits.remove(victimId);
      return at != null && System.currentTimeMillis() - at.longValue() <= 5000L;
   }

   private void purgeProjectileHits(long now) {
      if (!this.projectileHits.isEmpty()) {
         this.projectileHits.values().removeIf(at -> now - at.longValue() > 10000L);
      }
   }

   // ------------------------------------------------------------------ //
   //  Tugas berkala
   // ------------------------------------------------------------------ //

   private void tick() {
      if (!this.enabled) {
         return;
      }

      long now = System.currentTimeMillis();
      this.purgeProjectileHits(now);

      for (Player player : Bukkit.getOnlinePlayers()) {
         try {
            this.tickPlayer(player, now);
         } catch (RuntimeException exception) {
            this.plugin.debug("Skill: tick gagal untuk " + player.getName() + " (" + exception + ").");
         }
      }
   }

   private void tickPlayer(Player player, long now) {
      SkillService.RuntimeState state = this.state(player.getUniqueId());
      boolean active = this.worldAllowed(player.getWorld()) && this.gameModeAllowed(player);
      SkillSettings endurance = this.settings(SkillType.ENDURANCE);
      if (active && endurance.enabled() && endurance.xpPerMinuteOnline() > 0.0D) {
         state.onlineSeconds++;
         if (state.onlineSeconds >= 60) {
            state.onlineSeconds -= 60;
            this.addXp(player, SkillType.ENDURANCE, endurance.xpPerMinuteOnline());
         }
      }

      if (active && this.probe.healthApi()) {
         this.tickHealth(player, state, now);
      } else {
         state.lastHealth = 0.0D;
      }

      if (active && this.buffsEnabled && this.probe.potionApi() && now - state.lastPotionAt >= this.potionRefreshSeconds * 1000L) {
         this.applyPotions(player);
      }
   }

   private void tickHealth(Player player, SkillService.RuntimeState state, long now) {
      double health;
      try {
         health = player.getHealth();
      } catch (RuntimeException exception) {
         state.lastHealth = 0.0D;
         return;
      }

      if (state.lastHealth > 0.0D) {
         double delta = health - state.lastHealth;
         if (delta > 0.01D) {
            SkillSettings recovery = this.settings(SkillType.RECOVERY);
            if (recovery.enabled() && recovery.xpPerHeartRegen() > 0.0D && this.worldAllowed(player.getWorld()) && this.gameModeAllowed(player)) {
               this.addXp(player, SkillType.RECOVERY, delta * recovery.xpPerHeartRegen());
            }

            double boost = delta * this.buffValue(player, SkillType.RECOVERY) / 100.0D;
            if (boost > 0.01D) {
               health = this.heal(player, boost, state);
            }
         } else if (delta < -0.01D) {
            state.lastDamageAt = now;
         }
      }

      state.lastHealth = health;
      double amount = this.healAmount(player);
      if (amount > 0.0D
         && health > 0.0D
         && now - state.lastHealAt >= this.healIntervalSeconds * 1000L
         && now - state.lastDamageAt >= this.healDelaySeconds(player) * 1000L
         && !this.inCombat(player)) {
         if (this.heal(player, amount, state) > health) {
            state.lastHealAt = now;
         }
      }
   }

   /** Pulihkan darah; mengembalikan darah sesudahnya (tidak berubah bila gagal/ditolak API). */
   private double heal(Player player, double amount, SkillService.RuntimeState state) {
      if (!this.probe.healthApi() || !(amount > 0.0D)) {
         return 0.0D;
      }

      try {
         double health = player.getHealth();
         if (health <= 0.0D) {
            return health;
         }

         double target = Math.min(Math.max(this.healCap, health), health + amount);
         if (target <= health) {
            return health;
         }

         player.setHealth(target);
         state.lastHealth = target;
         return target;
      } catch (RuntimeException exception) {
         this.plugin.debug("Skill: gagal memulihkan darah " + player.getName() + " (" + exception + ").");
         return 0.0D;
      }
   }

   private boolean inCombat(Player player) {
      try {
         return this.plugin.combat() != null && this.plugin.combat().enabled() && this.plugin.combat().isTagged(player);
      } catch (RuntimeException exception) {
         return false;
      }
   }

   /** Dipanggil listener damage: menandai waktu damage terakhir supaya buff vitalitas menunggu. */
   public void noteDamage(Player player) {
      if (player != null) {
         this.state(player.getUniqueId()).lastDamageAt = System.currentTimeMillis();
      }
   }

   private void saveIfDirty() {
      if (this.dirty) {
         this.dirty = false;
         this.storage.saveAsync(this.profiles);
      }
   }

   public void saveNow() {
      this.dirty = false;
      this.storage.saveNow(this.profiles);
   }

   private void playSound(Player player, String raw) {
      if (player == null || raw == null || raw.isBlank() || raw.equalsIgnoreCase("none") || !this.plugin.config().soundsEnabled()) {
         return;
      }

      PlayerSettingsService settings = this.plugin.settings();
      if (settings != null && !settings.sounds(player)) {
         return;
      }

      try {
         String key = raw.trim().toLowerCase(Locale.ROOT);
         player.playSound(Sound.sound(Key.key(key.contains(":") ? key : "minecraft:" + key), Sound.Source.MASTER, 1.0F, 1.0F));
      } catch (RuntimeException exception) {
         this.plugin.debug("Skill: bunyi tidak dikenali '" + raw + "' (" + exception + ").");
      }
   }

   // ------------------------------------------------------------------ //
   //  Admin
   // ------------------------------------------------------------------ //

   /** Setel level skill (admin). Mengembalikan level yang benar-benar terpasang. */
   public int setLevel(UUID uniqueId, SkillType type, int level) {
      int target = Math.max(1, Math.min(this.curve.maxLevel(), level));
      SkillProfile profile = this.profile(uniqueId);
      profile.xp(type, this.curve.xpToReach(target));
      this.dirty = true;
      Player player = Bukkit.getPlayer(uniqueId);
      if (player != null) {
         this.applyBuffs(player);
      }

      return target;
   }

   /** Tambah/kurangi XP (admin). Nilai negatif mengurangi XP, tidak pernah di bawah 0. */
   public double addXpAdmin(UUID uniqueId, SkillType type, double amount) {
      SkillProfile profile = this.profile(uniqueId);
      double total = Math.max(0.0D, profile.xp(type) + amount);
      profile.xp(type, total);
      this.dirty = true;
      Player player = Bukkit.getPlayer(uniqueId);
      if (player != null) {
         this.applyBuffs(player);
      }

      return total;
   }

   /** Reset satu skill (type != null) atau semua skill. Mengembalikan jumlah skill yang direset. */
   public int reset(UUID uniqueId, SkillType type) {
      SkillProfile profile = this.profile(uniqueId);
      int count = 0;
      if (type != null) {
         profile.xp(type, 0.0D);
         count = 1;
      } else {
         for (SkillType value : SkillType.values()) {
            profile.xp(value, 0.0D);
            count++;
         }
      }

      this.dirty = true;
      Player player = Bukkit.getPlayer(uniqueId);
      if (player != null) {
         this.removeBuffs(player);
      }

      return count;
   }

   public static String format(double value) {
      if (Double.isNaN(value) || Double.isInfinite(value)) {
         return "0";
      }

      long rounded = Math.round(value * 10.0D);
      return rounded % 10L == 0L ? Long.toString(rounded / 10L) : String.format(Locale.ROOT, "%.1f", Double.valueOf(value));
   }

   /** Status runtime per pemain (hanya thread utama). */
   private static final class RuntimeState {
      private boolean skipHinted;
      private double lastHealth;
      private long lastDamageAt;
      private long lastHealAt;
      private long lastPotionAt;
      /** Kunci efek potion yang dipasang skill (dipakai agar buff lama tidak nyangkut). */
      private final Set<String> appliedPotions = new LinkedHashSet<>();
      private int onlineSeconds;
      private double pendingDistance;
      private Location lastLocation;

      private void reset() {
         this.lastHealth = 0.0D;
         this.lastDamageAt = System.currentTimeMillis();
         this.lastHealAt = 0L;
         this.lastPotionAt = 0L;
         this.appliedPotions.clear();
         this.onlineSeconds = 0;
         this.pendingDistance = 0.0D;
         this.lastLocation = null;
      }
   }

   /** Kecepatan jalan asli pemain dan nilai buff yang sedang terpasang. */
   private static final class WalkSpeed {
      private final float base;
      private float applied;

      private WalkSpeed(float base) {
         this.base = base;
      }
   }

   /** Daftar world yang diizinkan (kosong = semua). */
   public Set<String> allowedWorlds() {
      return this.allowedWorlds;
   }

   /** Game mode yang tidak mendapat XP/buff (untuk pesan diagnostik). */
   public Set<String> skippedGameModes() {
      return this.skippedGameModes;
   }

   /** Ringkasan untuk log diagnostik. */
   public List<String> infoLines() {
      List<String> lines = new ArrayList<>();
      lines.add("enabled=" + this.enabled + " buffs=" + this.buffsEnabled + " max-level=" + this.curve.maxLevel());
      lines.add("curve: base-xp=" + format(this.curve.baseXp()) + " exponent=" + format(this.curve.exponent()) + " multiplier=" + format(this.globalMultiplier));
      lines.add("api: " + this.probe.summary());
      lines.add("diagnostik: " + this.diagnostics.summary());

      for (String error : this.diagnostics.errorLines()) {
         lines.add("  error terakhir: " + error);
      }

      for (SkillSettings value : this.settings) {
         lines.add("  " + value.summary());
      }

      return lines;
   }

   /**
    * Snapshot semua profil yang tersimpan (dipakai peringkat/top skill). Salinan dangkal: isinya
    * objek {@link SkillProfile} yang sama, jadi pemanggil hanya boleh membaca.
    */
   public Map<UUID, SkillProfile> profilesSnapshot() {
      return new HashMap<>(this.profiles);
   }

   /** Peringkat skill (total atau per skill) yang dibaca dari data tersimpan. */
   public SkillTop top() {
      if (this.top == null) {
         this.top = new SkillTop(this);
      }

      return this.top;
   }

   /** Nama berkas data (dipakai pesan info). */
   public String fileName() {
      return this.storage.file().getName();
   }

   /** Kunci world yang dipakai config (untuk pesan diagnostik). */
   public Map<String, Object> debugSnapshot() {
      Map<String, Object> snapshot = new HashMap<>();
      snapshot.put("enabled", Boolean.valueOf(this.enabled));
      snapshot.put("profiles", Integer.valueOf(this.profiles.size()));
      snapshot.put("runtime", Integer.valueOf(this.runtime.size()));
      snapshot.put("dirty", Boolean.valueOf(this.dirty));
      snapshot.put("save-interval", Integer.valueOf(this.saveIntervalSeconds));
      return snapshot;
   }
}
