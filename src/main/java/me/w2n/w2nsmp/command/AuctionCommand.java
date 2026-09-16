package me.w2n.w2nsmp.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.auction.AuctionListing;
import me.w2n.w2nsmp.auction.AuctionManager;
import me.w2n.w2nsmp.economy.MoneyFormatter;
import me.w2n.w2nsmp.gui.AuctionMenu;
import me.w2n.w2nsmp.utility.Players;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class AuctionCommand implements TabExecutor {
   private final W2NSMP plugin;

   public AuctionCommand(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!this.plugin.config().auctionEnabled()) {
         this.plugin.messages().send(sender, "auction.disabled");
         return true;
      }

      if (sender instanceof Player player) {
         if (args.length == 0) {
            AuctionMenu.open(this.plugin, player, 1, null);
            return true;
         }

         switch (args[0].toLowerCase(Locale.ROOT)) {
            case "sell":
            case "jual":
               this.sell(player, label, args);
               break;
            case "cancel":
            case "batal":
               this.cancel(player, label, args);
               break;
            case "collect":
            case "ambil":
               this.collect(player);
               break;
            case "list":
            case "mine":
               this.listOwn(player);
               break;
            case "help":
            case "bantuan":
               this.sendHelp(player, label);
               break;
            default:
               this.openFiltered(player, args[0]);
         }

         return true;
      } else {
         this.plugin.messages().send(sender, "auction.player-only");
         return true;
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (args.length <= 1) {
         String typed = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
         List<String> options = new ArrayList<>(List.of("sell", "cancel", "collect", "list", "help"));
         if (sender instanceof Player player) {
            for (AuctionListing listing : this.plugin.auction().listingsOf(player.getUniqueId())) {
               options.add(Integer.toString(listing.id()));
            }
         }

         return options.stream().filter(option -> option.startsWith(typed)).sorted().toList();
      } else if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
         return List.of("<harga>");
      } else {
         return args.length == 2 && args[0].equalsIgnoreCase("cancel") && sender instanceof Player player
            ? this.plugin
               .auction()
               .listingsOf(player.getUniqueId())
               .stream()
               .map(listingx -> Integer.toString(listingx.id()))
               .filter(id -> id.startsWith(args[1]))
               .toList()
            : List.of();
      }
   }

   private void sell(Player player, String label, String[] args) {
      if (args.length != 2) {
         this.plugin
            .messages()
            .send(
               player,
               "auction.sell-usage",
               "usage",
               "/" + label + " sell <harga>",
               "min",
               Long.toString(this.plugin.config().auctionMinPrice()),
               "max",
               Long.toString(this.plugin.config().auctionMaxPrice())
            );
      } else {
         long price = MoneyFormatter.parseLongAmount(args[1]);
         if (price < 0L) {
            this.plugin.messages().send(player, "auction.price-invalid", "input", args[1]);
         } else {
            AuctionManager.ListResult result = this.plugin.auction().list(player, price);
            switch (result) {
               case SUCCESS:
               default:
                  break;
               case DISABLED:
                  this.plugin.messages().send(player, "auction.disabled");
                  break;
               case ECONOMY_DISABLED:
                  this.plugin.messages().send(player, "economy.disabled");
                  break;
               case NO_ITEM_IN_HAND:
                  this.plugin.messages().send(player, "auction.no-item");
                  break;
               case BLACKLISTED:
                  this.plugin.messages().send(player, "auction.blacklisted");
                  break;
               case PRICE_TOO_LOW:
                  this.plugin.messages().send(player, "auction.price-too-low", "min", Long.toString(this.plugin.config().auctionMinPrice()));
                  break;
               case PRICE_TOO_HIGH:
                  this.plugin.messages().send(player, "auction.price-too-high", "max", Long.toString(this.plugin.config().auctionMaxPrice()));
                  break;
               case LIMIT_REACHED:
                  this.plugin.messages().send(player, "auction.limit-reached", "limit", Integer.toString(this.plugin.auction().maxListings(player)));
                  break;
               case FEE_FAILED:
                  this.plugin.messages().send(player, "auction.fee-failed", "fee", this.plugin.economy().format(this.plugin.config().auctionListingFee()));
                  break;
               case SAVE_FAILED:
                  this.plugin.messages().send(player, "auction.save-failed");
            }
         }
      }
   }

   private void cancel(Player player, String label, String[] args) {
      if (args.length != 2) {
         this.plugin.messages().send(player, "auction.cancel-usage", "usage", "/" + label + " cancel <id>");
      } else {
         int id;
         try {
            id = Integer.parseInt(args[1]);
         } catch (NumberFormatException exception) {
            this.plugin.messages().send(player, "auction.cancel-usage", "usage", "/" + label + " cancel <id>");
            return;
         }

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
      }
   }

   private void collect(Player player) {
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
   }

   private void listOwn(Player player) {
      List<AuctionListing> listings = this.plugin.auction().listingsOf(player.getUniqueId());
      if (listings.isEmpty()) {
         this.plugin.messages().send(player, "auction.list-empty");
      } else {
         long now = System.currentTimeMillis();
         this.plugin
            .messages()
            .send(
               player, "auction.list-header", "count", Integer.toString(listings.size()), "limit", Integer.toString(this.plugin.auction().maxListings(player))
            );

         for (AuctionListing listing : listings) {
            this.plugin
               .messages()
               .send(
                  player,
                  "auction.list-entry",
                  "id",
                  Integer.toString(listing.id()),
                  "item",
                  AuctionManager.itemName(listing.item()),
                  "amount",
                  Integer.toString(listing.item().getAmount()),
                  "price",
                  this.plugin.economy().format(listing.price()),
                  "net",
                  this.plugin.economy().format(this.plugin.auction().netFor(listing.price())),
                  "time",
                  AuctionMenu.remainingText(listing.remainingMillis(now))
               );
         }

         int mailbox = this.plugin.auction().mailboxCount(player.getUniqueId());
         if (mailbox > 0) {
            this.plugin.messages().send(player, "auction.list-mailbox", "amount", Integer.toString(mailbox));
         }
      }
   }

   private void openFiltered(Player player, String name) {
      OfflinePlayer target = Players.findOnlineOrCached(name);
      if (target == null) {
         this.plugin.messages().send(player, "auction.player-not-found", "player", name);
      } else {
         List<AuctionListing> listings = this.plugin.auction().listingsOf(target.getUniqueId());
         if (listings.isEmpty()) {
            this.plugin.messages().send(player, "auction.filter-empty", "player", Players.displayName(target, name));
         } else {
            AuctionMenu.open(this.plugin, player, 1, target.getUniqueId());
         }
      }
   }

   private void sendHelp(Player player, String label) {
      this.plugin.messages().send(player, "auction.help-header");
      this.plugin.messages().send(player, "auction.help-open", "label", label);
      this.plugin
         .messages()
         .send(
            player,
            "auction.help-sell",
            "label",
            label,
            "min",
            Long.toString(this.plugin.config().auctionMinPrice()),
            "max",
            Long.toString(this.plugin.config().auctionMaxPrice())
         );
      this.plugin.messages().send(player, "auction.help-cancel", "label", label);
      this.plugin.messages().send(player, "auction.help-collect", "label", label);
      this.plugin.messages().send(player, "auction.help-list", "label", label);
      this.plugin
         .messages()
         .send(
            player,
            "auction.help-tax",
            "tax",
            Integer.toString(this.plugin.config().auctionTaxPercent()),
            "hours",
            Integer.toString(this.plugin.config().auctionExpirationHours())
         );
   }
}
