package me.w2n.w2nsmp.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.utility.Items;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class WorthCommand implements TabExecutor {
   private static final int LIST_LIMIT = 12;
   private final W2NSMP plugin;

   public WorthCommand(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!this.plugin.config().worthEnabled()) {
         this.plugin.messages().send(sender, "worth.disabled");
         return true;
      }

      if (args.length == 0) {
         if (sender instanceof Player player) {
            this.showHand(player);
            return true;
         } else {
            this.plugin.messages().send(sender, "worth.player-only");
            return true;
         }
      } else {
         switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help":
            case "bantuan":
               this.sendHelp(sender, label);
               break;
            case "list":
            case "daftar":
               this.listPrices(sender, args.length > 1 ? args[1] : null);
               break;
            case "clear":
            case "bersihkan":
               this.clear(sender, label, args);
               break;
            case "on":
            case "nyala":
            case "aktif":
               this.toggle(sender, true);
               break;
            case "off":
            case "mati":
            case "nonaktif":
               this.toggle(sender, false);
               break;
            default:
               this.showMaterial(sender, args[0]);
         }

         return true;
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (args.length <= 1) {
         String typed = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
         return List.of("list", "clear", "on", "off", "help").stream().filter(option -> option.startsWith(typed)).toList();
      } else {
         return args.length == 2 && args[0].equalsIgnoreCase("list")
            ? this.plugin
               .sell()
               .prices()
               .materials()
               .stream()
               .map(material -> material.name().toLowerCase(Locale.ROOT))
               .filter(name -> name.startsWith(args[1].toLowerCase(Locale.ROOT)))
               .limit(12L)
               .toList()
            : List.of();
      }
   }

   private void showHand(Player player) {
      ItemStack hand = player.getInventory().getItemInMainHand();
      if (Items.isEmpty(hand)) {
         this.plugin.messages().send(player, "worth.hand-empty");
      } else {
         int unit = this.plugin.worth().unitPrice(hand.getType());
         if (unit <= 0) {
            this.plugin.messages().send(player, "worth.not-sellable", "item", hand.getType().name(), "amount", Integer.toString(hand.getAmount()));
         } else {
            this.plugin
               .messages()
               .send(
                  player,
                  "worth.hand",
                  "item",
                  hand.getType().name(),
                  "amount",
                  Integer.toString(hand.getAmount()),
                  "unit",
                  this.plugin.economy().format(unit),
                  "total",
                  this.plugin.economy().format(this.plugin.worth().stackTotal(hand))
               );
            this.plugin.messages().send(player, "worth.hand-hint");
         }
      }
   }

   private void showMaterial(CommandSender sender, String raw) {
      Material material = Material.matchMaterial(raw);
      if (material == null) {
         this.plugin.messages().send(sender, "worth.unknown-item", "input", raw);
      } else {
         int unit = this.plugin.worth().unitPrice(material);
         if (unit <= 0) {
            this.plugin.messages().send(sender, "worth.material-not-sellable", "item", material.name());
         } else {
            this.plugin
               .messages()
               .send(
                  sender,
                  "worth.material",
                  "item",
                  material.name(),
                  "unit",
                  this.plugin.economy().format(unit),
                  "stack",
                  this.plugin.economy().format((long)unit * material.getMaxStackSize()),
                  "max",
                  Integer.toString(material.getMaxStackSize())
               );
         }
      }
   }

   private void listPrices(CommandSender sender, String filter) {
      String needle = filter == null ? null : filter.toLowerCase(Locale.ROOT);
      List<Material> matches = new ArrayList<>();

      for (Material material : this.plugin.sell().prices().materials()) {
         if (needle == null || material.name().toLowerCase(Locale.ROOT).contains(needle)) {
            matches.add(material);
         }
      }

      matches.sort((left, right) -> Integer.compare(this.plugin.sell().currentUnitPrice(right), this.plugin.sell().currentUnitPrice(left)));
      if (matches.isEmpty()) {
         this.plugin.messages().send(sender, "worth.list-empty", "total", Integer.toString(this.plugin.sell().prices().size()));
      } else {
         this.plugin
            .messages()
            .send(
               sender,
               "worth.list-header",
               "count",
               Integer.toString(Math.min(matches.size(), 12)),
               "total",
               Integer.toString(this.plugin.sell().prices().size())
            );

         for (Material material : matches.subList(0, Math.min(matches.size(), 12))) {
            this.plugin
               .messages()
               .send(sender, "worth.list-entry", "item", material.name(), "unit", this.plugin.economy().format(this.plugin.sell().currentUnitPrice(material)));
         }
      }
   }

   private void clear(CommandSender sender, String label, String[] args) {
      if (args.length > 1) {
         this.plugin.messages().send(sender, "worth.clear-usage", "usage", "/" + label + " clear");
      } else if (sender instanceof Player player) {
         int cleared = this.plugin.worth().clear(player);
         this.plugin.messages().send(player, "worth.clear-self", "count", Integer.toString(cleared));
      } else {
         this.plugin.messages().send(sender, "worth.player-only");
      }
   }

   private void toggle(CommandSender sender, boolean enable) {
      if (sender instanceof Player player) {
         if (!this.plugin.config().worthPersonalToggle()) {
            this.plugin.messages().send(player, "worth.toggle-disabled");
         } else if (!this.plugin.config().worthInventoryLore()) {
            this.plugin.messages().send(player, "worth.inventory-lore-off");
         } else {
            boolean enabled = this.plugin.worth().isLoreEnabledFor(player);
            if (enabled == enable) {
               this.plugin.messages().send(player, enable ? "worth.already-on" : "worth.already-off");
            } else {
               this.plugin.worth().setPersonal(player, enable);
               if (enable) {
                  this.plugin.messages().send(player, "worth.turn-on", "count", Integer.toString(this.plugin.worth().markedSlots(player)));
               } else {
                  this.plugin.messages().send(player, "worth.turn-off");
               }
            }
         }
      } else {
         this.plugin.messages().send(sender, "worth.player-only");
      }
   }

   private void sendHelp(CommandSender sender, String label) {
      this.plugin.messages().send(sender, "worth.help-header");
      this.plugin.messages().send(sender, "worth.help-hand", "label", label);
      this.plugin.messages().send(sender, "worth.help-material", "label", label);
      this.plugin.messages().send(sender, "worth.help-list", "label", label);
      this.plugin.messages().send(sender, "worth.help-clear", "label", label);
      this.plugin.messages().send(sender, "worth.help-toggle", "label", label);
      this.plugin.messages().send(sender, "worth.help-note");
   }
}
