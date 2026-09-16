package me.w2n.w2nsmp.listener;

import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.gui.FishMenu;
import me.w2n.w2nsmp.gui.FishMenuHolder;
import me.w2n.w2nsmp.gui.RodMenu;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

/**
 * Listener GUI /fish (v1.4.1): hub dan galeri ikan.
 *
 * <p>Semua klik di menu dibatalkan (murni tampilan); hanya klik kiri bersih pemilik yang
 * memicu aksi. Pola guard sama dengan GUI lain (holder + beginProcessing + pending).
 */
public final class FishGuiListener implements Listener {
   private final W2NSMP plugin;

   public FishGuiListener(W2NSMP plugin) {
      this.plugin = plugin;
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onClick(InventoryClickEvent event) {
      Inventory top = event.getView().getTopInventory();
      if (!(top.getHolder() instanceof FishMenuHolder holder)) {
         return;
      }

      event.setCancelled(true);
      if (!(event.getWhoClicked() instanceof Player player) || !holder.isOwner(player)) {
         return;
      }

      if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) {
         return;
      }

      if (event.isShiftClick()
         || event.isRightClick()
         || event.getClick().isKeyboardClick()
         || event.getClick().isCreativeAction()
         || event.getClick() == ClickType.DOUBLE_CLICK
         || event.getClick() == ClickType.SWAP_OFFHAND
         || event.getClick() == ClickType.DROP
         || event.getClick() == ClickType.CONTROL_DROP
         || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
         return;
      }

      String key = FishMenu.keyAt(this.plugin, holder, event.getRawSlot());
      if (key == null || !holder.beginProcessing()) {
         return;
      }

      holder.pending(key);

      try {
         this.plugin.guiSounds().play(player, FishMenu.gui(this.plugin), "click");

         if ("close".equals(key)) {
            player.closeInventory();
            return;
         }

         if ("gallery".equals(key)) {
            // Ukuran hub (27) berbeda dengan galeri (54), jadi buka inventory baru.
            player.closeInventory();
            Bukkit.getScheduler().runTask(this.plugin, () -> {
               if (player.isOnline()) {
                  FishMenu.open(this.plugin, player, FishMenuHolder.MODE_GALLERY, 0);
               }
            });
            return;
         }

         if ("rod".equals(key)) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(this.plugin, () -> {
               if (player.isOnline()) {
                  RodMenu.open(this.plugin, player);
               }
            });
            return;
         }

         if ("back".equals(key)) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(this.plugin, () -> {
               if (player.isOnline()) {
                  FishMenu.open(this.plugin, player, FishMenuHolder.MODE_HUB, 0);
               }
            });
            return;
         }

         if ("prev".equals(key) || "next".equals(key)) {
            int delta = "prev".equals(key) ? -1 : 1;
            holder.page(Math.max(0, holder.page() + delta));
            FishMenu.render(this.plugin, top, player, holder);
         }
      } catch (Throwable throwable) {
         this.plugin.debug("Fishing: gangguan klik GUI /fish (" + throwable + ").");
      } finally {
         holder.pending(null);
         holder.endProcessing();
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onDrag(InventoryDragEvent event) {
      if (event.getView().getTopInventory().getHolder() instanceof FishMenuHolder) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onClose(InventoryCloseEvent event) {
      try {
         FishMenu.markClosed(event.getPlayer(), event.getInventory());
      } catch (Throwable throwable) {
         this.plugin.debug("Fishing: gangguan tutup GUI /fish (" + throwable + ").");
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onQuit(PlayerQuitEvent event) {
      try {
         FishMenu.markClosed(event.getPlayer(), null);
      } catch (Throwable throwable) {
         this.plugin.debug("Fishing: gangguan keluar GUI /fish (" + throwable + ").");
      }
   }
}
