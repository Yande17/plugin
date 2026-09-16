package me.w2n.w2nsmp.economy;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class DynamicPriceStorage {
   public static final String FILE_NAME = "dynamic.yml";
   private final JavaPlugin plugin;
   private final File file;
   private final AtomicLong version = new AtomicLong();
   private final AtomicLong writtenVersion = new AtomicLong();
   private final Object writeLock = new Object();
   private boolean saveQueued;
   private boolean loadFailed;
   private boolean warnedBroken;

   public DynamicPriceStorage(JavaPlugin plugin) {
      super();
      this.plugin = plugin;
      this.file = new File(plugin.getDataFolder(), "dynamic.yml");
   }

   public File file() {
      return this.file;
   }

   public boolean loadFailed() {
      return this.loadFailed;
   }

   public DynamicPriceStorage.Snapshot load() {
      this.loadFailed = false;
      this.warnedBroken = false;
      Map<String, Double> multipliers = new LinkedHashMap<>();
      if (!this.file.isFile()) {
         return new DynamicPriceStorage.Snapshot(multipliers, 0L);
      }

      YamlConfiguration yaml = new YamlConfiguration();

      try {
         yaml.load(this.file);
      } catch (IOException | InvalidConfigurationException exception) {
         this.loadFailed = true;
         this.plugin
            .getLogger()
            .warning("dynamic.yml tidak bisa dibaca (" + exception.getMessage() + "). Berkas TIDAK ditimpa; harga kembali ke harga dasar (multiplier 1.0).");
         return new DynamicPriceStorage.Snapshot(multipliers, 0L);
      }

      long updatedAt = yaml.getLong("updated", 0L);
      ConfigurationSection section = yaml.getConfigurationSection("multipliers");
      if (section != null) {
         for (String key : section.getKeys(false)) {
            double value = section.getDouble(key, 1.0);
            if (Double.isFinite(value) && value > 0.0) {
               multipliers.put(key, value);
            }
         }
      }

      return new DynamicPriceStorage.Snapshot(multipliers, updatedAt);
   }

   public void saveAsync(DynamicPriceStorage.Snapshot snapshot) {
      if (!this.saveQueued) {
         this.saveQueued = true;
         long queued = this.version.incrementAndGet();
         Bukkit.getScheduler().runTask(this.plugin, () -> {
            this.saveQueued = false;
            String content = this.serialize(snapshot);
            Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> this.write(queued, content));
         });
      }
   }

   public void saveNow(DynamicPriceStorage.Snapshot snapshot) {
      this.write(this.version.incrementAndGet(), this.serialize(snapshot));
   }

   public boolean isDirty() {
      return this.version.get() > this.writtenVersion.get();
   }

   private String serialize(DynamicPriceStorage.Snapshot snapshot) {
      StringBuilder builder = new StringBuilder();
      builder.append("# Pengali harga dinamis W2NSMP (PHASE 11).\n");
      builder.append("# 1.0 = harga dasar dari prices.yml. Nilai di sini dihitung otomatis dari\n");
      builder.append("# aktivitas jual-beli dan pemulihan berkala; aman dihapus untuk mengembalikan harga dasar.\n");
      builder.append("updated: ").append(snapshot.updatedAt()).append('\n');
      builder.append("multipliers:");
      if (snapshot.multipliers().isEmpty()) {
         builder.append(" {}\n");
      } else {
         builder.append('\n');

         for (Entry<String, Double> entry : snapshot.multipliers().entrySet()) {
            builder.append("  ").append(entry.getKey()).append(": ").append(String.format(Locale.ROOT, "%.6f", entry.getValue())).append('\n');
         }
      }

      return builder.toString();
   }

   private boolean write(long writeVersion, String content) {
      synchronized (this.writeLock) {
         if (this.loadFailed) {
            if (!this.warnedBroken) {
               this.warnedBroken = true;
               this.plugin
                  .getLogger()
                  .warning("dynamic.yml tidak ditulis karena berkas lama tidak bisa dibaca. Perbaiki/rename berkas itu, lalu /w2nsmp reload.");
            }

            return false;
         } else {
            if (writeVersion < this.writtenVersion.get()) {
               return true;
            }

            boolean var10000;
            try {
               if (!this.plugin.getDataFolder().isDirectory() && !this.plugin.getDataFolder().mkdirs()) {
                  this.plugin.getLogger().warning("Tidak bisa membuat folder data plugin.");
                  return false;
               }

               Files.writeString(this.file.toPath(), content, StandardCharsets.UTF_8);
               this.writtenVersion.set(writeVersion);
               var10000 = true;
            } catch (IOException exception) {
               this.plugin.getLogger().log(Level.WARNING, "Gagal menulis dynamic.yml: " + exception.getMessage());
               return false;
            }

            return var10000;
         }
      }
   }

   public record Snapshot(Map<String, Double> multipliers, long updatedAt) {
      public Snapshot {
      }
   }
}
