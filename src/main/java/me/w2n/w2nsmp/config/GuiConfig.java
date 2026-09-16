package me.w2n.w2nsmp.config;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import me.w2n.w2nsmp.W2NSMP;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class GuiConfig {
   private final W2NSMP plugin;
   private final String id;
   private final File file;
   private YamlConfiguration yaml = new YamlConfiguration();
   private GuiConfig common;
   private boolean valid;

   private GuiConfig(W2NSMP plugin, String id, File file) {
      super();
      this.plugin = plugin;
      this.id = id;
      this.file = file;
   }

   public GuiConfig withCommon(GuiConfig common) {
      this.common = common;
      return this;
   }

   private Object shared(String path) {
      if (this.yaml.get(path) != null) {
         return this.yaml.get(path);
      } else {
         return this.common != null && this.common != this ? this.common.shared(path) : null;
      }
   }

   public static GuiConfig load(W2NSMP plugin, String id) {
      File folder = new File(plugin.getDataFolder(), "gui");
      if (!folder.isDirectory() && !folder.mkdirs()) {
         plugin.getLogger().warning("Tidak bisa membuat folder gui/ untuk konfigurasi GUI.");
      }

      File file = new File(folder, id + ".yml");
      GuiConfig config = new GuiConfig(plugin, id, file);
      if (!file.isFile()) {
         try {
            plugin.saveResource("gui/" + id + ".yml", false);
         } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Berkas bawaan gui/" + id + ".yml tidak ada di jar; GUI " + id + " memakai nilai bawaan kode.");
         }
      }

      config.reload();
      return config;
   }

   static GuiConfig defaultsOnly(W2NSMP plugin, String id) {
      return new GuiConfig(plugin, id, new File(plugin.getDataFolder(), "gui/" + id + ".yml"));
   }

   public void reload() {
      this.yaml = new YamlConfiguration();
      this.valid = false;
      if (!this.file.isFile()) {
         try {
            this.plugin.saveResource("gui/" + this.id + ".yml", false);
         } catch (IllegalArgumentException exception) {
            this.plugin.getLogger().warning("Berkas bawaan gui/" + this.id + ".yml tidak ada di jar; GUI " + this.id + " memakai nilai bawaan kode.");
            return;
         }
      }

      if (this.file.isFile()) {
         try {
            this.yaml.load(this.file);
            this.valid = true;
         } catch (IOException | InvalidConfigurationException exception) {
            this.plugin
               .getLogger()
               .warning("gui/" + this.id + ".yml tidak bisa dibaca (" + exception.getMessage() + "); GUI " + this.id + " memakai nilai bawaan.");
         }
      }
   }

   public String id() {
      return this.id;
   }

   public File file() {
      return this.file;
   }

   public boolean available() {
      return this.valid;
   }

   public boolean enabled(boolean fallback) {
      return this.yaml.getBoolean("enabled", fallback);
   }

   public Component title(String messageKey, String... placeholders) {
      String override = this.yaml.getString("title");
      return override != null && !override.isBlank()
         ? this.plugin.messages().colored(this.plugin.messages().apply(override, placeholders))
         : this.plugin.messages().component(messageKey, placeholders);
   }

   public int size(int fallback) {
      return this.normalizeSize(this.yaml.getInt("size", fallback), fallback);
   }

   public int size(String path, int fallback) {
      return !this.yaml.isInt(path) && this.yaml.get(path) == null ? fallback : this.normalizeSize(this.yaml.getInt(path, fallback), fallback);
   }

   public int slot(String path, int fallback) {
      int value = this.yaml.getInt(path, fallback);
      if (!this.yaml.isInt(path) && this.shared(path) != null) {
         return clampSlot(this.yaml.getInt(path, this.common == null ? fallback : this.common.yaml.getInt(path, fallback)), fallback);
      }

      if (value >= 0 && value <= 53) {
         return value;
      }

      this.plugin.getLogger().warning("gui/" + this.id + ".yml: slot " + path + "=" + value + " di luar 0-53; memakai " + fallback + ".");
      return fallback;
   }

   private static int clampSlot(int value, int fallback) {
      return value >= 0 && value <= 53 ? value : fallback;
   }

   public Material material(String path, Material fallback) {
      Object raw = this.shared(path);
      String name = raw == null ? null : String.valueOf(raw);
      if (name != null && !name.isBlank()) {
         Material material = Material.matchMaterial(name.trim());
         if (material != null && material.isItem()) {
            return material;
         }

         this.plugin.getLogger().warning("gui/" + this.id + ".yml: material '" + name + "' pada " + path + " tidak valid; memakai " + fallback.name() + ".");
         return fallback;
      } else {
         return fallback;
      }
   }

   public String name(String path) {
      return this.name(path, null);
   }

   public String name(String path, String fallbackPath) {
      String value = this.yaml.getString(path);
      if (value != null && !value.isBlank()) {
         return value;
      }

      if (fallbackPath != null) {
         String legacy = this.yaml.getString(fallbackPath);
         if (legacy != null && !legacy.isBlank()) {
            return legacy;
         }
      }

      Object shared = this.shared(path);
      String text = shared == null ? null : String.valueOf(shared);
      return text != null && !text.isBlank() ? text : null;
   }

   public List<String> lore(String path) {
      return this.lore(path, null);
   }

   public List<String> lore(String path, String fallbackPath) {
      List<String> own = this.ownLore(path);
      if (!own.isEmpty()) {
         return own;
      }

      if (fallbackPath != null) {
         List<String> legacy = this.ownLore(fallbackPath);
         if (!legacy.isEmpty()) {
            return legacy;
         }
      }

      return this.common != null && this.common != this ? this.common.ownLore(path) : List.of();
   }

   private List<String> ownLore(String path) {
      if (this.yaml.isList(path)) {
         List<String> lines = new ArrayList<>();

         for (String line : this.yaml.getStringList(path)) {
            lines.add(line == null ? "" : line);
         }

         return lines;
      } else {
         String single = this.yaml.getString(path);
         return single != null && !single.isBlank() ? List.of(single) : List.of();
      }
   }

   public boolean fillerEnabled() {
      return this.yaml.getBoolean("filler.enabled", true);
   }

   public Material fillerMaterial(Material fallback) {
      return this.material("filler.material", fallback);
   }

   public String fillerName() {
      return this.yaml.getString("filler.name", " ");
   }

   public String sound(String action, String fallbackName) {
      String own = this.yaml.getString("sounds." + action);
      if (own != null && !own.isBlank()) {
         return own;
      }

      if (this.common != null && this.common != this) {
         String shared = this.common.yaml.getString("sounds." + action);
         if (shared != null && !shared.isBlank()) {
            return shared;
         }
      }

      return fallbackName;
   }

   public float soundVolume() {
      return (float)this.yaml.getDouble("sounds.volume", 1.0);
   }

   public float soundPitch() {
      return (float)this.yaml.getDouble("sounds.pitch", 1.0);
   }

   public String summary() {
      return this.id + (this.valid ? " (dari " + this.file.getName() + ")" : " (nilai bawaan)");
   }

   private int normalizeSize(int value, int fallback) {
      if (value >= 9 && value <= 54 && value % 9 == 0) {
         return value;
      }

      if (value > 0) {
         this.plugin.getLogger().warning("gui/" + this.id + ".yml: size=" + value + " tidak sah (harus kelipatan 9 antara 9-54); memakai " + fallback + ".");
      }

      return fallback;
   }
}
