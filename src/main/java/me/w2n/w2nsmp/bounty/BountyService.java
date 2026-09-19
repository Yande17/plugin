package me.w2n.w2nsmp.bounty;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.stats.StatType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class BountyService {
   private final W2NSMP plugin;
   private final BountyStorage storage;
   private final Map<UUID, Long> amounts = new LinkedHashMap<>();
   private final Map<UUID, String> names = new HashMap<>();
   private final Map<UUID, Map<UUID, Long>> claims = new HashMap<>();
   private BukkitTask pruneTask;
   private boolean loaded;

   public BountyService(W2NSMP plugin) {
      super();
      this.plugin = plugin;
      this.storage = new BountyStorage(plugin);
   }

   public void load() {
      this.startTasks();
      BountyStorage.Snapshot snapshot = this.storage.load();
      this.amounts.clear();
      this.amounts.putAll(snapshot.amounts());
      this.names.clear();
      this.names.putAll(snapshot.names());
      this.claims.clear();
      this.claims.putAll(snapshot.claims());
      this.loaded = true;
   }

   public void reload() {
      this.startTasks();
      if (this.storage.loadFailed()) {
         this.plugin.getLogger().warning("bounties.yml masih tidak bisa dibaca; bounty dibiarkan seperti semula sampai berkas diperbaiki.");
      } else {
         int before = this.amounts.size();
         if (this.isDirty()) {
            this.saveNow();
         }

         this.load();
         this.plugin.getLogger().info("Bounty dimuat ulang: " + before + " -> " + this.amounts.size() + " pemain berburu.");
      }
   }

   public void saveNow() {
      this.storage.saveNow(this.snapshot());
   }

   private void startTasks() {
      this.stopTasks();
      this.pruneTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::pruneClaims, 6000L, 6000L);
   }

   private void stopTasks() {
      if (this.pruneTask != null) {
         this.pruneTask.cancel();
         this.pruneTask = null;
      }
   }

   public boolean taskRunning() {
      return this.pruneTask != null;
   }

   public void shutdown() {
      this.stopTasks();
      this.saveNow();
   }

   public boolean isDirty() {
      return this.storage.isDirty();
   }

   public File file() {
      return this.storage.file();
   }

   public boolean loadFailed() {
      return this.storage.loadFailed();
   }

   public boolean isLoaded() {
      return this.loaded;
   }

   public boolean enabled() {
      return this.plugin.config().bountyEnabled();
   }

   private BountyStorage.Snapshot snapshot() {
      Map<UUID, Map<UUID, Long>> claimsCopy = new LinkedHashMap<>();

      for (Entry<UUID, Map<UUID, Long>> entry : this.claims.entrySet()) {
         claimsCopy.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
      }

      return new BountyStorage.Snapshot(new LinkedHashMap<>(this.amounts), new HashMap<>(this.names), claimsCopy);
   }

   private void save() {
      this.storage.saveAsync(this.snapshot());
   }

   public long bounty(UUID uniqueId) {
      Long value = this.amounts.get(uniqueId);
      return value == null ? 0L : value;
   }

   public long bounty(OfflinePlayer player) {
      return player == null ? 0L : this.bounty(player.getUniqueId());
   }

   public int count() {
      return this.amounts.size();
   }

   public long total() {
      long sum = 0L;

      for (long value : this.amounts.values()) {
         sum += value;
      }

      return sum;
   }

   public String formatted(UUID uniqueId) {
      long value = this.bounty(uniqueId);
      if (value <= 0L) {
         return "-";
      } else {
         return this.plugin.economy() != null && this.plugin.economy().isEnabled() ? this.plugin.economy().format(value) : Long.toString(value);
      }
   }

   public List<BountyEntry> top() {
      List<BountyEntry> entries = new ArrayList<>(this.amounts.size());

      for (Entry<UUID, Long> entry : this.amounts.entrySet()) {
         if (entry.getValue() != null && entry.getValue() > 0L) {
            entries.add(new BountyEntry(entry.getKey(), this.nameOf(entry.getKey()), entry.getValue()));
         }
      }

      entries.sort(Comparator.comparingLong(BountyEntry::amount).reversed().thenComparing(entryx -> entryx.displayName("")));
      return entries;
   }

   public int pageSize() {
      return this.plugin.config().bountyPageSize();
   }

   public List<BountyEntry> page(int page) {
      List<BountyEntry> all = this.top();
      int size = this.pageSize();
      int from = Math.max(0, (page - 1) * size);
      return from >= all.size() ? List.of() : List.copyOf(all.subList(from, Math.min(all.size(), from + size)));
   }

   public int pages() {
      return Math.max(1, (int)Math.ceil((double)this.top().size() / this.pageSize()));
   }

   public String nameOf(UUID uniqueId) {
      String stored = this.names.get(uniqueId);
      return stored != null && !stored.isBlank() ? stored : null;
   }

   public void rememberName(Player player) {
      if (player != null) {
         UUID uniqueId = player.getUniqueId();
         String previous = this.names.get(uniqueId);
         if (previous == null || !previous.equals(player.getName()) || this.amounts.containsKey(uniqueId)) {
            if (previous == null || !previous.equals(player.getName())) {
               this.names.put(uniqueId, player.getName());
               if (this.amounts.containsKey(uniqueId)) {
                  this.save();
               }
            }
         }
      }
   }

   public int claimRecords() {
      int total = 0;

      for (Map<UUID, Long> entry : this.claims.values()) {
         total += entry.size();
      }

      return total;
   }

   public int repeatKillRemaining(UUID victim, UUID killer, int seconds) {
      Map<UUID, Long> perKiller = this.claims.get(victim);
      if (perKiller == null) {
         return 0;
      }

      Long last = perKiller.get(killer);
      if (last == null) {
         return 0;
      }

      long remaining = last + seconds * 1000L - System.currentTimeMillis();
      return remaining <= 0L ? 0 : (int)Math.ceil(remaining / 1000.0);
   }

   public BountyService.PlaceResult place(Player creator, OfflinePlayer target, long amount) {
      if (!this.enabled()) {
         this.plugin.messages().send(creator, "bounty.disabled");
         return BountyService.PlaceResult.DISABLED;
      }

      if (target == null) {
         this.plugin.messages().send(creator, "bounty.unknown-player", "player", "?");
         return BountyService.PlaceResult.UNKNOWN_TARGET;
      }

      if (this.plugin.economy() != null && this.plugin.economy().isEnabled()) {
         String targetName = target.getName() == null ? target.getUniqueId().toString() : target.getName();
         if (this.plugin.config().bountyPreventSelf() && creator.getUniqueId().equals(target.getUniqueId())) {
            this.plugin.messages().send(creator, "bounty.self");
            return BountyService.PlaceResult.SELF;
         }

         if (amount < this.plugin.config().bountyMinimum()) {
            this.plugin.messages().send(creator, "bounty.too-low", "amount", this.plugin.economy().format(this.plugin.config().bountyMinimum()));
            return BountyService.PlaceResult.TOO_LOW;
         }

         if (amount > this.plugin.config().bountyMaximum()) {
            this.plugin.messages().send(creator, "bounty.too-high", "amount", this.plugin.economy().format(this.plugin.config().bountyMaximum()));
            return BountyService.PlaceResult.TOO_HIGH;
         }

         long current = this.bounty(target.getUniqueId());
         if (current + amount > this.plugin.config().bountyMaxTotal()) {
            this.plugin
               .messages()
               .send(
                  creator,
                  "bounty.over-limit",
                  "amount",
                  this.plugin.economy().format(this.plugin.config().bountyMaxTotal()),
                  "current",
                  this.plugin.economy().format(current)
               );
            return BountyService.PlaceResult.OVER_LIMIT;
         }

         if (!creator.hasPermission("w2nsmp.bounty.admin") && !this.plugin.economy().has(creator, amount)) {
            this.plugin
               .messages()
               .send(
                  creator,
                  "bounty.no-money",
                  "amount",
                  this.plugin.economy().format(amount),
                  "balance",
                  this.plugin.economy().format(this.plugin.economy().balance(creator))
               );
            return BountyService.PlaceResult.NO_MONEY;
         }

         if (!creator.hasPermission("w2nsmp.bounty.admin") && !this.plugin.economy().withdraw(creator, amount)) {
            this.plugin
               .messages()
               .send(
                  creator,
                  "bounty.no-money",
                  "amount",
                  this.plugin.economy().format(amount),
                  "balance",
                  this.plugin.economy().format(this.plugin.economy().balance(creator))
               );
            return BountyService.PlaceResult.NO_MONEY;
         }

         long added = current + amount;
         this.amounts.put(target.getUniqueId(), added);
         this.refreshDisplay(target.getUniqueId());
         this.rememberName(creator);
         if (target.isOnline() && target.getPlayer() != null) {
            this.rememberName(target.getPlayer());
         } else {
            this.names.putIfAbsent(target.getUniqueId(), targetName);
         }

         this.save();
         String amountText = this.plugin.economy().format(amount);
         String potText = this.plugin.economy().format(added);
         this.plugin.messages().send(creator, "bounty.placed", "player", targetName, "target", targetName, "amount", amountText, "total", potText);
         Player online = target.getPlayer();
         if (online != null && online.isOnline() && (this.plugin.settings() == null || this.plugin.settings().bountyNotifications(online))) {
            this.plugin.messages().send(online, "bounty.received", "player", creator.getName(), "amount", amountText, "total", potText);
         }

         if (added >= this.plugin.config().bountyAnnounceMinimum()) {
            Bukkit.broadcast(
               this.plugin
                  .messages()
                  .component(
                     "bounty.broadcast", "player", targetName, "target", targetName, "amount", amountText, "total", potText, "creator", creator.getName()
                  )
            );
         }

         return BountyService.PlaceResult.OK;
      } else {
         this.plugin.messages().send(creator, "bounty.no-economy");
         return BountyService.PlaceResult.NO_ECONOMY;
      }
   }

   public BountyService.ClaimResult claim(Player killer, Player victim) {
      if (this.enabled() && killer != null && victim != null) {
         if (killer.getUniqueId().equals(victim.getUniqueId())) {
            return BountyService.ClaimResult.NONE;
         }

         long pot = this.bounty(victim.getUniqueId());
         if (pot <= 0L) {
            return BountyService.ClaimResult.NONE;
         }

         if (this.plugin.economy() == null || !this.plugin.economy().isEnabled()) {
            return BountyService.ClaimResult.FAILED;
         }

         if (this.plugin.config().bountyBlockSameIp() && this.sameAddress(killer, victim)) {
            this.plugin.messages().send(killer, "bounty.blocked-same-ip", "player", victim.getName());
            return BountyService.ClaimResult.BLOCKED_SAME_IP;
         }

         int cooldown = this.plugin.config().bountyRepeatKillSeconds();
         if (this.plugin.config().bountyPreventRepeatedKills()
            && cooldown > 0
            && this.repeatKillRemaining(victim.getUniqueId(), killer.getUniqueId(), cooldown) > 0) {
            this.plugin
               .messages()
               .send(
                  killer,
                  "bounty.blocked-repeat",
                  "player",
                  victim.getName(),
                  "seconds",
                  Integer.toString(this.repeatKillRemaining(victim.getUniqueId(), killer.getUniqueId(), cooldown))
               );
            return BountyService.ClaimResult.BLOCKED_REPEAT;
         }

         int minPlaytime = this.plugin.config().bountyMinVictimPlaytimeMinutes();
         if (minPlaytime > 0 && this.plugin.stats() != null && this.plugin.stats().value(victim.getUniqueId(), StatType.PLAYTIME) < minPlaytime * 60L) {
            this.plugin.messages().send(killer, "bounty.blocked-playtime", "player", victim.getName());
            return BountyService.ClaimResult.BLOCKED_PLAYTIME;
         }

         long tax = Math.round(pot * (this.plugin.config().bountyTaxPercent() / 100.0));
         long payout = pot - tax;
         if (payout > 0L && !this.plugin.economy().deposit(killer, payout)) {
            this.plugin
               .getLogger()
               .warning(
                  "Bounty " + pot + " untuk " + victim.getName() + " gagal dibayarkan ke " + killer.getName() + " (Vault menolak deposit). Pot dipertahankan."
               );
            return BountyService.ClaimResult.FAILED;
         }

         this.amounts.remove(victim.getUniqueId());
         this.refreshDisplay(victim.getUniqueId());
         this.rememberClaim(victim.getUniqueId(), killer.getUniqueId());
         this.save();
         String payoutText = this.plugin.economy().format(payout);
         String potText = this.plugin.economy().format(pot);
         if (tax > 0L) {
            this.plugin
               .messages()
               .send(
                  killer,
                  "bounty.paid-tax",
                  "player",
                  victim.getName(),
                  "target",
                  victim.getName(),
                  "amount",
                  payoutText,
                  "total",
                  potText,
                  "tax",
                  this.plugin.economy().format(tax)
               );
         } else {
            this.plugin.messages().send(killer, "bounty.paid", "player", victim.getName(), "target", victim.getName(), "amount", payoutText, "total", potText);
         }

         if (victim.isOnline()) {
            this.plugin.messages().send(victim, "bounty.lost", "player", killer.getName(), "amount", payoutText);
         }

         if (pot >= this.plugin.config().bountyAnnounceMinimum()) {
            Bukkit.broadcast(
               this.plugin
                  .messages()
                  .component("bounty.broadcast-claim", "player", victim.getName(), "target", victim.getName(), "killer", killer.getName(), "amount", payoutText)
            );
         }

         return BountyService.ClaimResult.PAID;
      } else {
         return BountyService.ClaimResult.NONE;
      }
   }

   public long setBounty(UUID target, long amount) {
      if (target == null) {
         return 0L;
      }

      long previous = this.bounty(target);
      if (amount <= 0L) {
         this.amounts.remove(target);
      } else {
         this.amounts.put(target, Math.min(amount, this.plugin.config().bountyMaxTotal()));
      }

      this.save();
      this.refreshDisplay(target);
      return previous;
   }

   public long clear(UUID target) {
      return this.setBounty(target, 0L);
   }

   public int clearAll() {
      int removed = this.amounts.size();
      this.amounts.clear();
      this.claims.clear();
      this.save();

      for (Player online : Bukkit.getOnlinePlayers()) {
         this.refreshDisplay(online.getUniqueId());
      }

      return removed;
   }

   /** v1.7.0 (PHASE 3): bounty berubah -> segarkan baris bounty di atas kepala seketika. */
   private void refreshDisplay(UUID target) {
      try {
         if (this.plugin.nametag() != null && target != null) {
            Player online = Bukkit.getPlayer(target);
            if (online != null && online.isOnline()) {
               this.plugin.nametag().refresh(online);
            }
         }
      } catch (Throwable ignored) {
      }
   }

   public int pruneClaims() {
      int cooldown = Math.max(1, this.plugin.config().bountyRepeatKillSeconds());
      long deadline = System.currentTimeMillis() - cooldown * 2000L;
      int removed = 0;

      for (Entry<UUID, Map<UUID, Long>> entry : new ArrayList<>(this.claims.entrySet())) {
         Map<UUID, Long> perKiller = entry.getValue();
         int before = perKiller.size();
         perKiller.entrySet().removeIf(killer -> killer.getValue() == null || killer.getValue() < deadline);
         removed += before - perKiller.size();
         if (perKiller.isEmpty()) {
            this.claims.remove(entry.getKey());
         }
      }

      if (removed > 0) {
         this.save();
      }

      return removed;
   }

   public long lastClaim(UUID victim, UUID killer) {
      Map<UUID, Long> perKiller = this.claims.get(victim);
      return perKiller != null && perKiller.get(killer) != null ? perKiller.get(killer) : 0L;
   }

   private void rememberClaim(UUID victim, UUID killer) {
      this.claims.computeIfAbsent(victim, key -> new HashMap<>()).put(killer, System.currentTimeMillis());
   }

   private boolean sameAddress(Player first, Player second) {
      InetSocketAddress firstAddress = first.getAddress();
      InetSocketAddress secondAddress = second.getAddress();
      return firstAddress != null && secondAddress != null
         ? firstAddress.getAddress() != null && firstAddress.getAddress().equals(secondAddress.getAddress())
         : false;
   }

   public enum ClaimResult {
      NONE,
      PAID,
      BLOCKED_REPEAT,
      BLOCKED_SAME_IP,
      BLOCKED_PLAYTIME,
      FAILED;
   }

   public enum PlaceResult {
      OK,
      DISABLED,
      SELF,
      TOO_LOW,
      TOO_HIGH,
      NO_MONEY,
      OVER_LIMIT,
      UNKNOWN_TARGET,
      NO_ECONOMY;
   }
}
