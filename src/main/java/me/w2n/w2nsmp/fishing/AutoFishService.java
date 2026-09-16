package me.w2n.w2nsmp.fishing;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.skill.SkillService;
import me.w2n.w2nsmp.skill.SkillType;
import me.w2n.w2nsmp.utility.Items;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

/**
 * Layanan /autofishing (v1.4.1): memancing otomatis TERKONTROL.
 *
 * <p>Desain performa: SATU task global berjalan tiap detik (bukan per pemain per tick).
 * Task hanya menyentuh pemain yang mengaktifkan autofishing dan yang jadwal tangkapannya
 * sudah tiba; map kosong = task berhenti sendiri. Interval per tangkapan configurable dan
 * dibatasi bawah (minimal 3 detik) supaya tidak bisa disetel jadi mesin tak terbatas.
 *
 * <p>Anti-abuse: berhenti otomatis saat pemain keluar, mati, pindah dunia, tidak lagi
 * memegang rod, tidak di dekat air (bila disyaratkan), atau inventory penuh (perilaku
 * configurable: stop dengan notifikasi, atau auto-sell bila diizinkan config - item TIDAK
 * PERNAH dibuang diam-diam). Saat plugin disable, task dibatalkan dan status dibersihkan.
 */
public final class AutoFishService {
   private final W2NSMP plugin;
   /** Pemain aktif -> keadaan sesi (jadwal tangkapan berikutnya + statistik sesi). */
   private final Map<UUID, Session> active = new ConcurrentHashMap<>();
   private BukkitTask task;

   // Konfigurasi (dimuat ulang lewat reload()).
   private boolean enabled;
   private int intervalSeconds = 10;
   private boolean requireRod = true;
   private boolean requireWater = true;
   private int minFishingLevel;
   private String requiredAttachment = "";
   private String requiredItem = "";
   private boolean allowAutoSell;
   private String inventoryFullBehavior = "stop";
   private Material vanillaCatch = Material.COD;
   private double vanillaCatchValueXp = 4.0D;

   /** Statistik & jadwal satu sesi autofishing. */
   public static final class Session {
      private long nextCatchAt;
      private int caught;
      private int customCaught;
      private long soldTotal;
      private boolean autoSell;

      public int caught() {
         return this.caught;
      }

      public int customCaught() {
         return this.customCaught;
      }

      public long soldTotal() {
         return this.soldTotal;
      }

      public boolean autoSell() {
         return this.autoSell;
      }

      public void autoSell(boolean value) {
         this.autoSell = value;
      }
   }

   public AutoFishService(W2NSMP plugin) {
      this.plugin = plugin;
   }

   // ------------------------------------------------------------------ //
   // Konfigurasi
   // ------------------------------------------------------------------ //

   public void reload() {
      var config = this.plugin.config().raw();
      this.enabled = config.getBoolean("autofishing.enabled", false);
      // Batas bawah 3 detik: mencegah konfigurasi berubah jadi mesin tanpa batas.
      this.intervalSeconds = Math.max(3, config.getInt("autofishing.interval-seconds", 15));
      this.requireRod = config.getBoolean("autofishing.require.rod", true);
      this.requireWater = config.getBoolean("autofishing.require.water", true);
      this.minFishingLevel = Math.max(0, config.getInt("autofishing.require.min-fishing-level", 10));
      this.requiredAttachment = config.getString("autofishing.require.attachment", "");
      this.requiredItem = config.getString("autofishing.require.item", "");
      this.allowAutoSell = config.getBoolean("autofishing.auto-sell.enabled", false);
      String behavior = config.getString("autofishing.inventory-full", "stop");
      this.inventoryFullBehavior = "sell".equalsIgnoreCase(behavior) ? "sell" : "stop";
      Material material = Material.matchMaterial(config.getString("autofishing.vanilla-catch", "COD"));
      this.vanillaCatch = material == null || material.isAir() ? Material.COD : material;
      this.vanillaCatchValueXp = Math.max(0.0D, config.getDouble("autofishing.vanilla-catch-skill-xp", 4.0D));
      if (!this.enabled) {
         this.stopAll("fishing.autofish.stopped-disabled");
      }
   }

   public boolean enabled() {
      return this.enabled;
   }

