package me.w2n.w2nsmp.fishing;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Catatan ikan custom yang PERNAH ditangkap tiap pemain (v1.4.1, untuk Fish Gallery /fish).
 *
 * <p>Disimpan di {@code fishing-discovery.yml} sebagai {@code uuid: [id, id, ...]}. Penemuan
 * baru adalah kejadian langka (paling banyak satu kali per ikan per pemain), jadi berkas
 * ditulis langsung saat ada penemuan - tidak butuh scheduler dan tidak ada data hilang saat
 * server mati mendadak. Id ikan yang sudah tidak ada di config TETAP dipertahankan di berkas
 * (tidak menghapus data lama) tetapi tidak dihitung di galeri.
 */
public final class FishDiscovery {
   public static final String FILE_NAME = "fishing-discovery.yml";
   private final JavaPlugin plugin;
   private final File file;
   private final Map<UUID, Set<String>> discovered = new ConcurrentHashMap<>();
   private volatile boolean loaded;

   public FishDiscovery(JavaPlugin plugin) {
      this.plugin = plugin;
      this.file = new File(plugin.getDataFolder(), FILE_NAME);
   }

   /** Muat berkas (idempotent; dipanggil dari reload FishingService). */
   public synchronized void load() {
      this.discovered.clear();
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

            Set<String> ids = ConcurrentHashMap.newKeySet();
            for (String id : root.getStringList(key)) {
               if (id != null && !id.isEmpty()) {
                  ids.add(id);
               }
            }

            if (!ids.isEmpty()) {
               this.discovered.put(uuid, ids);
            }
         }
      } catch (Throwable throwable) {
         this.plugin.getLogger().warning("Fishing: gagal membaca " + FILE_NAME + " (" + throwable + "); galeri mulai kosong.");
      }
   }

   /** Ikan yang pernah ditangkap pemain (set hanya-baca; kosong bila belum ada). */
   public Set<String> discovered(UUID uuid) {
      if (uuid == null) {
         return Set.of();
      }

      Set<String> ids = this.discovered.get(uuid);
      return ids == null ? Set.of() : Collections.unmodifiableSet(ids);
   }

   public boolean isDiscovered(UUID uuid, String fishId) {
      if (uuid == null || fishId == null) {
         return false;
      }

      Set<String> ids = this.discovered.get(uuid);
      return ids != null && ids.contains(fishId);
   }

   /**
    * Catat penemuan. Mengembalikan {@code true} hanya bila ini penemuan BARU
    * (pemanggil bisa memakai ini untuk pesan "ikan baru ditemukan!").
    */
   public boolean discover(UUID uuid, String fishId) {
      if (uuid == null || fishId == null || fishId.isEmpty()) {
         return false;
      }

      if (!this.loaded) {
         this.load();
      }

      Set<String> ids = this.discovered.computeIfAbsent(uuid, ignored -> ConcurrentHashMap.newKeySet());
      if (!ids.add(fishId)) {
         return false;
      }

      this.save();
      return true;
   }

   /** Jumlah penemuan pemain yang masih ada di registry (id usang tidak dihitung). */
   public int countIn(UUID uuid, Set<String> knownIds) {
      int count = 0;

      for (String id : this.discovered(uuid)) {
         if (knownIds.contains(id)) {
            count++;
         }
      }

      return count;
   }

   /** Tulis berkas secara sinkron (kejadian langka, berkas kecil). */
   private synchronized void save() {
      try {
         YamlConfiguration yaml = new YamlConfiguration();

         for (Map.Entry<UUID, Set<String>> entry : this.discovered.entrySet()) {
            yaml.set("players." + entry.getKey(), List.copyOf(new LinkedHashSet<>(entry.getValue())));
         }

         File parent = this.file.getParentFile();
         if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            this.plugin.getLogger().warning("Fishing: tidak bisa membuat folder data untuk " + FILE_NAME + ".");
            return;
         }

         yaml.save(this.file);
      } catch (Throwable throwable) {
         this.plugin.getLogger().warning("Fishing: gagal menyimpan " + FILE_NAME + " (" + throwable + ").");
      }
   }
}
