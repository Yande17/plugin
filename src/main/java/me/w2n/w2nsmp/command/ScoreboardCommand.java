package me.w2n.w2nsmp.command;

import java.util.List;
import java.util.Locale;
import me.w2n.w2nsmp.W2NSMP;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class ScoreboardCommand implements TabExecutor {
   private static final String PERMISSION = "w2nsmp.scoreboard";
   private final W2NSMP plugin;

   public ScoreboardCommand(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!sender.hasPermission("w2nsmp.scoreboard")) {
         this.plugin.messages().send(sender, "command.no-permission", "permission", "w2nsmp.scoreboard");
         return true;
      }

      if (this.plugin.config().scoreboardEnabled() && this.plugin.scoreboard() != null) {
         if (args.length <= 0 || !args[0].equalsIgnoreCase("help") && !args[0].equalsIgnoreCase("bantuan")) {
            if (sender instanceof Player player) {
               if (!this.plugin.config().scoreboardPersonalToggle()) {
                  this.plugin.messages().send(sender, "scoreboard.toggle-disabled");
                  return true;
               }

               boolean wanted;
               if (args.length == 0) {
                  boolean currentlyHidden = this.plugin.scoreboard().isHidden(player.getUniqueId());
                  wanted = currentlyHidden;
               } else {
                  switch (args[0].toLowerCase(Locale.ROOT)) {
                     case "on":
                     case "nyala":
                     case "aktif":
                     case "hidup":
                        wanted = true;
                        break;
                     case "off":
                     case "mati":
                     case "nonaktif":
                        wanted = false;
                        break;
                     default:
                        this.plugin.messages().send(sender, "scoreboard.usage", "usage", "/" + label + " [on|off]");
                        return true;
                  }
               }

               boolean already = this.plugin.scoreboard().visibleFor(player) == wanted;
               if (already) {
                  this.plugin.messages().send(sender, wanted ? "scoreboard.already-on" : "scoreboard.already-off");
                  return true;
               } else {
                  this.plugin.scoreboard().setVisible(player, wanted);
                  this.plugin.messages().send(sender, wanted ? "scoreboard.enabled" : "scoreboard.disabled");
                  return true;
               }
            } else {
               this.plugin.messages().send(sender, "scoreboard.player-only");
               return true;
            }
         } else {
            this.sendHelp(sender, label);
            return true;
         }
      } else {
         this.plugin.messages().send(sender, "scoreboard.feature-off");
         return true;
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (!sender.hasPermission("w2nsmp.scoreboard")) {
         return List.of();
      }

      String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
      return args.length <= 1 ? List.of("on", "off", "help").stream().filter(option -> option.startsWith(typed)).toList() : List.of();
   }

   private void sendHelp(CommandSender sender, String label) {
      this.plugin.messages().send(sender, "scoreboard.help-header", "label", label);
      this.plugin.messages().send(sender, "scoreboard.help-toggle", "label", label);
      this.plugin.messages().send(sender, "scoreboard.help-on", "label", label);
      this.plugin.messages().send(sender, "scoreboard.help-help", "label", label);
   }
}
