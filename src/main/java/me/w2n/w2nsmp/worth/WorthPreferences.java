package me.w2n.w2nsmp.worth;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class WorthPreferences {
   public static final String FILE_NAME = "worth.yml";
   private final JavaPlugin plugin;
   private final File file;
   private final Set<UUID> disabled = new HashSet<>();
   private final AtomicLong version = new AtomicLong();
   private final AtomicLong writtenVersion = new AtomicLong();
   private final Object writeLock = new Object();
   private boolean saveQueued;

   public WorthPreferences(JavaPlugin plugin) {
      super();
      this.plugin = plugin;
      this.file = new File(plugin.getDataFolder(), "worth.yml");
   }

   public File file() {
      return this.file;
   }

   public void load() {
      Set<UUID> loaded = new HashSet<>();
      if (this.file.isFile()) {
         YamlConfiguration yaml = new YamlConfiguration();

         try {
            yaml.load(this.file);

            for (String raw : yaml.getStringList("disabled")) {
               if (raw != null && !raw.isBlank()) {
                  try {
                     loaded.add(UUID.fromString(raw.trim()));
                  } catch (IllegalArgumentException exception) {
                     this.plugin.getLogger().warning("worth.yml memuat UUID tidak valid: " + raw);
                  }
               }
            }
         } catch (IOException | InvalidConfigurationException exception) {
            this.plugin
               .getLogger()
               .warning("worth.yml tidak bisa dibaca (" + exception.getMessage() + "). File TIDAK ditimpa; pemain dianggap memakai pengaturan bawaan.");
            return;
         }
      }

      this.disabled.clear();
      this.disabled.addAll(loaded);
   }

   public boolean isDisabled(UUID uniqueId) {
      return uniqueId != null && this.disabled.contains(uniqueId);
   }

   public boolean setDisabled(UUID uniqueId, boolean value) {
      if (uniqueId == null) {
         return false;
      }

      boolean changed = value ? this.disabled.add(uniqueId) : this.disabled.remove(uniqueId);
      if (changed) {
         this.saveAsync();
      }

      return changed;
   }

   public int disabledCount() {
      return this.disabled.size();
   }

   public Set<UUID> disabledPlayers() {
      return Collections.unmodifiableSet(new HashSet<>(this.disabled));
   }

   public void saveAsync() {
      if (!this.saveQueued) {
         this.saveQueued = true;
         long queued = this.version.incrementAndGet();
         Bukkit.getScheduler().runTask(this.plugin, () -> {
            this.saveQueued = false;
            String content = this.serialize();
            Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> this.write(queued, content));
         });
      }
   }

   public void saveNow() {
      this.write(this.version.incrementAndGet(), this.serialize());
   }

   public boolean isDirty() {
      return this.version.get() > this.writtenVersion.get();
   }

   private String serialize() {
      StringBuilder builder = new StringBuilder();
      builder.append("# Data preferensi fitur worth W2NSMP - jangan diedit saat server berjalan.\n");
      builder.append("# Pemain pada daftar 'disabled' tidak diberi lore harga pada itemnya.\n");
      builder.append("# Kunci data adalah UUID pemain; urutan tidak berpengaruh.\n\n");
      builder.append("disabled:\n");
      if (this.disabled.isEmpty()) {
         builder.append("  []            # belum ada pemain yang mematikan lore harga\n");
         return builder.toString();
      }

      for (UUID uniqueId : this.disabled.stream().sorted().toList()) {
         builder.append("  - ").append(uniqueId).append('\n');
      }

      return builder.toString();
   }

   private boolean write(long writeVersion, String content) {
      synchronized (this.writeLock) {
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
            this.plugin.getLogger().log(Level.WARNING, "Gagal menulis worth.yml: " + exception.getMessage());
            return false;
         }

         return var10000;
      }
   }
}
