package me.w2n.w2nsmp.listener;

import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.fishing.AutoFishService;
import me.w2n.w2nsmp.gui.AutoFishMenu;
import me.w2n.w2nsmp.gui.AutoFishMenuHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

/**
 * Listener /autofishing (v1.4.1): klik GUI + pembersihan sesi (quit, mati, pindah dunia).
 */
public final class AutoFishGuiListener implements Listener {
   private final W2NSMP plugin;

   public AutoFishGuiListener(W2NSMP plugin) {
      this.plugin = plugin;
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onClick(InventoryClickEvent event) {
      Inventory top = event.getView().getTopInventory();
      if (!(top.getHolder() instanceof AutoFishMenuHolder holder)) {
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

      String key = AutoFishMenu.keyAt(this.plugin, event.getRawSlot());
      if (key == null || !holder.beginProcessing()) {
         return;
      }

      holder.pending(key);

      try {
         AutoFishService auto = this.plugin.autoFish();

         if ("close".equals(key)) {
            this.plugin.guiSounds().play(player, AutoFishMenu.gui(this.plugin), "click");
            player.closeInventory();
            return;
         }

         if (auto == null) {
            return;
         }

         if ("toggle".equals(key)) {
            if (auto.isActive(player)) {
               auto.stop(player, "fishing.autofish.stopped");
               this.plugin.guiSounds().play(player, AutoFishMenu.gui(this.plugin), "click");
            } else {
               String blocked = auto.start(player);
               if (blocked != null) {
                  this.plugin.messages().send(player, blocked);
                  this.plugin.guiSounds().play(player, AutoFishMenu.gui(this.plugin), "error");
               } else {
                  this.plugin.messages().send(player, "fishing.autofish.started",
                     "interval", Integer.toString(auto.intervalSeconds()));
                  this.plugin.guiSounds().play(player, AutoFishMenu.gui(this.plugin), "success");
               }
            }

            AutoFishMenu.render(this.plugin, top, player);
            return;
         }

         if ("auto-sell".equals(key)) {
            if (!auto.allowAutoSell()) {
               this.plugin.messages().send(player, "fishing.autofish.sell-not-allowed");
               this.plugin.guiSounds().play(player, AutoFishMenu.gui(this.plugin), "error");
               return;
            }

            AutoFishService.Session session = auto.session(player);
            if (session == null) {
               this.plugin.messages().send(player, "fishing.autofish.sell-need-active");
               this.plugin.guiSounds().play(player, AutoFishMenu.gui(this.plugin), "error");
               return;
            }

            session.autoSell(!session.autoSell());
            this.plugin.guiSounds().play(player, AutoFishMenu.gui(this.plugin), "click");
            AutoFishMenu.render(this.plugin, top, player);
         }
      } catch (Throwable throwable) {
         this.plugin.debug("Autofish: gangguan klik GUI (" + throwable + ").");
      } finally {
         holder.pending(null);
         holder.endProcessing();
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onDrag(InventoryDragEvent event) {
      if (event.getView().getTopInventory().getHolder() instanceof AutoFishMenuHolder) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onClose(InventoryCloseEvent event) {
      try {
         AutoFishMenu.markClosed(event.getPlayer(), event.getInventory());
      } catch (Throwable ignored) {
      }
   }

   /** Keluar server: sesi autofishing DIHENTIKAN (tidak berjalan untuk pemain offline). */
   @EventHandler(priority = EventPriority.MONITOR)
   public void onQuit(PlayerQuitEvent event) {
      try {
         AutoFishMenu.markClosed(event.getPlayer(), null);
         if (this.plugin.autoFish() != null) {
            this.plugin.autoFish().stopQuietly(event.getPlayer().getUniqueId());
         }
      } catch (Throwable ignored) {
      }
   }

   /** Mati: hentikan sesi + beri tahu setelah respawn tidak perlu - cukup pesan berhenti. */
   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onDeath(PlayerDeathEvent event) {
      try {
         AutoFishService auto = this.plugin.autoFish();
         if (auto != null && event.getEntity() instanceof Player player && auto.isActive(player)) {
            auto.stop(player, "fishing.autofish.stopped-death");
         }
      } catch (Throwable ignored) {
      }
   }

   /** Pindah dunia: syarat lingkungan berubah total - hentikan sesi. */
   @EventHandler(priority = EventPriority.MONITOR)
   public void onWorldChange(PlayerChangedWorldEvent event) {
      try {
         AutoFishService auto = this.plugin.autoFish();
         if (auto != null && auto.isActive(event.getPlayer())) {
            auto.stop(event.getPlayer(), "fishing.autofish.stopped-world");
         }
      } catch (Throwable ignored) {
      }
   }
}
