package me.w2n.w2nsmp.command;

import java.util.ArrayList;
import java.util.List;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.teleport.TpaRequest;
import me.w2n.w2nsmp.utility.Players;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class TpaCancelCommand implements TabExecutor {
   private static final String PERMISSION = "w2nsmp.tpa";
   private final W2NSMP plugin;

   public TpaCancelCommand(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("w2nsmp.tpa")) {
            this.plugin.messages().send(player, "command.no-permission", "permission", "w2nsmp.tpa");
            return true;
         }

         if (args.length > 0) {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
               this.plugin.messages().send(player, "tpa.no-outgoing");
               return true;
            } else {
               this.plugin.tpa().cancelOutgoing(player, target.getUniqueId());
               return true;
            }
         } else {
            this.plugin.tpa().cancelOutgoing(player, null);
            this.plugin.teleport().cancel(player, "cancel");
            return true;
         }
      } else {
         this.plugin.messages().send(sender, "command.player-only");
         return true;
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (sender instanceof Player player && player.hasPermission("w2nsmp.tpa") && args.length == 1) {
         List<String> options = new ArrayList<>();

         for (TpaRequest request : this.plugin.tpa().outgoing(player.getUniqueId())) {
            options.add(request.targetName());
         }

         return Players.filter(options, args[0]);
      } else {
         return List.of();
      }
   }
}
