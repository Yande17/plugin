package me.w2n.w2nsmp.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.gui.ConfirmMenu;
import me.w2n.w2nsmp.gui.HomeMenu;
import me.w2n.w2nsmp.home.Home;
import me.w2n.w2nsmp.home.HomeManager;
import me.w2n.w2nsmp.home.HomeTeleportRequests;
import me.w2n.w2nsmp.teleport.TeleportRequest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class HomeCommand implements TabExecutor {
   private static final String PERMISSION = "w2nsmp.home";
   private final W2NSMP plugin;

   public HomeCommand(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("w2nsmp.home")) {
            this.plugin.messages().send(player, "command.no-permission");
            return true;
         }

         if (args.length == 0) {
            HomeMenu.open(this.plugin, player);
            return true;
         }

         String sub = args[0].toLowerCase(Locale.ROOT);
         switch (sub) {
            case "help":
               this.sendUsage(player, label);
               break;
            case "list":
               this.sendList(player);
               break;
            case "buy":
               this.handleBuy(player, args);
               break;
            default:
               this.handleTeleport(player, args[0]);
         }

         return true;
      } else {
         this.plugin.messages().send(sender, "command.player-only");
         return true;
      }
   }

   private void sendList(Player player) {
      Map<Integer, Home> homes = this.plugin.homes().homes(player.getUniqueId());
      if (homes.isEmpty()) {
         this.plugin.messages().send(player, "home.list-empty");
      } else {
         this.plugin.messages().send(player, "home.list-header");

         for (Entry<Integer, Home> entry : homes.entrySet()) {
            this.plugin
               .messages()
               .send(
                  player,
                  "home.list-entry",
                  "slot",
                  Integer.toString(entry.getKey() + 1),
                  "home",
                  entry.getValue().name(),
                  "coords",
                  entry.getValue().coordinateText()
               );
         }
      }
   }

   private void handleBuy(Player player, String[] args) {
      if (args.length < 2) {
         this.plugin.messages().send(player, "home.usage-buy", "max", Integer.toString(this.plugin.homes().maxSlots()));
      } else {
         int slotNumber;
         try {
            slotNumber = Integer.parseInt(args[1]);
         } catch (NumberFormatException exception) {
            this.plugin.messages().send(player, "home.usage-buy", "max", Integer.toString(this.plugin.homes().maxSlots()));
            return;
         }

         int index = slotNumber - 1;
         if (index >= 0 && index < this.plugin.homes().maxSlots()) {
            if (this.plugin.homes().isUnlocked(player.getUniqueId(), index)) {
               this.plugin.messages().send(player, "home.purchase-already", "slot", Integer.toString(slotNumber));
            } else if (!this.plugin.homes().isPurchasable(index)) {
               this.plugin.messages().send(player, "home.purchase-unavailable", "slot", Integer.toString(slotNumber));
            } else {
               String[] placeholders = new String[]{
                  "slot", Integer.toString(slotNumber), "price", this.plugin.economy().format(this.plugin.homes().priceFor(index))
               };
               if (!this.plugin.config().homeConfirmPurchase()) {
                  this.purchase(player, index);
               } else {
                  ConfirmMenu.open(
                     this.plugin,
                     player,
                     "home.purchase",
                     target -> this.purchase(target, index),
                     target -> this.plugin.messages().send(target, "home.purchase-cancelled", "slot", Integer.toString(slotNumber)),
                     placeholders
                  );
               }
            }
         } else {
            this.plugin.messages().send(player, "home.usage-buy", "max", Integer.toString(this.plugin.homes().maxSlots()));
         }
      }
   }

   private void purchase(Player player, int index) {
      String slot = Integer.toString(index + 1);
      String price = this.plugin.economy().format(this.plugin.homes().priceFor(index));
      HomeManager.PurchaseResult result = this.plugin.homes().purchase(player, index);
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
   }

   private void handleTeleport(Player player, String name) {
      Home home = this.plugin.homes().byName(player.getUniqueId(), name);
      if (home == null) {
         this.plugin.messages().send(player, "home.not-found", "home", name);
      } else {
         TeleportRequest request = HomeTeleportRequests.create(this.plugin, player, home);
         if (request != null) {
            this.plugin.teleport().start(request);
         }
      }
   }

   private void sendUsage(Player player, String label) {
      this.plugin.messages().send(player, "home.usage-header");
      this.plugin.messages().send(player, "home.usage-open", "label", label);
      this.plugin.messages().send(player, "home.usage-set");
      this.plugin.messages().send(player, "home.usage-del");
      this.plugin.messages().send(player, "home.usage-teleport", "label", label);
      this.plugin.messages().send(player, "home.usage-buy", "max", Integer.toString(this.plugin.homes().maxSlots()));
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (sender instanceof Player player) {
         if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("help");
            options.add("list");
            options.add("buy");
            options.addAll(this.plugin.homes().homes(player.getUniqueId()).values().stream().map(Home::name).toList());
            return filter(options, args[0]);
         }

         if (args.length == 2 && args[0].equalsIgnoreCase("buy")) {
            List<String> slots = new ArrayList<>();

            for (int index = 0; index < this.plugin.homes().maxSlots(); index++) {
               if (!this.plugin.homes().isUnlocked(player.getUniqueId(), index)) {
                  slots.add(Integer.toString(index + 1));
               }
            }

            return filter(slots, args[1]);
         } else {
            return List.of();
         }
      } else {
         return List.of();
      }
   }

   private static List<String> filter(List<String> options, String prefix) {
      String lower = prefix.toLowerCase(Locale.ROOT);
      return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
   }
}
