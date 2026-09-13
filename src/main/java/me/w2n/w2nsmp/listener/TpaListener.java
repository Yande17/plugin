package me.w2n.w2nsmp.listener;

import me.w2n.w2nsmp.W2NSMP;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class TpaListener implements Listener {
   private final W2NSMP plugin;

   public TpaListener(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onQuit(PlayerQuitEvent event) {
      if (this.plugin.tpa() != null) {
         this.plugin.tpa().cancelFor(event.getPlayer());
      }
   }
}
