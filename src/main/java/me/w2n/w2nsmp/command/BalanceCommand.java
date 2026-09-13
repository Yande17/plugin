package me.w2n.w2nsmp.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.config.Messages;
import me.w2n.w2nsmp.economy.EconomyManager;
import me.w2n.w2nsmp.utility.Players;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class BalanceCommand implements TabExecutor {
   private static final String PERM_SELF = "w2nsmp.balance";
   private static final String PERM_OTHERS = "w2nsmp.balance.others";
   private final W2NSMP plugin;

   public BalanceCommand(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      Messages messages = this.plugin.messages();
      EconomyManager economy = this.plugin.economy();
      if (!economy.isEnabled()) {
         messages.send(sender, "economy.disabled");
         return true;
      }

      if (args.length == 0) {
         if (sender instanceof Player player) {
            if (!player.hasPermission("w2nsmp.balance")) {
               messages.send(sender, "command.no-permission", "permission", "w2nsmp.balance");
               return true;
            } else {
               this.showBalance(sender, player, false, Players.displayName(player, "?"));
               return true;
            }
         } else {
            messages.send(sender, "command.player-only");
            return true;
         }
      } else if (!sender.hasPermission("w2nsmp.balance.others")) {
         messages.send(sender, "command.no-permission", "permission", "w2nsmp.balance.others");
         return true;
      } else if (!this.plugin.config().balanceAllowOthers()) {
         messages.send(sender, "economy.balance-others-disabled");
         return true;
      } else {
         String inputName = args[0];
         OfflinePlayer target = Players.findOnlineOrCached(inputName);
         if (target == null) {
            messages.send(sender, "economy.player-not-found", "player", inputName);
            return true;
         } else {
            this.showBalance(sender, target, true, Players.displayName(target, inputName));
            return true;
         }
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (args.length == 1 && sender.hasPermission("w2nsmp.balance.others")) {
         String typed = args[0].toLowerCase(Locale.ROOT);
         List<String> names = new ArrayList<>();

         for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(typed)) {
               names.add(online.getName());
            }
         }

         return names;
      } else {
         return List.of();
      }
   }

   private void showBalance(CommandSender sender, OfflinePlayer target, boolean others, String displayName) {
      Messages messages = this.plugin.messages();
      EconomyManager economy = this.plugin.economy();
      if (!economy.hasAccount(target)) {
         messages.send(sender, "economy.account-not-found", "player", displayName);
      } else {
         String balance = economy.format(economy.balance(target));
         if (others) {
            messages.send(sender, "economy.balance-others", "player", displayName, "balance", balance);
         } else {
            messages.send(sender, "economy.balance-self", "balance", balance);
         }
      }
   }
}
