package me.w2n.w2nsmp.listener;

import java.util.function.Consumer;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.gui.ConfirmMenu;
import me.w2n.w2nsmp.gui.ConfirmMenuHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public final class ConfirmGuiListener implements Listener {
   private final W2NSMP plugin;

   public ConfirmGuiListener(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         Inventory top = event.getView().getTopInventory();
         if (top.getHolder() instanceof ConfirmMenuHolder holder) {
            event.setCancelled(true);
            if (holder.isOwner(player)) {
               if (!event.isShiftClick() && event.getClick() != ClickType.DOUBLE_CLICK && event.getClick() != ClickType.CREATIVE) {
                  int slot = event.getRawSlot();
                  if (slot == ConfirmMenu.slotConfirm(this.plugin)) {
                     this.run(player, holder, holder.onConfirm());
                  } else if (slot == ConfirmMenu.slotCancel(this.plugin)) {
                     this.run(player, holder, holder.onCancel());
                  }
               }
            }
         }
      }
   }

   private void run(Player player, ConfirmMenuHolder holder, Consumer<Player> action) {
      if (action != null && holder.beginProcessing()) {
         player.closeInventory();
         action.accept(player);
      }
   }

   @EventHandler
   public void onDrag(InventoryDragEvent event) {
      if (event.getView().getTopInventory().getHolder() instanceof ConfirmMenuHolder) {
         event.setCancelled(true);
      }
   }
}
