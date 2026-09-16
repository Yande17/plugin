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

public final class TpAcceptCommand implements TabExecutor {
   private static final String PERMISSION = "w2nsmp.tpaccept";
   private final W2NSMP plugin;

   public TpAcceptCommand(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("w2nsmp.tpaccept")) {
            this.plugin.messages().send(player, "command.no-permission", "permission", "w2nsmp.tpaccept");
            return true;
         } else if (args.length == 0) {
            this.plugin.tpa().accept(player, null);
            return true;
         } else {
            Player other = Bukkit.getPlayerExact(args[0]);
            if (other == null) {
               this.plugin.messages().send(player, "tpa.no-request-from", "player", args[0]);
               return true;
            } else {
               this.plugin.tpa().accept(player, other.getUniqueId());
               return true;
            }
         }
      } else {
         this.plugin.messages().send(sender, "command.player-only");
         return true;
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (sender instanceof Player player && player.hasPermission("w2nsmp.tpaccept") && args.length == 1) {
         List<String> options = new ArrayList<>();

         for (TpaRequest request : this.plugin.tpa().incoming(player.getUniqueId())) {
            options.add(request.senderName());
         }

         return Players.filter(options, args[0]);
      } else {
         return List.of();
      }
   }
}
