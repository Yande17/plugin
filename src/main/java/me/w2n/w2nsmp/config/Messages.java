package me.w2n.w2nsmp.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.utility.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

public final class Messages {
   private static final String FILE_NAME = "messages.yml";
   private static final String DEFAULT_PREFIX = "&8[&bW2NSMP&8] &r";
   private final W2NSMP plugin;
   private YamlConfiguration file;
   private YamlConfiguration defaults;
   private String prefix;

   public Messages(W2NSMP plugin) {
      super();
      this.plugin = plugin;
      this.reload();
   }

   public void reload() {
      File target = new File(this.plugin.getDataFolder(), "messages.yml");
      if (!target.exists()) {
         this.plugin.saveResource("messages.yml", false);
      }

      this.file = YamlConfiguration.loadConfiguration(target);
      this.defaults = this.loadDefaults();
      this.prefix = this.file.getString("prefix", "&8[&bW2NSMP&8] &r");
   }

   private YamlConfiguration loadDefaults() {
      try (InputStream stream = this.plugin.getResource("messages.yml")) {
         return stream == null ? null : YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
      } catch (IOException exception) {
         this.plugin.getLogger().warning("Gagal membaca messages.yml bawaan: " + exception.getMessage());
         return null;
      }
   }

   public String prefix() {
      String value = this.prefix != null && !this.prefix.isEmpty() ? this.prefix : this.plugin.getConfig().getString("plugin.prefix");
      return value == null ? "&8[&bW2NSMP&8] &r" : value;
   }

   public Component component(String key, String... placeholders) {
      return Text.color(this.raw(key, placeholders));
   }

   public List<Component> components(List<String> lines, String... placeholders) {
      List<Component> result = new ArrayList<>(lines.size());

      for (String line : lines) {
         result.add(Text.color(this.substitute(line, placeholders)));
      }

      return result;
   }

   public void send(CommandSender sender, String key, String... placeholders) {
      sender.sendMessage(this.component(key, placeholders));
   }

   public String raw(String key, String... placeholders) {
      String value = this.value(key);
      return value == null ? "&cMissing message key: " + key : this.substitute(value, placeholders);
   }

   private String value(String key) {
      String fromFile = this.file.getString(key);
      if (fromFile != null) {
         return fromFile;
      } else {
         return this.defaults == null ? null : this.defaults.getString(key);
      }
   }

   public List<String> rawList(String key, String... placeholders) {
      List<String> lines = this.file.isList(key) ? this.file.getStringList(key) : (this.defaults == null ? List.of() : this.defaults.getStringList(key));
      List<String> result = new ArrayList<>(lines.size());

      for (String line : lines) {
         result.add(this.substitute(line, placeholders));
      }

      return result;
   }

   private String substitute(String value, String... placeholders) {
      String result = value.replace("%prefix%", this.prefix());

      for (int index = 0; index + 1 < placeholders.length; index += 2) {
         result = result.replace("%" + placeholders[index] + "%", placeholders[index + 1]);
      }

      return result;
   }

   public String apply(String text, String... placeholders) {
      return this.substitute(text == null ? "" : text, placeholders);
   }

   public List<String> applyList(List<String> lines, String... placeholders) {
      List<String> result = new ArrayList<>(lines.size());

      for (String line : lines) {
         result.add(this.substitute(line == null ? "" : line, placeholders));
      }

      return result;
   }

   public Component colored(String text) {
      return Text.color(text == null ? "" : text);
   }

   public boolean hasList(String key) {
      return this.file.isList(key) || this.defaults != null && this.defaults.isList(key);
   }

   public boolean has(String key) {
      return this.file.isString(key) || this.defaults != null && this.defaults.isString(key);
   }
}