   public int intervalSeconds() {
      return this.intervalSeconds;
   }

   public boolean requireRod() {
      return this.requireRod;
   }

   public boolean requireWater() {
      return this.requireWater;
   }

   public int minFishingLevel() {
      return this.minFishingLevel;
   }

   public String requiredAttachment() {
      return this.requiredAttachment == null ? "" : this.requiredAttachment;
   }

   public String requiredItem() {
      return this.requiredItem == null ? "" : this.requiredItem;
   }

   public boolean allowAutoSell() {
      return this.allowAutoSell;
   }

   public String inventoryFullBehavior() {
      return this.inventoryFullBehavior;
   }

   // ------------------------------------------------------------------ //
   // Nyala/mati per pemain
   // ------------------------------------------------------------------ //

   public boolean isActive(Player player) {
      return player != null && this.active.containsKey(player.getUniqueId());
   }

   public Session session(Player player) {
      return player == null ? null : this.active.get(player.getUniqueId());
   }

   /**
    * Coba nyalakan autofishing. Mengembalikan {@code null} bila berhasil, atau kunci pesan
    * alasan gagal (untuk dikirim ke pemain oleh pemanggil).
    */
   public String start(Player player) {
      if (!this.enabled) {
         return "fishing.autofish.feature-disabled";
      }

      if (player == null || !player.isOnline()) {
         return "fishing.autofish.feature-disabled";
      }

      String blocked = this.requirementBlocking(player);
      if (blocked != null) {
         return blocked;
      }

      Session session = new Session();
      session.nextCatchAt = System.currentTimeMillis() + this.intervalSeconds * 1000L;
      this.active.put(player.getUniqueId(), session);
      this.ensureTask();
      return null;
   }

   /** Matikan autofishing pemain; {@code messageKey} null = tanpa pesan. */
   public void stop(Player player, String messageKey) {
      if (player == null) {
         return;
      }

      if (this.active.remove(player.getUniqueId()) != null && messageKey != null) {
         this.plugin.messages().send(player, messageKey);
      }

      this.stopTaskIfIdle();
   }

   public void stopQuietly(UUID uuid) {
      if (uuid != null) {
         this.active.remove(uuid);
         this.stopTaskIfIdle();
      }
   }

   /** Matikan semua sesi (reload/disable). */
   public void stopAll(String messageKey) {
      for (UUID uuid : this.active.keySet()) {
         Player player = Bukkit.getPlayer(uuid);
         if (player != null && messageKey != null) {
            this.plugin.messages().send(player, messageKey);
         }
      }

      this.active.clear();
      this.stopTaskIfIdle();
   }

   public void shutdown() {
      this.active.clear();
      if (this.task != null) {
         try {
            this.task.cancel();
         } catch (Throwable ignored) {
         }

         this.task = null;
      }
   }

   /**
    * Kunci pesan syarat yang BELUM terpenuhi (null = semua syarat lolos). Syarat:
    * rod di tangan, level Fishing minimum, attachment tertentu, item tertentu, dekat air.
    */
   public String requirementBlocking(Player player) {
      FishingService fishing = this.plugin.fishing();
      if (fishing == null || !fishing.enabled()) {
         return "fishing.autofish.feature-disabled";
      }

      ItemStack rod = player.getInventory().getItemInMainHand();
      if (this.requireRod && !fishing.isRod(rod)) {
         return "fishing.autofish.need-rod";
      }

      SkillService skills = this.plugin.skills();
      if (this.minFishingLevel > 0 && skills != null && skills.enabled()
         && skills.level(player, SkillType.FISHING) < this.minFishingLevel) {
         return "fishing.autofish.need-level";
      }

      if (!this.requiredAttachment().isEmpty() && fishing.isRod(rod)
         && !fishing.attachments(rod).contains(this.requiredAttachment())) {
         return "fishing.autofish.need-attachment";
      }

      if (!this.requiredItem().isEmpty() && !this.hasItem(player, this.requiredItem())) {
         return "fishing.autofish.need-item";
      }

      if (this.requireWater && !this.nearWater(player)) {
         return "fishing.autofish.need-water";
      }

      return null;
   }

   /** Publik untuk kartu syarat GUI (status per-syarat tanpa urutan pengecekan). */
   public boolean hasRequiredItem(Player player) {
      return this.requiredItem().isEmpty() || this.hasItem(player, this.requiredItem());
   }

