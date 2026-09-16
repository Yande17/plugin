package me.w2n.w2nsmp.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.config.Messages;
import me.w2n.w2nsmp.economy.EconomyManager;
import me.w2n.w2nsmp.economy.MoneyFormatter;
import me.w2n.w2nsmp.economy.TransferResult;
import me.w2n.w2nsmp.utility.Players;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class PayCommand implements TabExecutor {
   private static final String PERM_PAY = "w2nsmp.pay";
   private static final double MAX_AMOUNT = 1.0E9;
   private final W2NSMP plugin;
   private final Map<UUID, Long> lastPayment = new HashMap<>();

   public PayCommand(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      Messages messages = this.plugin.messages();
      EconomyManager economy = this.plugin.economy();
      if (sender instanceof Player player) {
         if (!player.hasPermission("w2nsmp.pay")) {
            messages.send(sender, "command.no-permission", "permission", "w2nsmp.pay");
            return true;
         }

         if (!this.plugin.config().payEnabled()) {
            messages.send(sender, "economy.pay-disabled");
            return true;
         }

         if (!economy.isEnabled()) {
            messages.send(sender, "economy.disabled");
            return true;
         }

         if (args.length != 2) {
            messages.send(sender, "economy.pay-usage", "usage", "/" + label + " <pemain> <jumlah>");
            return true;
         }

         String inputName = args[0];
         OfflinePlayer target = this.resolveTarget(inputName);
         if (target == null) {
            messages.send(sender, "economy.player-offline", "player", inputName);
            return true;
         }

         if (target.getUniqueId().equals(player.getUniqueId())) {
            messages.send(sender, "economy.pay-self");
            return true;
         }

         Double amount = this.parseAmount(args[1]);
         double minimum = this.plugin.config().payMinimum();
         if (amount == null) {
            messages.send(sender, "economy.invalid-amount", "input", args.length > 1 ? args[1] : "?");
            return true;
         }

         if (amount < minimum) {
            messages.send(sender, "economy.pay-minimum", "minimum", economy.format(minimum));
            return true;
         }

         long cooldownMillis = this.plugin.config().payCooldownMillis();
         if (cooldownMillis > 0L) {
            long now = System.currentTimeMillis();
            Long previous = this.lastPayment.get(player.getUniqueId());
            if (previous != null) {
               long remaining = cooldownMillis - (now - previous);
               if (remaining > 0L) {
                  messages.send(sender, "economy.pay-cooldown", "seconds", String.format(Locale.US, "%.1f", remaining / 1000.0));
                  return true;
               }
            }

            this.lastPayment.put(player.getUniqueId(), now);
         }

         if (!economy.has(player, amount)) {
            messages.send(sender, "economy.pay-insufficient", "balance", economy.format(economy.balance(player)));
            return true;
         }

         String displayName = Players.displayName(target, inputName);
         TransferResult result = economy.transfer(player, target, amount);
         switch (result) {
            case SUCCESS:
               messages.send(sender, "economy.pay-success", "player", displayName, "amount", economy.format(amount));
               if (target instanceof Player onlineTarget) {
                  messages.send(onlineTarget, "economy.pay-received", "player", Players.displayName(player, "?"), "amount", economy.format(amount));
               }

               this.plugin.debug("PAY " + player.getName() + " -> " + displayName + " : " + amount);
               break;
            case INSUFFICIENT_FUNDS:
               messages.send(sender, "economy.pay-insufficient", "balance", economy.format(economy.balance(player)));
               break;
            case ACCOUNT_NOT_FOUND:
               messages.send(sender, "economy.account-not-found", "player", displayName);
               break;
            case INVALID_AMOUNT:
               messages.send(sender, "economy.invalid-amount", "input", args.length > 1 ? args[1] : "?");
               break;
            case DISABLED:
               messages.send(sender, "economy.disabled");
               break;
            case FAILED:
               messages.send(sender, "economy.transaction-failed");
               this.plugin
                  .getLogger()
                  .warning(
                     "Transaksi /pay gagal: " + player.getName() + " -> " + displayName + " sebesar " + amount + " (dana sudah dikembalikan bila terdebit)."
                  );
         }

         return true;
      } else {
         messages.send(sender, "command.player-only");
         return true;
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (args.length == 1) {
         String typed = args[0].toLowerCase(Locale.ROOT);
         List<String> names = new ArrayList<>();

         for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(typed)) {
               names.add(online.getName());
            }
         }

         return names;
      } else if (args.length == 2) {
         String typed = args[1].toLowerCase(Locale.ROOT);
         List<String> amounts = new ArrayList<>();

         for (String suggestion : new String[]{"100", "1k", "10k", "100k", "1m", "10m"}) {
            if (suggestion.startsWith(typed)) {
               amounts.add(suggestion);
            }
         }

         return amounts;
      } else {
         return List.of();
      }
   }

   private OfflinePlayer resolveTarget(String name) {
      Player online = Bukkit.getPlayerExact(name);
      if (online != null) {
         return online;
      } else {
         return !this.plugin.config().payAllowOfflineTarget() ? null : Players.findOnlineOrCached(name);
      }
   }

   private Double parseAmount(String raw) {
      double value = MoneyFormatter.parseAmount(raw);
      return Double.isFinite(value) && !(value <= 0.0) && !(value > 1.0E9) ? value : null;
   }
}
