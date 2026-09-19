package me.w2n.w2nsmp.fishing;

import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import me.w2n.w2nsmp.W2NSMP;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Fish Inventory - penyimpanan ikan custom terpisah per pemain (v1.8.0, PHASE 4).
 *
 * <p>Ikan hasil memancing TIDAK lagi memenuhi inventory normal: tangkapan custom masuk ke
 * sini dan dilihat/diambil lewat halaman "Fish Storage" di /fish. Setiap ikan disimpan
 * PER EKOR sebagai Base64 dari serialisasi biner Paper (pola sama dengan AuctionStorage) -
 * ikan custom memiliki size/value unik di PDC sehingga TIDAK boleh ditumpuk sembarangan;
 * kapasitas dihitung per ekor.
 *
 * <p>Kapasitas bertingkat: {@code fishing.fish-inventory.capacities} (level 1 = entri
 * pertama), upgrade berbayar lewat economy existing ({@code upgrade-costs}). Semua angka
 * dari config - tidak ada yang hardcoded.
 *
 * <p>Anti-hilang & anti-dupe:
 * <ul>
 *   <li>penuh -> ikan jatuh ke inventory normal (fallback lama); tidak pernah dibuang;</li>
 *   <li>withdraw: ikan DIHAPUS dari storage dulu, baru masuk inventory pemain - dalam satu
 *       event main-thread, jadi tidak ada jendela dupe; inventory penuh -> batal tanpa
 *       mengubah storage;</li>
 *   <li>tulis berkas async & digabung per tick (pola BountyStorage); saveNow saat shutdown;</li>
 *   <li>relog/restart/death/teleport tidak menyentuh data - hidup di berkas, bukan di
 *       inventory pemain.</li>
 * </ul>
 */
public final class FishInventory {
   public static final String FILE_NAME = "fish-inventory.yml";

   private final W2NSMP plugin;
   private final File file;
   private final Map<UUID, List<ItemStack>> fish = new ConcurrentHashMap<>();
   private final Map<UUID, Integer> levels = new ConcurrentHashMap<>();
   private final Object writeLock = new Object();
   private final AtomicLong version = new AtomicLong();
   private final AtomicLong writtenVersion = new AtomicLong();
   private boolean saveQueued;
   private volatile boolean loaded;

   public FishInventory(W2NSMP plugin) {
      this.plugin = plugin;
      this.file = new File(plugin.getDataFolder(), FILE_NAME);
   }

   // ------------------------------------------------------------------ //
   // Config
   // ------------------------------------------------------------------ //

   public boolean enabled() {
      return this.plugin.config().raw().getBoolean("fishing.fish-inventory.enabled", true)
         && this.plugin.fishing() != null
         && this.plugin.fishing().enabled();
   }

   /** Kapasitas per level (level 1 = entri pertama). Selalu minimal satu entri. */
   public List<Integer> capacities() {
      List<Integer> result = new ArrayList<>();
      for (Object value : this.plugin.config().raw().getList("fishing.fish-inventory.capacities", List.of())) {
         if (value instanceof Number number && number.intValue() > 0) {
            result.add(Math.min(number.intValue(), 100000));
         }
      }

      if (result.isEmpty()) {
         result.add(25);
      }

      return result;
   }

   /** Biaya upgrade KE level (index 0 = biaya menuju level 2). */
   public List<Long> upgradeCosts() {
      List<Long> result = new ArrayList<>();
      for (Object value : this.plugin.config().raw().getList("fishing.fish-inventory.upgrade-costs", List.of())) {
         if (value instanceof Number number) {
            result.add(Math.max(0L, number.longValue()));
         }
      }

      return result;
   }

   public int maxLevel() {
      return this.capacities().size();
   }

   public int level(UUID uniqueId) {
      int stored = uniqueId == null ? 1 : this.levels.getOrDefault(uniqueId, 1);
      return Math.max(1, Math.min(stored, this.maxLevel()));
   }

   public int capacity(UUID uniqueId) {
      List<Integer> capacities = this.capacities();
      return capacities.get(Math.min(this.level(uniqueId), capacities.size()) - 1);
   }

   /** Kapasitas level berikut; -1 bila sudah level maksimum. */
   public int nextCapacity(UUID uniqueId) {
      List<Integer> capacities = this.capacities();
      int level = this.level(uniqueId);
      return level >= capacities.size() ? -1 : capacities.get(level);
   }

