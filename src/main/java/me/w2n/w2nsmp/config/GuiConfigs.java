package me.w2n.w2nsmp.config;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.w2n.w2nsmp.W2NSMP;

public final class GuiConfigs {
   public static final String COMMON = "common";
   private static final List<String> IDS = List.of("sell", "home", "auction", "top", "confirm", "profile", "settings", "skill", "tpa", "bounty", "common");
   private final W2NSMP plugin;
   private final Map<String, GuiConfig> configs = new LinkedHashMap<>();

   public GuiConfigs(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   public void load() {
      this.configs.clear();
      GuiConfig common = GuiConfig.load(this.plugin, "common");
      this.configs.put("common", common);

      for (String id : IDS) {
         if (!"common".equals(id)) {
            this.configs.put(id, GuiConfig.load(this.plugin, id).withCommon(common));
         }
      }
   }

   public void reload() {
      GuiConfig common = this.configs.get("common");
      if (common != null) {
         common.reload();
      }

      for (GuiConfig config : this.configs.values()) {
         config.reload();
      }
   }

   public boolean defaultsOnly() {
      return !this.plugin.config().guiConfigEnabled();
   }

   public GuiConfig of(String id) {
      if (this.defaultsOnly()) {
         return GuiConfig.defaultsOnly(this.plugin, id).withCommon(this.configs.get("common"));
      }

      GuiConfig config = this.configs.get(id);
      if (config != null) {
         return config;
      }

      GuiConfig fallback = GuiConfig.load(this.plugin, id).withCommon(this.configs.get("common"));
      this.configs.put(id, fallback);
      return fallback;
   }

   public GuiConfig sell() {
      return this.of("sell");
   }

   public GuiConfig home() {
      return this.of("home");
   }

   public GuiConfig auction() {
      return this.of("auction");
   }

   public GuiConfig top() {
      return this.of("top");
   }

   public GuiConfig confirm() {
      return this.of("confirm");
   }

   public GuiConfig profile() {
      return this.of("profile");
   }

   public GuiConfig settings() {
      return this.of("settings");
   }

   public GuiConfig skill() {
      return this.of("skill");
   }

   public GuiConfig tpa() {
      return this.of("tpa");
   }

   public GuiConfig bounty() {
      return this.of("bounty");
   }

   public GuiConfig common() {
      return this.of("common");
   }

   public File folder() {
      return new File(this.plugin.getDataFolder(), "gui");
   }

   public List<String> files() {
      List<String> names = new ArrayList<>();

      for (GuiConfig config : this.configs.values()) {
         names.add(config.file().getName() + (config.available() ? "" : " (bawaan)"));
      }

      return names;
   }
}
