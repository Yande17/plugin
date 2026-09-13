package me.w2n.w2nsmp.command;

import java.util.List;
import java.util.Locale;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.home.Home;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class DelHomeCommand implements TabExecutor {
   private static final String PERMISSION = "w2nsmp.home";
   private final W2NSMP plugin;

   public DelHomeCommand(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("w2nsmp.home")) {
            this.plugin.messages().send(player, "command.no-permission");
            return true;
         } else if (args.length < 1) {
            this.plugin.messages().send(player, "home.usage-del");
            return true;
         } else {
            Home removed = this.plugin.homes().deleteHome(player, args[0]);
            if (removed == null) {
               this.plugin.messages().send(player, "home.not-found", "home", args[0]);
               return true;
            } else {
               this.plugin.messages().send(player, "home.delete-success", "home", removed.name());
               return true;
            }
         }
      } else {
         this.plugin.messages().send(sender, "command.player-only");
         return true;
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (sender instanceof Player player && args.length == 1) {
         String prefix = args[0].toLowerCase(Locale.ROOT);
         return this.plugin
            .homes()
            .homes(player.getUniqueId())
            .values()
            .stream()
            .map(Home::name)
            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
            .toList();
      } else {
         return List.of();
      }
   }
}
