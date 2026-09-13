package me.w2n.w2nsmp.utility;

import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public final class Players {
   private Players() {
   }

   public static OfflinePlayer findOnlineOrCached(String name) {
      if (name != null && !name.isEmpty()) {
         Player online = Bukkit.getPlayerExact(name);
         return (OfflinePlayer)(online != null ? online : Bukkit.getOfflinePlayerIfCached(name));
      } else {
         return null;
      }
   }

   public static List<String> filter(List<String> options, String typed) {
      String prefix = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
      return options.stream().filter(option -> option != null && option.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
   }

   public static String displayName(OfflinePlayer player, String fallback) {
      if (player == null) {
         return fallback;
      }

      String name = player.getName();
      return name != null && !name.isEmpty() ? name : fallback;
   }
}
