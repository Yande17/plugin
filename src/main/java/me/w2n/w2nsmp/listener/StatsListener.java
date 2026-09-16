package me.w2n.w2nsmp.listener;

import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.stats.StatType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class StatsListener implements Listener {
   private final W2NSMP plugin;

   public StatsListener(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onJoin(PlayerJoinEvent event) {
      if (this.plugin.stats() != null) {
         this.plugin.stats().onJoin(event.getPlayer());
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onQuit(PlayerQuitEvent event) {
      if (this.plugin.stats() != null) {
         this.plugin.stats().onQuit(event.getPlayer());
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onPlayerDeath(PlayerDeathEvent event) {
      if (this.plugin.stats() != null && event.getEntity().isOnline()) {
         Player victim = event.getEntity();
         this.plugin.stats().add(victim, StatType.DEATHS, 1L);
         Player killer = victim.getKiller();
         if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            this.plugin.stats().add(killer, StatType.KILLS, 1L);
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onEntityDeath(EntityDeathEvent event) {
      if (this.plugin.stats() != null && !(event.getEntity() instanceof Player)) {
         Player killer = event.getEntity().getKiller();
         if (killer != null) {
            this.plugin.stats().add(killer, StatType.MOBS, 1L);
         }
      }
   }
}
