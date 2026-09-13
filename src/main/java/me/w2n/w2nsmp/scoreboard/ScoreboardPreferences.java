package me.w2n.w2nsmp.scoreboard;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ScoreboardPreferences {
   public static final String FILE_NAME = "scoreboard.yml";
   private final JavaPlugin plugin;
   private final File file;
   private final Set<UUID> hidden = new HashSet<>();
   private final Map<UUID, Set<String>> hiddenLines = new HashMap<>();
   private final AtomicLong version = new AtomicLong();
   private final AtomicLong writtenVersion = new AtomicLong();
   private final Object writeLock = new Object();
   private boolean saveQueued;

   public ScoreboardPreferences(JavaPlugin plugin) {
      super();
      this.plugin = plugin;
      this.file = new File(plugin.getDataFolder(), "scoreboard.yml");
   }

   public File file() {
      return this.file;
   }

   public void load() {
      Set<UUID> loaded = new HashSet<>();
      Map<UUID, Set<String>> loadedLines = new LinkedHashMap<>();
      if (this.file.isFile()) {
         YamlConfiguration yaml = new YamlConfiguration();

         try {
            yaml.load(this.file);

            for (String raw : yaml.getStringList("hidden")) {
               if (raw != null && !raw.isBlank()) {
                  try {
                     loaded.add(UUID.fromString(raw.trim()));
                  } catch (IllegalArgumentException exception) {
                     this.plugin.getLogger().warning("scoreboard.yml memuat UUID tidak valid: " + raw);
                  }
               }
            }

            ConfigurationSection lineSection = yaml.getConfigurationSection("hidden-lines");
            if (lineSection != null) {
               Iterator var15 = lineSection.getKeys(false).iterator();

               label58:
               while (true) {
                  String rawId;
                  UUID uniqueId;
                  while (true) {
                     if (!var15.hasNext()) {
                        break label58;
                     }

                     rawId = (String)var15.next();

                     try {
                        uniqueId = UUID.fromString(rawId.trim());
                        break;
                     } catch (IllegalArgumentException exception) {
                        this.plugin.getLogger().warning("scoreboard.yml memuat UUID tidak valid pada hidden-lines: " + rawId);
                     }
                  }

                  Set<String> keys = new HashSet<>();

                  for (String line : lineSection.getStringList(rawId)) {
                     if (line != null && !line.isBlank()) {
                        keys.add(line.trim().toLowerCase(Locale.ROOT));
                     }
                  }

                  if (!keys.isEmpty()) {
                     loadedLines.put(uniqueId, keys);
                  }
               }
            }
         } catch (IOException | InvalidConfigurationException exception) {
            this.plugin
               .getLogger()
               .warning("scoreboard.yml tidak bisa dibaca (" + exception.getMessage() + "). File TIDAK ditimpa; pemain dianggap memakai pengaturan bawaan.");
            return;
         }
      }

      this.hidden.clear();
      this.hidden.addAll(loaded);
      this.hiddenLines.clear();
      this.hiddenLines.putAll(loadedLines);
   }

   public boolean isHidden(UUID uniqueId) {
      return uniqueId != null && this.hidden.contains(uniqueId);
   }

   public boolean setHidden(UUID uniqueId, boolean value) {
      if (uniqueId == null) {
         return false;
      }

      boolean changed = value ? this.hidden.add(uniqueId) : this.hidden.remove(uniqueId);
      if (changed) {
         this.saveAsync();
      }

      return changed;
   }

   public boolean isLineHidden(UUID uniqueId, String key) {
      if (uniqueId != null && key != null) {
         Set<String> keys = this.hiddenLines.get(uniqueId);
         return keys != null && keys.contains(key.toLowerCase(Locale.ROOT));
      } else {
         return false;
      }
   }

   public boolean setLineHidden(UUID uniqueId, String key, boolean value) {
      if (uniqueId != null && key != null && !key.isBlank()) {
         String normalized = key.toLowerCase(Locale.ROOT);
         Set<String> keys = this.hiddenLines.computeIfAbsent(uniqueId, id -> new HashSet<>());
         boolean changed = value ? keys.add(normalized) : keys.remove(normalized);
         if (keys.isEmpty()) {
            this.hiddenLines.remove(uniqueId);
         }

         if (changed) {
            this.saveAsync();
         }

         return changed;
      } else {
         return false;
      }
   }

   public Set<String> hiddenLines(UUID uniqueId) {
      Set<String> keys = uniqueId == null ? null : this.hiddenLines.get(uniqueId);
      return keys == null ? Set.of() : Collections.unmodifiableSet(new HashSet<>(keys));
   }

   public int hiddenLinePlayers() {
      return this.hiddenLines.size();
   }

   public int hiddenCount() {
      return this.hidden.size();
   }

   public Set<UUID> hiddenPlayers() {
      return Collections.unmodifiableSet(new HashSet<>(this.hidden));
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
      builder.append("# Preferensi sidebar statistik W2NSMP (PHASE 9) - jangan diedit saat server berjalan.\n");
      builder.append("# Pemain pada daftar 'hidden' tidak melihat sidebar sampai memakai /sb on.\n");
      builder.append("# Kunci data adalah UUID pemain; urutan tidak berpengaruh.\n\n");
      builder.append("# Pemain pada 'hidden-lines' tetap melihat sidebar, hanya baris tertentu disembunyikan.\n\n");
      builder.append("hidden:\n");
      if (this.hidden.isEmpty()) {
         builder.append("  []            # belum ada pemain yang mematikan sidebar\n");
      } else {
         for (UUID uniqueId : this.hidden.stream().sorted().toList()) {
            builder.append("  - ").append(uniqueId).append('\n');
         }
      }

      builder.append("\nhidden-lines:\n");
      if (this.hiddenLines.isEmpty()) {
         builder.append("  {}            # belum ada pemain yang menyembunyikan baris tertentu\n");
         return builder.toString();
      }

      for (Entry<UUID, Set<String>> entry : this.hiddenLines.entrySet().stream().sorted(Entry.comparingByKey()).toList()) {
         builder.append("  ").append(entry.getKey()).append(':').append('\n');

         for (String key : entry.getValue().stream().sorted().toList()) {
            builder.append("    - ").append(key).append('\n');
         }
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
            this.plugin.getLogger().log(Level.WARNING, "Gagal menulis scoreboard.yml: " + exception.getMessage());
            return false;
         }

         return var10000;
      }
   }
}
