package me.w2n.w2nsmp.listener;

import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.teleport.TeleportRequest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class TeleportListener implements Listener {
   private final W2NSMP plugin;

   public TeleportListener(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   @EventHandler(ignoreCancelled = true)
   public void onMove(PlayerMoveEvent event) {
      Player player = event.getPlayer();
      TeleportRequest request = this.plugin.teleport().pendingRequest(player);
      if (request != null && request.cancelOnMove()) {
         if (this.plugin.teleport().movedTooFar(player, event.getTo())) {
            this.plugin.teleport().cancel(player, "move");
         }
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onDamage(EntityDamageEvent event) {
      if (event.getEntity() instanceof Player player) {
         TeleportRequest request = this.plugin.teleport().pendingRequest(player);
         if (request != null && request.cancelOnDamage() && !(event.getFinalDamage() <= 0.0)) {
            this.plugin.teleport().cancel(player, "damage");
         }
      }
   }

   @EventHandler
   public void onDeath(PlayerDeathEvent event) {
      TeleportRequest request = this.plugin.teleport().pendingRequest(event.getEntity());
      if (request != null && request.cancelOnDeath()) {
         this.plugin.teleport().cancel(event.getEntity(), "death");
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.plugin.teleport().cancel(event.getPlayer(), null);
   }
}
