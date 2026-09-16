package me.w2n.w2nsmp.listener;

import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.gui.ConfirmMenu;
import me.w2n.w2nsmp.gui.HomeMenu;
import me.w2n.w2nsmp.gui.HomeMenuHolder;
import me.w2n.w2nsmp.home.Home;
import me.w2n.w2nsmp.home.HomeManager;
import me.w2n.w2nsmp.home.HomeTeleportRequests;
import me.w2n.w2nsmp.home.PlayerHomes;
import me.w2n.w2nsmp.teleport.TeleportRequest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class HomeGuiListener implements Listener {
   private final W2NSMP plugin;

   public HomeGuiListener(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         Inventory top = event.getView().getTopInventory();
         if (top.getHolder() instanceof HomeMenuHolder holder) {
            event.setCancelled(true);
            if (holder.isOwner(player)) {
               ClickType click = event.getClick();
               if (!event.isShiftClick()
                  && !click.isKeyboardClick()
                  && !click.isCreativeAction()
                  && click != ClickType.DOUBLE_CLICK
                  && click != ClickType.SWAP_OFFHAND
                  && click != ClickType.DROP
                  && click != ClickType.CONTROL_DROP
                  && click != ClickType.MIDDLE
                  && event.getAction() != InventoryAction.COLLECT_TO_CURSOR) {
                  int index = HomeMenu.homeIndexOf(this.plugin, event.getRawSlot());
                  if (index >= 0 && top.equals(event.getClickedInventory())) {
                     if (holder.beginProcessing()) {
                        try {
                           this.plugin.guiSounds().play(player, HomeMenu.gui(this.plugin), "click");
                           if (click == ClickType.RIGHT) {
                              this.handleRightClick(player, index);
                           } else if (click == ClickType.LEFT) {
                              this.handleLeftClick(player, index);
                           }
                        } finally {
                           holder.endProcessing();
                        }
                     }
                  } else {
                     if (event.getRawSlot() == HomeMenu.slotClose(this.plugin)) {
                        this.plugin.guiSounds().play(player, HomeMenu.gui(this.plugin), "click");
                        player.closeInventory();
                     }
                  }
               }
            }
         }
      }
   }

   private void handleLeftClick(Player player, int index) {
      PlayerHomes data = this.plugin.homes().data(player);
      if (!data.isUnlocked(index)) {
         this.openPurchaseConfirm(player, index);
      } else {
         Home home = data.homeAt(index);
         if (home == null) {
            this.createHome(player, index);
         } else {
            player.closeInventory();
            TeleportRequest request = HomeTeleportRequests.create(this.plugin, player, home);
            if (request != null) {
               this.plugin.teleport().start(request);
            }
         }
      }
   }

   private void handleRightClick(Player player, int index) {
      Home home = this.plugin.homes().data(player).homeAt(index);
      if (home != null) {
         String[] placeholders = new String[]{
            "home", home.name(), "slot", Integer.toString(index + 1), "world", String.valueOf(home.world()), "coords", home.coordinateText()
         };
         if (!this.plugin.config().homeConfirmDelete()) {
            this.deleteHome(player, index, false);
         } else {
            ConfirmMenu.open(
               this.plugin, player, "home.delete", target -> this.deleteHome(target, index, true), target -> this.reopenLater(target), placeholders
            );
         }
      }
   }

   private void createHome(Player player, int index) {
      String name = this.plugin.homes().suggestName(player, index);
      HomeManager.SetHomeResult result = this.plugin.homes().setHomeAt(player, index, name, player.getLocation());
      switch (result) {
         case CREATED:
            this.plugin.messages().send(player, "home.set-success", "home", name, "slot", Integer.toString(index + 1));
            break;
         case OVERWRITTEN:
            this.plugin.messages().send(player, "home.set-overwritten", "home", name);
            break;
         case NO_FREE_SLOT:
            this.plugin
               .messages()
               .send(
                  player,
                  "home.set-no-slot",
                  "used",
                  Integer.toString(this.plugin.homes().data(player).used()),
                  "slots",
                  Integer.toString(this.plugin.homes().data(player).unlocked())
               );
            break;
         case INVALID_NAME:
            this.plugin.messages().send(player, "home.invalid-name");
      }

      this.reopenLater(player);
   }

   private void deleteHome(Player player, int index, boolean reopen) {
      Home removed = this.plugin.homes().deleteAt(player, index);
      if (removed == null) {
         this.plugin.messages().send(player, "home.delete-none");
      } else {
         this.plugin.messages().send(player, "home.delete-success", "home", removed.name());
      }

      if (reopen) {
         this.reopenLater(player);
      }
   }

   private void openPurchaseConfirm(Player player, int index) {
      if (!this.plugin.homes().isPurchasable(index)) {
         this.plugin.messages().send(player, "home.purchase-unavailable", "slot", Integer.toString(index + 1));
      } else {
         String[] placeholders = new String[]{"slot", Integer.toString(index + 1), "price", this.plugin.economy().format(this.plugin.homes().priceFor(index))};
         if (!this.plugin.config().homeConfirmPurchase()) {
            this.purchase(player, index);
         } else {
            ConfirmMenu.open(this.plugin, player, "home.purchase", target -> this.purchase(target, index), target -> this.reopenLater(target), placeholders);
         }
      }
   }

   private void purchase(Player player, int index) {
      HomeManager.PurchaseResult result = this.plugin.homes().purchase(player, index);
      String slot = Integer.toString(index + 1);
      String price = this.plugin.economy().format(this.plugin.homes().priceFor(index));
      switch (result) {
         case SUCCESS:
            this.plugin.messages().send(player, "home.purchase-success", "slot", slot, "price", price);
            break;
         case ALREADY_UNLOCKED:
            this.plugin.messages().send(player, "home.purchase-already", "slot", slot);
            break;
         case INSUFFICIENT_FUNDS:
            this.plugin.messages().send(player, "home.purchase-insufficient", "slot", slot, "price", price);
            break;
         case ECONOMY_DISABLED:
            this.plugin.messages().send(player, "economy.disabled");
            break;
         case NOT_PURCHASABLE:
            this.plugin.messages().send(player, "home.purchase-unavailable", "slot", slot);
            break;
         case FAILED:
            this.plugin.messages().send(player, "economy.transaction-failed");
      }

      this.reopenLater(player);
   }

   private void reopenLater(Player player) {
      Bukkit.getScheduler().runTask(this.plugin, () -> {
         if (player.isOnline()) {
            HomeMenu.open(this.plugin, player);
         }
      });
   }

   @EventHandler
   public void onDrag(InventoryDragEvent event) {
      if (event.getView().getTopInventory().getHolder() instanceof HomeMenuHolder) {
         event.setCancelled(true);
      }
   }

   @EventHandler
   public void onClose(InventoryCloseEvent event) {
      Inventory top = event.getView().getTopInventory();
      if (top.getHolder() instanceof HomeMenuHolder holder) {
         holder.endProcessing();

         for (int var6 = 0; var6 < top.getSize(); var6++) {
            ItemStack stack = top.getItem(var6);
            if (stack != null) {
               top.setItem(var6, null);
            }
         }
      }
   }
}