   private boolean hasItem(Player player, String itemId) {
      FishingService fishing = this.plugin.fishing();
      if (fishing == null) {
         return false;
      }

      try {
         for (ItemStack stack : player.getInventory().getContents()) {
            if (!Items.isEmpty(stack) && itemId.equalsIgnoreCase(fishing.itemId(stack))) {
               return true;
            }
         }
      } catch (Throwable ignored) {
      }

      return false;
   }

   /** Air dalam radius 3 blok horizontal, 1 blok vertikal dari kaki pemain. */
   private boolean nearWater(Player player) {
      try {
         int baseX = player.getLocation().getBlockX();
         int baseY = player.getLocation().getBlockY();
         int baseZ = player.getLocation().getBlockZ();

         for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
               for (int dy = -1; dy <= 1; dy++) {
                  if (player.getWorld().getBlockAt(baseX + dx, baseY + dy, baseZ + dz).getType() == Material.WATER) {
                     return true;
                  }
               }
            }
         }
      } catch (Throwable ignored) {
      }

      return false;
   }

   // ------------------------------------------------------------------ //
   // Scheduler global
   // ------------------------------------------------------------------ //

   private void ensureTask() {
      if (this.task == null) {
         // Satu task untuk SEMUA pemain, tiap 20 tick. Bukan per-pemain-per-tick.
         this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 20L, 20L);
      }
   }

   private void stopTaskIfIdle() {
      if (this.active.isEmpty() && this.task != null) {
         try {
            this.task.cancel();
         } catch (Throwable ignored) {
         }

         this.task = null;
      }
   }

   private void tick() {
      if (this.active.isEmpty()) {
         this.stopTaskIfIdle();
         return;
      }

      long now = System.currentTimeMillis();
      Iterator<Map.Entry<UUID, Session>> iterator = this.active.entrySet().iterator();

      while (iterator.hasNext()) {
         Map.Entry<UUID, Session> entry = iterator.next();
         Player player = Bukkit.getPlayer(entry.getKey());
         if (player == null || !player.isOnline() || player.isDead()) {
            // Keluar/mati: hentikan tanpa pesan (listener juga membersihkan saat quit).
            iterator.remove();
            continue;
         }

         Session session = entry.getValue();
         if (now < session.nextCatchAt) {
            continue;
         }

         session.nextCatchAt = now + this.intervalSeconds * 1000L;

         String blocked = this.requirementBlocking(player);
         if (blocked != null) {
            // Syarat tidak lagi terpenuhi (rod hilang/patah, menjauh dari air, dsb).
            iterator.remove();
            this.plugin.messages().send(player, blocked);
            this.plugin.messages().send(player, "fishing.autofish.stopped");
            continue;
         }

         try {
            if (!this.doCatch(player, session)) {
               iterator.remove();
            }
         } catch (Throwable throwable) {
            this.plugin.debug("Autofish: gangguan tangkapan (" + throwable + ").");
         }
      }

      this.stopTaskIfIdle();
   }

   /** Satu siklus tangkapan. Mengembalikan false bila sesi harus dihentikan. */
   private boolean doCatch(Player player, Session session) {
      FishingService fishing = this.plugin.fishing();
      SkillService skills = this.plugin.skills();
      if (fishing == null) {
         return false;
      }

      ItemStack rod = player.getInventory().getItemInMainHand();
      boolean holdingRod = fishing.isRod(rod);
      int fishingLevel = skills == null ? 0 : skills.level(player, SkillType.FISHING);
      int rodLevel = holdingRod ? fishing.rodLevel(rod) : 1;

      // Undi ikan: efek rod tetap berlaku, sama seperti memancing manual.
      double bonusChance = holdingRod ? fishing.effectTotal(rod, "custom-chance") : 0.0D;
      double rarityBoost = holdingRod ? 1.0D + fishing.effectTotal(rod, "rarity-boost") / 100.0D : 1.0D;
      CustomFish rolled = fishing.roll(this.biomeName(player), this.storming(player), this.worldTime(player),
         fishingLevel, rodLevel, bonusChance, rarityBoost);

      ItemStack catchStack;
      double skillXp;
      if (rolled != null) {
         double valueBonus = holdingRod ? fishing.effectTotal(rod, "value") : 0.0D;
         catchStack = fishing.createFish(rolled, valueBonus);
         skillXp = rolled.xp();
      } else {
         catchStack = new ItemStack(this.vanillaCatch);
         skillXp = this.vanillaCatchValueXp;
      }

      // Auto-sell menjual TANGKAPAN INI SAJA, langsung, tanpa menyentuh item lain.
      boolean sellNow = session.autoSell() && this.allowAutoSell && this.plugin.sell() != null;
      if (!sellNow && this.isInventoryFull(player, catchStack)) {
         if ("sell".equals(this.inventoryFullBehavior) && this.allowAutoSell && this.plugin.sell() != null) {
            sellNow = true;
         } else {
            // Konfigurasi "stop": berhenti + beri tahu. Item TIDAK dibuang.
            this.plugin.messages().send(player, "fishing.autofish.inventory-full");
            this.plugin.messages().send(player, "fishing.autofish.stopped");
            return false;
         }
      }

      if (sellNow) {
         long price = (long) this.plugin.sell().currentUnitPrice(catchStack) * catchStack.getAmount();
         if (price > 0L && this.plugin.sell().payout(player, price)) {
            this.plugin.sell().recordSale(List.of(catchStack));
            session.soldTotal += price;
         } else {
            // Tidak bisa dijual (harga 0/economy mati): fallback simpan ke inventory.
            if (this.isInventoryFull(player, catchStack)) {
               this.plugin.messages().send(player, "fishing.autofish.inventory-full");
               this.plugin.messages().send(player, "fishing.autofish.stopped");
               return false;
            }

            player.getInventory().addItem(catchStack);
         }
      } else {
         player.getInventory().addItem(catchStack);
      }

      session.caught++;
      if (rolled != null) {
         session.customCaught++;
         this.plugin.messages().send(player, "fishing.autofish.caught-custom",
            "fish", rolled.fishName(), "rarity", fishing.rarityLabel(rolled.rarity()));
         if (fishing.discovery().discover(player.getUniqueId(), rolled.id())) {
            this.plugin.messages().send(player, "fishing.discovered",
               "fish", rolled.fishName(), "rarity", fishing.rarityLabel(rolled.rarity()));
         }
      }

      // XP skill & XP rod - autofishing memberi XP lebih kecil (configurable lewat multiplier).
      double xpMultiplier = Math.max(0.0D,
         this.plugin.config().raw().getDouble("autofishing.skill-xp-multiplier", 0.5D));
      if (skills != null && skills.enabled() && skillXp > 0.0D && xpMultiplier > 0.0D) {
         double xpBonus = holdingRod ? fishing.effectTotal(rod, "xp") : 0.0D;
         skills.addXp(player, SkillType.FISHING, skillXp * xpMultiplier * (1.0D + xpBonus / 100.0D));
      }

      if (holdingRod) {
         fishing.addRodXp(rod, rolled != null ? fishing.rodXpPerCustomCatch() : fishing.rodXpPerCatch());
         fishing.refreshRodLore(rod);
         player.getInventory().setItemInMainHand(rod);
      }

      return true;
   }

   /** Penuh = tidak ada slot kosong DAN tidak ada stack sejenis yang masih muat. */
   private boolean isInventoryFull(Player player, ItemStack incoming) {
      try {
         if (player.getInventory().firstEmpty() >= 0) {
            return false;
         }

         for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (!Items.isEmpty(stack) && stack.isSimilar(incoming)
               && stack.getAmount() + incoming.getAmount() <= stack.getMaxStackSize()) {
               return false;
            }
         }
      } catch (Throwable ignored) {
      }

      return true;
   }

   private String biomeName(Player player) {
      try {
         return String.valueOf(player.getWorld()
            .getBlockAt(player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ())
            .getBiome());
      } catch (Throwable throwable) {
         return null;
      }
   }

   private boolean storming(Player player) {
      try {
         return player.getWorld().hasStorm();
      } catch (Throwable throwable) {
         return false;
      }
   }

   private long worldTime(Player player) {
      try {
         return player.getWorld().getTime();
      } catch (Throwable throwable) {
         return 6000L;
      }
   }
}
