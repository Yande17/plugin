package me.w2n.w2nsmp.listener;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import me.w2n.w2nsmp.W2NSMP;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class WorthListener implements Listener {
   private final W2NSMP plugin;
   private final Set<UUID> pending = new HashSet<>();

   public WorthListener(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onJoin(PlayerJoinEvent event) {
      this.schedule(event.getPlayer(), 20L);
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onPickup(EntityPickupItemEvent event) {
      if (event.getEntity() instanceof Player player) {
         this.schedule(player, 1L);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onInventoryClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         this.schedule(player, 1L);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onInventoryDrag(InventoryDragEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         this.schedule(player, 1L);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onHeldItemChange(PlayerItemHeldEvent event) {
      this.schedule(event.getPlayer(), 1L);
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onInventoryClose(InventoryCloseEvent event) {
      if (event.getPlayer() instanceof Player player) {
         this.schedule(player, 1L);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onQuit(PlayerQuitEvent event) {
      this.pending.remove(event.getPlayer().getUniqueId());
      this.plugin.worth().forget(event.getPlayer().getUniqueId());
   }

   private void schedule(Player player, long delay) {
      if (this.plugin.worth().enabled()) {
         UUID uniqueId = player.getUniqueId();
         if (this.pending.add(uniqueId)) {
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
               this.pending.remove(uniqueId);
               if (player.isOnline()) {
                  try {
                     this.plugin.worth().refresh(player);
                  } catch (RuntimeException exception) {
                     this.plugin.getLogger().warning("Gagal memperbarui lore harga " + player.getName() + ": " + exception);
                  }
               }
            }, Math.max(1L, delay));
         }
      }
   }
}
