package me.w2n.w2nsmp.stats;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class StatsStorage {
   public static final String YAML_FILE_NAME = "statistics.yml";
   public static final String SQLITE_FILE_NAME = "w2nsmp.db";
   public static final int SCHEMA_VERSION = 1;
   private final JavaPlugin plugin;

   public StatsStorage(JavaPlugin plugin) {
      super();
      this.plugin = plugin;
   }

   public File yamlFile() {
      return new File(this.plugin.getDataFolder(), "statistics.yml");
   }

   public File sqliteFile() {
      return new File(this.plugin.getDataFolder(), "w2nsmp.db");
   }

   public File fileFor(StatsStorage.Mode mode) {
      return mode == StatsStorage.Mode.SQLITE ? this.sqliteFile() : this.yamlFile();
   }

   public boolean sqliteDriverAvailable() {
      try {
         return Class.forName("org.sqlite.JDBC") != null;
      } catch (ClassNotFoundException | LinkageError exception) {
         return false;
      }
   }

   public Map<UUID, PlayerStats> loadYaml() {
      Map<UUID, PlayerStats> result = new HashMap<>();
      File file = this.yamlFile();
      if (!file.isFile()) {
         return result;
      }

      YamlConfiguration yaml = new YamlConfiguration();

      try {
         yaml.load(file);
      } catch (IOException | InvalidConfigurationException exception) {
         this.plugin.getLogger().log(Level.WARNING, "Gagal membaca statistics.yml: " + exception.getMessage());
         return result;
      }

      ConfigurationSection players = yaml.getConfigurationSection("players");
      if (players == null) {
         return result;
      }

      for (String key : players.getKeys(false)) {
         UUID uniqueId = this.parseUuid(key);
         if (uniqueId == null) {
            this.plugin.getLogger().warning("Lewati entri statistik dengan UUID tidak valid: " + key);
         } else {
            ConfigurationSection section = players.getConfigurationSection(key);
            if (section != null) {
               PlayerStats stats = new PlayerStats(uniqueId);
               stats.name(section.getString("name"));
               stats.firstSeen(section.getLong("first-seen", 0L));
               stats.lastSeen(section.getLong("last-seen", 0L));
               this.readValues(stats, section.getConfigurationSection("stats"));
               result.put(uniqueId, stats);
            }
         }
      }

      return result;
   }

   private void readValues(PlayerStats stats, ConfigurationSection section) {
      if (section != null) {
         for (StatType type : StatType.values()) {
            if (section.isSet(type.key())) {
               stats.value(type, section.getLong(type.key(), 0L));
            }
         }
      }
   }

   public String serializeYaml(Map<UUID, PlayerStats> data) {
      YamlConfiguration yaml = new YamlConfiguration();
      yaml.options()
         .setHeader(
            List.of(
               "Data statistik W2NSMP (PHASE 9) - jangan diedit saat server berjalan.",
               "Kunci data adalah UUID pemain; nama pemain hanya keterangan.",
               "statistics.storage di config.yml menentukan apakah file ini dipakai (YAML) atau w2nsmp.db (SQLITE)."
            )
         );

      for (PlayerStats stats : data.values()) {
         if (!stats.isEmpty() || !stats.values().isEmpty()) {
            String base = "players." + stats.uniqueId() + ".";
            yaml.set(base + "name", stats.name());
            yaml.set(base + "first-seen", stats.firstSeen());
            yaml.set(base + "last-seen", stats.lastSeen());

            for (Entry<StatType, Long> entry : stats.values().entrySet()) {
               if (entry.getValue() != null && entry.getValue() > 0L) {
                  yaml.set(base + "stats." + entry.getKey().key(), entry.getValue());
               }
            }
         }
      }

      return yaml.saveToString();
   }

   public boolean writeYaml(StatsStorage.Mode mode, String content) {
      File file = this.fileFor(mode);

      try {
         File folder = this.plugin.getDataFolder();
         if (!folder.isDirectory() && !folder.mkdirs()) {
            this.plugin.getLogger().warning("Tidak bisa membuat folder data plugin.");
            return false;
         } else {
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
            return true;
         }
      } catch (IOException exception) {
         this.plugin.getLogger().log(Level.WARNING, "Gagal menulis " + file.getName() + ": " + exception.getMessage());
         return false;
      }
   }

   public String jdbcUrl() {
      return "jdbc:sqlite:" + this.sqliteFile().getAbsolutePath();
   }

   public String driverClass() {
      return "org.sqlite.JDBC";
   }

   private UUID parseUuid(String raw) {
      try {
         return UUID.fromString(raw.trim());
      } catch (IllegalArgumentException exception) {
         return null;
      }
   }

   public enum Mode {
      YAML,
      SQLITE;

      public static StatsStorage.Mode fromConfig(String raw) {
         if (raw != null && !raw.isBlank()) {
            String wanted = raw.trim().toUpperCase(Locale.ROOT).replace("SQLITE3", "SQLITE");

            for (StatsStorage.Mode mode : values()) {
               if (mode.name().equals(wanted)) {
                  return mode;
               }
            }

            return null;
         } else {
            return null;
         }
      }
   }
}
