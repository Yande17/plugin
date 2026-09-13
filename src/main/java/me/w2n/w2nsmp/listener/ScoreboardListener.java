package me.w2n.w2nsmp.listener;

import me.w2n.w2nsmp.W2NSMP;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ScoreboardListener implements Listener {
   private final W2NSMP plugin;

   public ScoreboardListener(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onJoin(PlayerJoinEvent event) {
      if (this.plugin.scoreboard() != null && this.plugin.scoreboard().enabled()) {
         Player player = event.getPlayer();
         Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (player.isOnline()) {
               if (this.plugin.scoreboard() != null) {
                  this.plugin.scoreboard().apply(player);
               }

               if (this.plugin.nametag() != null && this.plugin.nametag().enabled()) {
                  this.plugin.nametag().apply(player);
               }
            }
         });
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onQuit(PlayerQuitEvent event) {
      if (this.plugin.scoreboard() != null) {
         this.plugin.scoreboard().remove(event.getPlayer());
      }

      if (this.plugin.nametag() != null) {
         this.plugin.nametag().remove(event.getPlayer());
      }
   }
}
