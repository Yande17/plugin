package me.w2n.w2nsmp.listener;

import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.bounty.BountyService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public final class BountyListener implements Listener {
   private final W2NSMP plugin;

   public BountyListener(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onPlayerDeath(PlayerDeathEvent event) {
      BountyService bounty = this.plugin.bounty();
      if (bounty != null && bounty.enabled()) {
         Player victim = event.getEntity();
         Player killer = victim.getKiller();
         if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            bounty.claim(killer, victim);
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onJoin(PlayerJoinEvent event) {
      BountyService bounty = this.plugin.bounty();
      if (bounty != null) {
         bounty.rememberName(event.getPlayer());
      }
   }
}
