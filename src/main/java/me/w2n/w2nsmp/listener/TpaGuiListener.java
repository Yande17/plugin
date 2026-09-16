package me.w2n.w2nsmp.listener;

import java.util.List;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.gui.TpaMenu;
import me.w2n.w2nsmp.gui.TpaMenuHolder;
import me.w2n.w2nsmp.teleport.TpaRequest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public final class TpaGuiListener implements Listener {
   private final W2NSMP plugin;

   public TpaGuiListener(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onClick(InventoryClickEvent event) {
      Inventory top = event.getView().getTopInventory();
      if (top.getHolder() instanceof TpaMenuHolder holder) {
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
                  int incomingStart = TpaMenu.incomingStart(this.plugin);
                  int outgoingStart = TpaMenu.outgoingStart(this.plugin);
                  if (slot >= incomingStart && slot < incomingStart + 9) {
                     this.acceptEntry(player, slot - incomingStart);
                  } else if (slot >= outgoingStart && slot < outgoingStart + 9) {
                     this.cancelEntry(player, slot - outgoingStart);
                  } else if (slot == TpaMenu.slotAccept(this.plugin)) {
                     this.plugin.guiSounds().play(player, TpaMenu.gui(this.plugin), "click");
                     this.plugin.tpa().accept(player, null);
                     TpaMenu.refresh(this.plugin, player, top);
                  } else if (slot == TpaMenu.slotDeny(this.plugin)) {
                     this.plugin.guiSounds().play(player, TpaMenu.gui(this.plugin), "click");
                     this.plugin.tpa().deny(player, null);
                     TpaMenu.refresh(this.plugin, player, top);
                  } else if (slot == TpaMenu.slotCancel(this.plugin)) {
                     this.plugin.guiSounds().play(player, TpaMenu.gui(this.plugin), "click");
                     this.plugin.tpa().cancelOutgoing(player, null);
                     TpaMenu.refresh(this.plugin, player, top);
                  } else if (slot == TpaMenu.slotRefresh(this.plugin)) {
                     this.plugin.guiSounds().play(player, TpaMenu.gui(this.plugin), "click");
                     TpaMenu.refresh(this.plugin, player, top);
                  } else {
                     if (slot == TpaMenu.slotClose(this.plugin)) {
                        this.plugin.guiSounds().play(player, TpaMenu.gui(this.plugin), "click");
                        player.closeInventory();
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onDrag(InventoryDragEvent event) {
      if (event.getView().getTopInventory().getHolder() instanceof TpaMenuHolder) {
         event.setCancelled(true);
      }
   }

   private void acceptEntry(Player player, int index) {
      List<TpaRequest> incoming = this.plugin.tpa().incoming(player.getUniqueId());
      if (index < incoming.size()) {
         this.plugin.guiSounds().play(player, TpaMenu.gui(this.plugin), "click");
         this.plugin.tpa().accept(player, incoming.get(index).senderId());
         TpaMenu.refresh(this.plugin, player, player.getOpenInventory().getTopInventory());
      }
   }

   private void cancelEntry(Player player, int index) {
      List<TpaRequest> outgoing = this.plugin.tpa().outgoing(player.getUniqueId());
      if (index < outgoing.size()) {
         this.plugin.guiSounds().play(player, TpaMenu.gui(this.plugin), "click");
         this.plugin.tpa().cancelOutgoing(player, outgoing.get(index).targetId());
         TpaMenu.refresh(this.plugin, player, player.getOpenInventory().getTopInventory());
      }
   }
}