   /** Biaya upgrade ke level berikut; -1 bila sudah maksimum / config tidak lengkap. */
   public long upgradeCost(UUID uniqueId) {
      int level = this.level(uniqueId);
      if (level >= this.maxLevel()) {
         return -1L;
      }

      List<Long> costs = this.upgradeCosts();
      return level - 1 < costs.size() ? costs.get(level - 1) : -1L;
   }

   // ------------------------------------------------------------------ //
   // Isi
   // ------------------------------------------------------------------ //

   public int count(UUID uniqueId) {
      List<ItemStack> list = uniqueId == null ? null : this.fish.get(uniqueId);
      return list == null ? 0 : list.size();
   }

   public boolean isFull(UUID uniqueId) {
      return this.count(uniqueId) >= this.capacity(uniqueId);
   }

   /** Salinan daftar ikan pemain (untuk GUI; urutan = urutan masuk). */
   public List<ItemStack> list(UUID uniqueId) {
      List<ItemStack> list = uniqueId == null ? null : this.fish.get(uniqueId);
      if (list == null) {
         return List.of();
      }

      List<ItemStack> copy = new ArrayList<>(list.size());
      synchronized (list) {
         for (ItemStack stack : list) {
            copy.add(stack.clone());
         }
      }

      return copy;
   }

   /**
    * Simpan satu ikan; false bila penuh/invalid (pemanggil wajib fallback ke inventory
    * normal supaya ikan tidak pernah hilang).
    */
   public boolean add(UUID uniqueId, ItemStack stack) {
      if (uniqueId == null || stack == null || stack.getType().isAir() || !this.loaded) {
         return false;
      }

      List<ItemStack> list = this.fish.computeIfAbsent(uniqueId, ignored -> new ArrayList<>());
      synchronized (list) {
         if (list.size() >= this.capacity(uniqueId)) {
            return false;
         }

         list.add(stack.clone());
      }

      this.saveAsync();
      return true;
   }

   /**
    * Ambil ikan pada indeks (hapus dari storage dan kembalikan salinannya); null bila indeks
    * tidak valid. Pemanggil menaruh hasilnya ke inventory pemain DI TICK YANG SAMA.
    */
   public ItemStack take(UUID uniqueId, int index) {
      List<ItemStack> list = uniqueId == null ? null : this.fish.get(uniqueId);
      if (list == null) {
         return null;
      }

      ItemStack taken;
      synchronized (list) {
         if (index < 0 || index >= list.size()) {
            return null;
         }

         taken = list.remove(index);
      }

      this.saveAsync();
      return taken;
   }

   /**
    * Upgrade kapasitas ke level berikut memakai economy existing. Validasi saldo, tarik
    * biaya, lalu naikkan level - semuanya di main thread (dipanggil dari klik GUI).
    */
   public UpgradeResult upgrade(Player player) {
      if (player == null || !this.loaded) {
         return UpgradeResult.UNAVAILABLE;
      }

      UUID id = player.getUniqueId();
      if (this.level(id) >= this.maxLevel()) {
         return UpgradeResult.MAX_LEVEL;
      }

      long cost = this.upgradeCost(id);
      if (cost < 0L) {
         return UpgradeResult.MAX_LEVEL;
      }

      if (this.plugin.economy() == null || !this.plugin.economy().isEnabled()) {
         return UpgradeResult.UNAVAILABLE;
      }

      if (this.plugin.economy().balance(player) < cost) {
         return UpgradeResult.NO_MONEY;
      }

      if (cost > 0L && !this.plugin.economy().withdraw(player, cost)) {
         return UpgradeResult.NO_MONEY;
      }

      this.levels.put(id, this.level(id) + 1);
      this.saveAsync();
      return UpgradeResult.SUCCESS;
   }

   public enum UpgradeResult {
      SUCCESS,
      NO_MONEY,
      MAX_LEVEL,
      UNAVAILABLE
   }

   // ------------------------------------------------------------------ //
   // Muat & simpan
   // ------------------------------------------------------------------ //

