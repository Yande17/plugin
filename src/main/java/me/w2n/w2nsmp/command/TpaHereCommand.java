package me.w2n.w2nsmp.command;

import java.util.ArrayList;
import java.util.List;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.utility.Players;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class TpaHereCommand implements TabExecutor {
   private static final String PERMISSION = "w2nsmp.tpahere";
   private final W2NSMP plugin;

   public TpaHereCommand(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("w2nsmp.tpahere")) {
            this.plugin.messages().send(player, "command.no-permission", "permission", "w2nsmp.tpahere");
            return true;
         } else if (args.length == 0) {
            this.plugin.messages().send(player, "tpa.usage-here", "usage", "/" + label + " <pemain>");
            return true;
         } else {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target != null && target.isOnline()) {
               this.plugin.tpa().request(player, target, true);
               return true;
            } else {
               this.plugin.messages().send(player, "tpa.target-offline", "target", args[0]);
               return true;
            }
         }
      } else {
         this.plugin.messages().send(sender, "command.player-only");
         return true;
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (sender instanceof Player player && player.hasPermission("w2nsmp.tpahere") && args.length == 1) {
         List<String> options = new ArrayList<>();

         for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(player.getUniqueId())) {
               options.add(online.getName());
            }
         }

         return Players.filter(options, args[0]);
      } else {
         return List.of();
      }
   }
}
