package me.w2n.w2nsmp.listener;

import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.gui.SettingsMenu;
import me.w2n.w2nsmp.gui.SettingsMenuHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public final class SettingsGuiListener implements Listener {
   private final W2NSMP plugin;

   public SettingsGuiListener(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onClick(InventoryClickEvent event) {
      Inventory top = event.getView().getTopInventory();
      if (top.getHolder() instanceof SettingsMenuHolder holder) {
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
                  String key = SettingsMenu.keyAt(this.plugin, event.getRawSlot());
                  if (key != null && !"info".equals(key)) {
                     if ("close".equals(key)) {
                        this.plugin.guiSounds().play(player, SettingsMenu.gui(this.plugin), "click");
                        player.closeInventory();
                     } else if (holder.beginProcessing()) {
                        holder.pending(key);

                        try {
                           if (SettingsMenu.locked(this.plugin, key, player)) {
                              this.plugin.messages().send(player, "setting.locked", "setting", SettingsMenu.label(this.plugin, key));
                              this.plugin.guiSounds().play(player, SettingsMenu.gui(this.plugin), "error");
                              return;
                           }

                           boolean changed = SettingsMenu.toggle(this.plugin, player, key);
                           if (changed) {
                              this.plugin.guiSounds().play(player, SettingsMenu.gui(this.plugin), "toggle");
                              SettingsMenu.render(this.plugin, top, player);
                              this.plugin.messages().send(player, "setting.toggled", "setting", SettingsMenu.label(this.plugin, key));
                              return;
                           }

                           this.plugin.messages().send(player, "setting.not-changed", "setting", SettingsMenu.label(this.plugin, key));
                           this.plugin.guiSounds().play(player, SettingsMenu.gui(this.plugin), "error");
                        } finally {
                           holder.pending(null);
                           holder.endProcessing();
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onDrag(InventoryDragEvent event) {
      if (event.getView().getTopInventory().getHolder() instanceof SettingsMenuHolder) {
         event.setCancelled(true);
      }
   }
}
