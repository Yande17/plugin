package me.w2n.w2nsmp.listener;

import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.gui.LeaderboardMenu;
import me.w2n.w2nsmp.gui.ProfileMenu;
import me.w2n.w2nsmp.gui.ProfileMenuHolder;
import me.w2n.w2nsmp.gui.SettingsMenu;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

public final class ProfileGuiListener implements Listener {
   private final W2NSMP plugin;

   public ProfileGuiListener(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onClick(InventoryClickEvent event) {
      Inventory top = event.getView().getTopInventory();
      if (top.getHolder() instanceof ProfileMenuHolder holder) {
         event.setCancelled(true);
         if (event.getWhoClicked() instanceof Player player && holder.isOwner(player)) {
            if (event.getClickedInventory() != null && event.getClickedInventory().equals(top)) {
               if (!event.isShiftClick()
                  && !event.isRightClick()
                  && !event.getClick().isKeyboardClick()
                  && !event.getClick().isCreativeAction()
                  && event.getClick() != ClickType.DOUBLE_CLICK
                  && event.getClick() != ClickType.SWAP_OFFHAND
                  && event.getClick() != ClickType.DROP
                  && event.getClick() != ClickType.CONTROL_DROP
                  && event.getAction() != InventoryAction.COLLECT_TO_CURSOR) {
                  int slot = event.getRawSlot();
                  if (slot == ProfileMenu.slot(this.plugin, "close", 53)) {
                     this.plugin.guiSounds().play(player, ProfileMenu.gui(this.plugin), "click");
                     player.closeInventory();
                  } else if (slot == ProfileMenu.slot(this.plugin, "settings", 49)) {
                     if (holder.subject() != null && holder.subject().equals(player.getUniqueId())) {
                        this.plugin.guiSounds().play(player, ProfileMenu.gui(this.plugin), "click");
                        SettingsMenu.open(this.plugin, player);
                     } else {
                        this.plugin.messages().send(player, "profile.self-only");
                        this.plugin.guiSounds().play(player, ProfileMenu.gui(this.plugin), "error");
                     }
                  } else {
                     if (slot == ProfileMenu.slot(this.plugin, "top", 51)) {
                        this.plugin.guiSounds().play(player, ProfileMenu.gui(this.plugin), "click");
                        LeaderboardMenu.openCategories(this.plugin, player, 1);
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onDrag(InventoryDragEvent event) {
      if (event.getView().getTopInventory().getHolder() instanceof ProfileMenuHolder) {
         event.setCancelled(true);
      }
   }

   public void refresh(Player player) {
      if (player != null) {
         InventoryView view = player.getOpenInventory();
         if (view != null && view.getTopInventory().getHolder() instanceof ProfileMenuHolder holder) {
            Player subject = holder.subject() == null ? player : Bukkit.getPlayer(holder.subject());
            if (subject != null) {
               ProfileMenu.render(this.plugin, view.getTopInventory(), subject);
            }
         }
      }
   }
}
