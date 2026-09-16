package me.w2n.w2nsmp.player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class SettingsStorage {
   public static final String FILE_NAME = "settings.yml";
   private final JavaPlugin plugin;
   private final File file;
   private final AtomicLong version = new AtomicLong();
   private final AtomicLong writtenVersion = new AtomicLong();
   private final Object writeLock = new Object();
   private boolean saveQueued;
   private boolean loadFailed;
   private boolean warnedBroken;

   public SettingsStorage(JavaPlugin plugin) {
      super();
      this.plugin = plugin;
      this.file = new File(plugin.getDataFolder(), "settings.yml");
   }

   public File file() {
      return this.file;
   }

   public Map<UUID, PlayerSettings> load() {
      this.loadFailed = false;
      this.warnedBroken = false;
      Map<UUID, PlayerSettings> loaded = new LinkedHashMap<>();
      if (!this.file.isFile()) {
         return loaded;
      }

      YamlConfiguration yaml = new YamlConfiguration();

      try {
         yaml.load(this.file);
      } catch (IOException | InvalidConfigurationException exception) {
         this.loadFailed = true;
         this.plugin
            .getLogger()
            .warning(
               "settings.yml tidak bisa dibaca ("
                  + exception.getMessage()
                  + "). File TIDAK ditimpa; pemain memakai pengaturan bawaan. Perbaiki atau pindahkan berkas itu (mis. rename) lalu jalankan /w2nsmp reload."
            );
         return loaded;
      }

      for (String rawId : yaml.getKeys(false)) {
         UUID uniqueId;
         try {
            uniqueId = UUID.fromString(rawId.trim());
         } catch (IllegalArgumentException exception) {
            this.plugin.getLogger().warning("settings.yml memuat UUID tidak valid: " + rawId);
            continue;
         }

         ConfigurationSection section = yaml.getConfigurationSection(rawId);
         if (section != null) {
            PlayerSettings settings = new PlayerSettings(uniqueId);

            for (String key : section.getKeys(false)) {
               settings.put(key, section.getBoolean(key));
            }

            if (!settings.isEmpty()) {
               loaded.put(uniqueId, settings);
            }
         }
      }

      return loaded;
   }

   public void saveAsync(Map<UUID, PlayerSettings> data) {
      if (!this.saveQueued) {
         this.saveQueued = true;
         long queued = this.version.incrementAndGet();
         Bukkit.getScheduler().runTask(this.plugin, () -> {
            this.saveQueued = false;
            String content = this.serialize(data);
            Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> this.write(queued, content));
         });
      }
   }

   public void saveNow(Map<UUID, PlayerSettings> data) {
      this.write(this.version.incrementAndGet(), this.serialize(data));
   }

   public boolean isDirty() {
      return this.version.get() > this.writtenVersion.get();
   }

   public boolean loadFailed() {
      return this.loadFailed;
   }

   private String serialize(Map<UUID, PlayerSettings> data) {
      StringBuilder builder = new StringBuilder();
      builder.append("# Pengaturan pribadi pemain W2NSMP (PHASE 11) - diatur lewat /setting.\n");
      builder.append("# Kunci data adalah UUID pemain. Pilihan yang kosong berarti pemain memakai\n");
      builder.append("# nilai bawaan dari config.yml, jadi file ini tetap kecil.\n");
      builder.append("# Sidebar (/sb + baris mana yang tampil) disimpan di scoreboard.yml,\n");
      builder.append("# lore harga item disimpan di worth.yml - bukan di sini.\n\n");
      if (data.isEmpty()) {
         builder.append("{}\n");
         return builder.toString();
      }

      for (Entry<UUID, PlayerSettings> entry : data.entrySet().stream().sorted(Entry.comparingByKey()).toList()) {
         if (!entry.getValue().isEmpty()) {
            builder.append(entry.getKey()).append(":\n");

            for (Entry<String, Boolean> value : entry.getValue().values().entrySet()) {
               builder.append("  ").append(value.getKey()).append(": ").append(value.getValue()).append('\n');
            }
         }
      }

      return builder.toString();
   }

   private boolean write(long writeVersion, String content) {
      synchronized (this.writeLock) {
         if (this.loadFailed) {
            if (this.warnedBroken) {
               return false;
            }

            this.warnedBroken = true;
            this.plugin
               .getLogger()
               .warning("settings.yml tidak ditulis karena berkas lama tidak bisa dibaca. Perbaiki/rename berkas itu, lalu /w2nsmp reload.");
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
               this.plugin.getLogger().log(Level.WARNING, "Gagal menulis settings.yml: " + exception.getMessage());
               return false;
            }

            return var10000;
         }
      }
   }
}
