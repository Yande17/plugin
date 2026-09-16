package me.w2n.w2nsmp.utility;

import java.util.List;
import org.bukkit.Bukkit;

public final class EnvironmentReport {
   private static final List<String> CANDIDATE_PLUGINS = List.of("Vault", "Essentials", "LuckPerms", "Geyser-Spigot", "Geyser", "floodgate", "Floodgate");

   private EnvironmentReport() {
   }

   public static String minecraftVersion() {
      return Bukkit.getMinecraftVersion();
   }

   public static String serverName() {
      return Bukkit.getName();
   }

   public static String apiTarget() {
      return "26.2";
   }

   public static boolean isPluginPresent(String pluginName) {
      return Bukkit.getPluginManager().getPlugin(pluginName) != null;
   }

   public static String firstPresent(String... candidateNames) {
      for (String candidate : candidateNames) {
         if (isPluginPresent(candidate)) {
            return candidate;
         }
      }

      return null;
   }

   public static String softDependencySummary() {
      StringBuilder builder = new StringBuilder("Soft dependencies: ");
      builder.append("Vault=").append(status("Vault")).append(", ");
      builder.append("EssentialsX=").append(status("Essentials")).append(", ");
      builder.append("LuckPerms=").append(status("LuckPerms")).append(", ");
      builder.append("Geyser=").append(status("Geyser-Spigot", "Geyser")).append(", ");
      builder.append("Floodgate=").append(status("floodgate", "Floodgate"));
      return builder.toString();
   }

   public static String status(String... candidateNames) {
      return firstPresent(candidateNames) == null ? "absent" : "present";
   }
}
