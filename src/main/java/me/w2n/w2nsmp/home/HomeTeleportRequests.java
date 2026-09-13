package me.w2n.w2nsmp.home;

import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.stats.StatType;
import me.w2n.w2nsmp.teleport.TeleportRequest;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class HomeTeleportRequests {
   private HomeTeleportRequests() {
   }

   public static TeleportRequest create(W2NSMP plugin, Player player, Home home) {
      Location target = home.location();
      if (target == null) {
         plugin.messages().send(player, "home.world-missing", "home", home.name(), "world", String.valueOf(home.world()));
         return null;
      } else {
         return TeleportRequest.builder(player, target, "home")
            .placeholder("home", home.name())
            .placeholder("world", String.valueOf(home.world()))
            .placeholder("coords", home.coordinateText())
            .delaySeconds(plugin.config().homeTeleportDelay())
            .actionBar(plugin.config().homeTeleportActionBar())
            .cancelOnMove(plugin.config().homeCancelOnMove())
            .cancelOnDamage(plugin.config().homeCancelOnDamage())
            .cancelOnDeath(plugin.config().homeCancelOnDeath())
            .blockedInCombat(plugin.config().combatBlockTeleport())
            .statType(StatType.HOME_TELEPORTS)
            .build();
      }
   }
}
