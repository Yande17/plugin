package me.w2n.w2nsmp.command;

import java.util.List;
import me.w2n.w2nsmp.W2NSMP;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class RtpCommand implements TabExecutor {
   private static final String PERMISSION = "w2nsmp.rtp";
   private final W2NSMP plugin;

   public RtpCommand(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("w2nsmp.rtp")) {
            this.plugin.messages().send(player, "command.no-permission", "permission", "w2nsmp.rtp");
            return true;
         } else {
            this.plugin.rtp().request(player);
            return true;
         }
      } else {
         this.plugin.messages().send(sender, "command.player-only");
         return true;
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      return List.of();
   }
}
