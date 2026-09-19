package me.w2n.w2nsmp.listener;

import me.w2n.w2nsmp.W2NSMP;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Night Vision /setting (v1.5.1): pulihkan efek saat join dan sesudah respawn.
 *
 * <p>Keduanya ditunda 1 tick supaya pemain sudah benar-benar berada di dunia sebelum efek
 * dipasang (respawn menghapus semua efek vanilla). Teleport & relog tidak butuh handler
 * khusus: teleport tidak menghapus efek, dan relog tertangani lewat join.
 */
public final class NightVisionListener implements Listener {
   private final W2NSMP plugin;

   public NightVisionListener(W2NSMP plugin) {
      this.plugin = plugin;
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onJoin(PlayerJoinEvent event) {
      this.applyLater(event.getPlayer());
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onRespawn(PlayerRespawnEvent event) {
      this.applyLater(event.getPlayer());
   }

   private void applyLater(Player player) {
      if (player == null || this.plugin.nightVision() == null) {
         return;
      }

      Bukkit.getScheduler().runTask(this.plugin, () -> {
         try {
            if (player.isOnline() && this.plugin.nightVision() != null) {
               this.plugin.nightVision().onJoin(player);
            }
         } catch (Throwable throwable) {
            this.plugin.debug("NightVision: gangguan pemulihan efek (" + throwable + ").");
         }
      });
   }
}
