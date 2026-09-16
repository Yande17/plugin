package me.w2n.w2nsmp.home;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class HomeStorage {
   public static final String FILE_NAME = "homes.yml";
   private final JavaPlugin plugin;
   private final File file;

   public HomeStorage(JavaPlugin plugin) {
      super();
      this.plugin = plugin;
      this.file = new File(plugin.getDataFolder(), "homes.yml");
   }

   public File file() {
      return this.file;
   }

   public Map<UUID, PlayerHomes> load() {
      Map<UUID, PlayerHomes> result = new HashMap<>();
      if (!this.file.isFile()) {
         return result;
      }

      YamlConfiguration yaml = new YamlConfiguration();

      try {
         yaml.load(this.file);
      } catch (IOException | InvalidConfigurationException exception) {
         this.plugin.getLogger().log(Level.WARNING, "Gagal membaca homes.yml (data home tidak dimuat, file TIDAK ditimpa): " + exception.getMessage());
         return result;
      }

      ConfigurationSection players = yaml.getConfigurationSection("players");
      if (players == null) {
         return result;
      }

      for (String rawUuid : players.getKeys(false)) {
         UUID uniqueId;
         try {
            uniqueId = UUID.fromString(rawUuid);
         } catch (IllegalArgumentException exception) {
            this.plugin.getLogger().warning("Lewati entri home dengan UUID tidak valid: " + rawUuid);
            continue;
         }

         ConfigurationSection section = players.getConfigurationSection(rawUuid);
         if (section != null) {
            PlayerHomes data = new PlayerHomes(uniqueId, section.getInt("unlocked", 0));
            data.lastName(section.getString("name"));
            ConfigurationSection homes = section.getConfigurationSection("homes");
            if (homes != null) {
               for (String rawSlot : homes.getKeys(false)) {
                  int slot;
                  try {
                     slot = Integer.parseInt(rawSlot);
                  } catch (NumberFormatException exception) {
                     continue;
                  }

                  ConfigurationSection home = homes.getConfigurationSection(rawSlot);
                  if (home != null) {
                     data.put(
                        slot,
                        new Home(
                           home.getString("name", "home" + (slot + 1)),
                           home.getString("world"),
                           home.getDouble("x"),
                           home.getDouble("y"),
                           home.getDouble("z"),
                           (float)home.getDouble("yaw"),
                           (float)home.getDouble("pitch")
                        )
                     );
                  }
               }
            }

            result.put(uniqueId, data);
         }
      }

      return result;
   }

   public String serialize(Map<UUID, PlayerHomes> data) {
      YamlConfiguration yaml = new YamlConfiguration();
      yaml.options()
         .setHeader(List.of("Data home W2NSMP - jangan diedit saat server berjalan.", "Kunci data adalah UUID pemain; nama pemain hanya keterangan."));

      for (PlayerHomes playerHomes : data.values()) {
         String base = "players." + playerHomes.uniqueId() + ".";
         yaml.set(base + "name", playerHomes.lastName());
         yaml.set(base + "unlocked", playerHomes.unlocked());

         for (Entry<Integer, Home> entry : playerHomes.homes().entrySet()) {
            Home home = entry.getValue();
            String path = base + "homes." + entry.getKey() + ".";
            yaml.set(path + "name", home.name());
            yaml.set(path + "world", home.world());
            yaml.set(path + "x", home.x());
            yaml.set(path + "y", home.y());
            yaml.set(path + "z", home.z());
            yaml.set(path + "yaw", (double)home.yaw());
            yaml.set(path + "pitch", (double)home.pitch());
         }
      }

      return yaml.saveToString();
   }

   public boolean write(String content) {
      try {
         if (!this.plugin.getDataFolder().isDirectory() && !this.plugin.getDataFolder().mkdirs()) {
            this.plugin.getLogger().warning("Tidak bisa membuat folder data plugin.");
            return false;
         } else {
            Files.writeString(this.file.toPath(), content, StandardCharsets.UTF_8);
            return true;
         }
      } catch (IOException exception) {
         this.plugin.getLogger().log(Level.WARNING, "Gagal menulis homes.yml: " + exception.getMessage());
         return false;
      }
   }
}
