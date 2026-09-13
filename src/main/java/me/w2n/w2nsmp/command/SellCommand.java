package me.w2n.w2nsmp.command;

import java.util.List;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.gui.SellMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class SellCommand implements TabExecutor {
   private static final String PERMISSION = "w2nsmp.sell";
   private final W2NSMP plugin;

   public SellCommand(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("w2nsmp.sell")) {
            this.plugin.messages().send(sender, "command.no-permission", "permission", "w2nsmp.sell");
            return true;
         } else if (!this.plugin.config().sellEnabled()) {
            this.plugin.messages().send(player, "sell.disabled");
            return true;
         } else if (!this.plugin.economy().isEnabled()) {
            this.plugin.messages().send(player, "economy.disabled");
            return true;
         } else {
            SellMenu.open(this.plugin, player);
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
