package me.w2n.w2nsmp.hook;

import me.w2n.w2nsmp.W2NSMP;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class BedrockHook {
   private static final String GEYSER_SPIGOT = "Geyser-Spigot";
   private static final String GEYSER = "Geyser";
   private static final String FLOODGATE_LOWER = "floodgate";
   private static final String FLOODGATE = "Floodgate";
   private final W2NSMP plugin;
   private String geyserVersion;
   private String floodgateVersion;

   public BedrockHook(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   public void register() {
      this.geyserVersion = versionOf("Geyser-Spigot", "Geyser");
      this.floodgateVersion = versionOf("floodgate", "Floodgate");
      this.plugin.debug("Bedrock hook: Geyser=" + this.geyser() + ", Floodgate=" + this.floodgate());
   }

   public boolean hasGeyser() {
      return this.geyserVersion != null;
   }

   public boolean hasFloodgate() {
      return this.floodgateVersion != null;
   }

   public String geyser() {
      return this.geyserVersion;
   }

   public String floodgate() {
      return this.floodgateVersion;
   }

   public String summary() {
      return "Geyser=" + text(this.geyserVersion) + ", Floodgate=" + text(this.floodgateVersion);
   }

   public boolean bedrockReady() {
      return this.hasGeyser() && this.hasFloodgate();
   }

   public boolean javaOnly() {
      return !this.hasGeyser() && !this.hasFloodgate();
   }

   private static String text(String version) {
      return version == null ? "tidak terpasang" : version;
   }

   private static String versionOf(String... names) {
      for (String name : names) {
         Plugin found = Bukkit.getPluginManager().getPlugin(name);
         if (found != null) {
            try {
               return found.getPluginMeta().getVersion();
            } catch (RuntimeException exception) {
               return "terdeteksi";
            }
         }
      }

      return null;
   }
}
