package me.w2n.w2nsmp.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.bounty.BountyEntry;
import me.w2n.w2nsmp.economy.MoneyFormatter;
import me.w2n.w2nsmp.gui.BountyMenu;
import me.w2n.w2nsmp.utility.Players;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class BountyCommand implements TabExecutor {
   private static final String PERMISSION = "w2nsmp.bounty";
   private static final int CHAT_LIMIT = 10;
   private final W2NSMP plugin;

   public BountyCommand(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("w2nsmp.bounty")) {
            this.plugin.messages().send(player, "command.no-permission", "permission", "w2nsmp.bounty");
            return true;
         }

         if (args.length == 0) {
            if (!this.plugin.bounty().enabled()) {
               this.plugin.messages().send(player, "bounty.disabled");
               return true;
            } else {
               BountyMenu.open(this.plugin, player);
               return true;
            }
         } else {
            String first = args[0].toLowerCase(Locale.ROOT);
            switch (first) {
               case "help":
                  this.sendHelp(player, label);
                  return true;
               case "top":
                  this.sendTop(player);
                  return true;
               case "info":
                  this.sendInfo(player, args.length > 1 ? args[1] : player.getName());
                  return true;
               default:
                  if (args.length < 2) {
                     this.plugin.messages().send(player, "bounty.usage", "usage", "/" + label + " <pemain> <jumlah>");
                     return true;
                  } else {
                     OfflinePlayer target = Players.findOnlineOrCached(args[0]);
                     if (target == null) {
                        this.plugin.messages().send(player, "bounty.unknown-player", "player", args[0]);
                        return true;
                     } else {
                        long amount = this.parseAmount(args[1]);
                        if (amount <= 0L) {
                           this.plugin.messages().send(player, "bounty.invalid-amount", "input", args[1]);
                           return true;
                        } else {
                           this.plugin.bounty().place(player, target, amount);
                           return true;
                        }
                     }
                  }
            }
         }
      } else {
         this.plugin.messages().send(sender, "command.player-only");
         return true;
      }
   }

   private long parseAmount(String raw) {
      return MoneyFormatter.parseLongAmount(raw);
   }

   private void sendTop(Player player) {
      List<BountyEntry> entries = this.plugin.bounty().top();
      this.plugin
         .messages()
         .send(
            player,
            "bounty.top-header",
            "count",
            Integer.toString(entries.size()),
            "total",
            this.plugin.economy() == null ? Long.toString(this.plugin.bounty().total()) : this.plugin.economy().format(this.plugin.bounty().total())
         );
      if (entries.isEmpty()) {
         this.plugin.messages().send(player, "bounty.top-empty");
      } else {
         int rank = 0;

         for (BountyEntry entry : entries) {
            if (++rank > 10) {
               break;
            }

            String name = entry.displayName(entry.uniqueId().toString());
            this.plugin
               .messages()
               .send(
                  player,
                  "bounty.top-entry",
                  "rank",
                  Integer.toString(rank),
                  "player",
                  name,
                  "target",
                  name,
                  "amount",
                  this.plugin.economy() != null && this.plugin.economy().isEnabled()
                     ? this.plugin.economy().format(entry.amount())
                     : Long.toString(entry.amount())
               );
         }
      }
   }

   private void sendInfo(Player player, String name) {
      OfflinePlayer target = (OfflinePlayer)(Bukkit.getPlayerExact(name) != null ? Bukkit.getPlayerExact(name) : Players.findOnlineOrCached(name));
      if (target == null) {
         this.plugin.messages().send(player, "bounty.unknown-player", "player", name);
      } else {
         String display = target.getName() == null ? name : target.getName();
         boolean none = this.plugin.bounty().bounty(target.getUniqueId()) <= 0L;
         String amount = none ? this.plugin.messages().raw("bounty.none") : this.plugin.bounty().formatted(target.getUniqueId());
         this.plugin
            .messages()
            .send(
               player,
               target.getUniqueId().equals(player.getUniqueId()) ? "bounty.info-self" : "bounty.info-other",
               "player",
               display,
               "target",
               display,
               "amount",
               amount,
               "bounty",
               amount,
               "minimum",
               this.plugin.economy() == null
                  ? Long.toString(this.plugin.config().bountyMinimum())
                  : this.plugin.economy().format(this.plugin.config().bountyMinimum()),
               "maximum",
               this.plugin.economy() == null
                  ? Long.toString(this.plugin.config().bountyMaximum())
                  : this.plugin.economy().format(this.plugin.config().bountyMaximum())
            );
      }
   }

   private void sendHelp(Player player, String label) {
      this.plugin.messages().send(player, "bounty.help-header", "label", label);
      this.plugin.messages().send(player, "bounty.help-list", "label", label);
      this.plugin.messages().send(player, "bounty.help-set", "label", label);
      this.plugin.messages().send(player, "bounty.help-top", "label", label);
      this.plugin.messages().send(player, "bounty.help-info", "label", label);
      this.plugin
         .messages()
         .send(
            player,
            "bounty.help-note",
            "minimum",
            this.plugin.economy() == null
               ? Long.toString(this.plugin.config().bountyMinimum())
               : this.plugin.economy().format(this.plugin.config().bountyMinimum()),
            "maximum",
            this.plugin.economy() == null
               ? Long.toString(this.plugin.config().bountyMaximum())
               : this.plugin.economy().format(this.plugin.config().bountyMaximum())
         );
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (!(sender instanceof Player player && player.hasPermission("w2nsmp.bounty"))) {
         return List.of();
      } else if (args.length == 1) {
         List<String> options = new ArrayList<>(List.of("top", "info", "help"));

         for (Player online : Bukkit.getOnlinePlayers()) {
            options.add(online.getName());
         }

         return Players.filter(options, args[0]);
      } else {
         if (args.length != 2) {
            return List.of();
         }

         List<String> options = new ArrayList<>();

         for (long preset : this.plugin.config().bountyPresets()) {
            options.add(Long.toString(preset));
         }

         return Players.filter(options, args[1]);
      }
   }
}
