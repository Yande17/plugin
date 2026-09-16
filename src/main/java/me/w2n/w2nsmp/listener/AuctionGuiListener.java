package me.w2n.w2nsmp.listener;

import java.util.UUID;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.auction.AuctionListing;
import me.w2n.w2nsmp.auction.AuctionManager;
import me.w2n.w2nsmp.gui.AuctionMenu;
import me.w2n.w2nsmp.gui.AuctionMenuHolder;
import me.w2n.w2nsmp.gui.ConfirmMenu;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public final class AuctionGuiListener implements Listener {
   private final W2NSMP plugin;

   public AuctionGuiListener(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         Inventory top = event.getView().getTopInventory();
         if (top.getHolder() instanceof AuctionMenuHolder holder) {
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
                  int slot = event.getRawSlot();
                  int size = top.getSize();
                  if (slot >= 0) {
                     if (top.equals(event.getClickedInventory())) {
                        if (slot == AuctionMenu.slotClose(this.plugin, size)) {
                           this.plugin.guiSounds().play(player, AuctionMenu.gui(this.plugin), "click");
                           player.closeInventory();
                        } else if (slot == AuctionMenu.slotPrev(this.plugin, size) && holder.page() > 1) {
                           this.reopen(player, holder.page() - 1, holder);
                        } else if (slot == AuctionMenu.slotNext(this.plugin, size)) {
                           this.reopen(player, holder.page() + 1, holder);
                        } else if (slot == AuctionMenu.slotCollect(this.plugin, size)) {
                           if (holder.beginProcessing()) {
                              try {
                                 this.collect(player, holder);
                              } finally {
                                 holder.endProcessing();
                              }
                           }
                        } else if (slot < holder.pageSize()) {
                           int listingId = holder.listingIdAt(slot);
                           if (listingId >= 0) {
                              if (holder.beginProcessing()) {
                                 try {
                                    AuctionListing listing = this.plugin.auction().listing(listingId);
                                    if (listing == null) {
                                       this.plugin.messages().send(player, "auction.not-found", "id", Integer.toString(listingId));
                                       this.reopen(player, holder.page(), holder);
                                       return;
                                    }

                                    if (listing.sellerId().equals(player.getUniqueId())) {
                                       this.openCancelConfirm(player, holder, listing);
                                    } else {
                                       this.openBuyConfirm(player, holder, listing);
                                    }
                                 } finally {
                                    holder.endProcessing();
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void collect(Player player, AuctionMenuHolder holder) {
      AuctionManager.CollectResult result = this.plugin.auction().collect(player);
      switch (result) {
         case NOTHING:
            this.plugin.messages().send(player, "auction.collect-none");
            break;
         case COLLECTED:
            this.plugin.messages().send(player, "auction.collect-success");
            break;
         case PARTIAL:
            this.plugin.messages().send(player, "auction.collect-full");
      }

      this.reopen(player, holder.page(), holder);
   }

   private void openBuyConfirm(Player player, AuctionMenuHolder holder, AuctionListing listing) {
      String[] placeholders = new String[]{
         "id",
         Integer.toString(listing.id()),
         "item",
         AuctionManager.itemName(listing.item()),
         "amount",
         Integer.toString(listing.item().getAmount()),
         "price",
         this.plugin.economy().format(listing.price()),
         "tax",
         this.plugin.economy().format(this.plugin.auction().taxFor(listing.price())),
         "net",
         this.plugin.economy().format(this.plugin.auction().netFor(listing.price())),
         "seller",
         listing.sellerName()
      };
      ConfirmMenu.open(
         this.plugin,
         player,
         "auction.buy",
         target -> this.buy(target, holder, listing.id(), placeholders),
         target -> this.reopen(target, holder.page(), holder),
         placeholders
      );
   }

   private void openCancelConfirm(Player player, AuctionMenuHolder holder, AuctionListing listing) {
      String[] placeholders = new String[]{
         "id",
         Integer.toString(listing.id()),
         "item",
         AuctionManager.itemName(listing.item()),
         "amount",
         Integer.toString(listing.item().getAmount()),
         "price",
         this.plugin.economy().format(listing.price())
      };
      ConfirmMenu.open(
         this.plugin,
         player,
         "auction.cancel",
         target -> this.cancel(target, holder, listing.id()),
         target -> this.reopen(target, holder.page(), holder),
         placeholders
      );
   }

   private void buy(Player player, AuctionMenuHolder holder, int id, String[] placeholders) {
      AuctionManager.BuyResult result = this.plugin.auction().buy(player, id);
      switch (result) {
         case SUCCESS:
         default:
            break;
         case NOT_FOUND:
            this.plugin.messages().send(player, "auction.not-found", "id", Integer.toString(id));
            break;
         case OWN_LISTING:
            this.plugin.messages().send(player, "auction.buy-own");
            break;
         case ECONOMY_DISABLED:
            this.plugin.messages().send(player, "economy.disabled");
            break;
         case INSUFFICIENT_FUNDS:
            this.plugin.messages().send(player, "auction.buy-insufficient", "price", placeholder(placeholders, "price"));
            break;
         case NO_SPACE:
            this.plugin.messages().send(player, "auction.buy-no-space");
            break;
         case BUSY:
            this.plugin.messages().send(player, "auction.busy");
            break;
         case WITHDRAW_FAILED:
         case DEPOSIT_FAILED:
            this.plugin.messages().send(player, "economy.transaction-failed");
            break;
         case SAVE_FAILED:
            this.plugin.messages().send(player, "auction.save-failed");
            break;
         case DISABLED:
            this.plugin.messages().send(player, "auction.disabled");
      }

      this.reopen(player, holder.page(), holder);
   }

   private void cancel(Player player, AuctionMenuHolder holder, int id) {
      AuctionManager.CancelResult result = this.plugin.auction().cancel(player, id);
      switch (result) {
         case SUCCESS:
         default:
            break;
         case NOT_FOUND:
            this.plugin.messages().send(player, "auction.not-found", "id", Integer.toString(id));
            break;
         case NOT_OWNER:
            this.plugin.messages().send(player, "auction.cancel-not-owner");
            break;
         case BUSY:
            this.plugin.messages().send(player, "auction.busy");
            break;
         case SAVE_FAILED:
            this.plugin.messages().send(player, "auction.save-failed");
      }

      this.reopen(player, holder.page(), holder);
   }

   private void reopen(Player player, int page, AuctionMenuHolder holder) {
      UUID filter = holder.sellerFilter();
      Bukkit.getScheduler().runTask(this.plugin, () -> {
         if (player.isOnline()) {
            AuctionMenu.open(this.plugin, player, Math.max(1, page), filter);
         }
      });
   }

   private static String placeholder(String[] placeholders, String key) {
      for (int index = 0; index + 1 < placeholders.length; index += 2) {
         if (placeholders[index].equals(key)) {
            return placeholders[index + 1];
         }
      }

      return "?";
   }

   @EventHandler
   public void onDrag(InventoryDragEvent event) {
      if (event.getView().getTopInventory().getHolder() instanceof AuctionMenuHolder) {
         event.setCancelled(true);
      }
   }
}