   /** Muat berkas (dipanggil sekali saat enable; reload TIDAK membuang data di memory). */
   public synchronized void load() {
      this.fish.clear();
      this.levels.clear();
      this.loaded = true;
      if (!this.file.isFile()) {
         return;
      }

      try {
         YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.file);
         ConfigurationSection root = yaml.getConfigurationSection("players");
         if (root == null) {
            return;
         }

         for (String key : root.getKeys(false)) {
            UUID uuid;
            try {
               uuid = UUID.fromString(key);
            } catch (IllegalArgumentException invalid) {
               continue;
            }

            int level = Math.max(1, root.getInt(key + ".level", 1));
            if (level > 1) {
               this.levels.put(uuid, level);
            }

            List<ItemStack> list = new ArrayList<>();
            for (String encoded : root.getStringList(key + ".fish")) {
               ItemStack stack = decode(encoded);
               if (stack != null) {
                  list.add(stack);
               }
            }

            if (!list.isEmpty()) {
               this.fish.put(uuid, list);
            }
         }
      } catch (Throwable throwable) {
         // Jangan pernah menimpa berkas yang gagal dibaca - matikan penulisan agar data asli
         // selamat, dan biarkan admin memperbaikinya.
         this.loaded = false;
         this.plugin.getLogger().severe("Fish inventory: failed to read " + FILE_NAME + " (" + throwable + "). Storage is read-only until the file is fixed.");
      }
   }

   public boolean isLoaded() {
      return this.loaded;
   }

   public boolean isDirty() {
      return this.version.get() > this.writtenVersion.get();
   }

   public File file() {
      return this.file;
   }

   /** Jumlah pemain yang punya data (debug /w2nsmp). */
   public int playerCount() {
      return Math.max(this.fish.size(), this.levels.size());
   }

   public void shutdown() {
      if (this.loaded && this.isDirty()) {
         this.write(this.version.get(), this.serialize());
      }
   }

   private void saveAsync() {
      this.version.incrementAndGet();
      if (!this.saveQueued) {
         this.saveQueued = true;
         Bukkit.getScheduler().runTask(this.plugin, () -> {
            this.saveQueued = false;
            long queued = this.version.get();
            String content = this.serialize();
            Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> this.write(queued, content));
         });
      }
   }

   private String serialize() {
      YamlConfiguration yaml = new YamlConfiguration();
      for (Map.Entry<UUID, Integer> entry : this.levels.entrySet()) {
         if (entry.getValue() != null && entry.getValue() > 1) {
            yaml.set("players." + entry.getKey() + ".level", entry.getValue());
         }
      }

      for (Map.Entry<UUID, List<ItemStack>> entry : this.fish.entrySet()) {
         List<ItemStack> list = entry.getValue();
         List<String> encoded = new ArrayList<>();
         synchronized (list) {
            for (ItemStack stack : list) {
               String text = encode(stack);
               if (text != null) {
                  encoded.add(text);
               }
            }
         }

         if (!encoded.isEmpty()) {
            yaml.set("players." + entry.getKey() + ".fish", encoded);
         }
      }

      return "# W2NSMP Fish Inventory (v1.8.0) - ikan custom per pemain, level kapasitas.\n"
         + "# Item disimpan sebagai Base64 dari serialisasi biner Paper (PDC ikut aman).\n"
         + yaml.saveToString();
   }

   private void write(long queued, String content) {
      synchronized (this.writeLock) {
         if (queued <= this.writtenVersion.get()) {
            return;
         }

         try {
            File parent = this.file.getParentFile();
            if (parent != null && !parent.isDirectory()) {
               parent.mkdirs();
            }

            File temp = new File(this.file.getPath() + ".tmp");
            java.nio.file.Files.writeString(temp.toPath(), content);
            java.nio.file.Files.move(temp.toPath(), this.file.toPath(),
               java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            this.writtenVersion.set(queued);
         } catch (Throwable throwable) {
            this.plugin.getLogger().warning("Fish inventory: failed to write " + FILE_NAME + " (" + throwable + ").");
         }
      }
   }

   private static String encode(ItemStack stack) {
      try {
         return Base64.getEncoder().encodeToString(stack.serializeAsBytes());
      } catch (Throwable throwable) {
         return null;
      }
   }

   private static ItemStack decode(String text) {
      try {
         return ItemStack.deserializeBytes(Base64.getDecoder().decode(text));
      } catch (Throwable throwable) {
         return null;
      }
   }
}
