package me.w2n.w2nsmp.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.gui.TpaMenu;
import me.w2n.w2nsmp.teleport.TpaRequest;
import me.w2n.w2nsmp.utility.Players;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class TpaCommand implements TabExecutor {
   private static final String PERMISSION = "w2nsmp.tpa";
   private final W2NSMP plugin;

   public TpaCommand(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("w2nsmp.tpa")) {
            this.plugin.messages().send(player, "command.no-permission", "permission", "w2nsmp.tpa");
            return true;
         }

         if (args.length == 0) {
            if (!this.plugin.tpa().enabled()) {
               this.plugin.messages().send(player, "tpa.disabled");
               return true;
            } else {
               TpaMenu.open(this.plugin, player);
               return true;
            }
         } else {
            String first = args[0].toLowerCase(Locale.ROOT);
            if ("help".equals(first)) {
               this.sendHelp(player, label);
               return true;
            } else if ("list".equals(first)) {
               this.sendList(player);
               return true;
            } else {
               Player target = Bukkit.getPlayerExact(args[0]);
               if (target != null && target.isOnline()) {
                  this.plugin.tpa().request(player, target, false);
                  return true;
               } else {
                  this.plugin.messages().send(player, "tpa.target-offline", "target", args[0]);
                  return true;
               }
            }
         }
      } else {
         this.plugin.messages().send(sender, "command.player-only");
         return true;
      }
   }

   private void sendList(Player player) {
      List<TpaRequest> incoming = this.plugin.tpa().incoming(player.getUniqueId());
      List<TpaRequest> outgoing = this.plugin.tpa().outgoing(player.getUniqueId());
      this.plugin.messages().send(player, "tpa.info-header", "incoming", Integer.toString(incoming.size()), "outgoing", Integer.toString(outgoing.size()));
      if (incoming.isEmpty() && outgoing.isEmpty()) {
         this.plugin.messages().send(player, "tpa.gui-empty-name");
      } else {
         long now = System.currentTimeMillis();

         for (TpaRequest request : incoming) {
            this.plugin
               .messages()
               .send(
                  player,
                  "tpa.info-entry-incoming",
                  "player",
                  request.senderName(),
                  "seconds",
                  Integer.toString(request.remainingSeconds(now)),
                  "type",
                  request.here() ? "tpahere" : "tpa"
               );
         }

         for (TpaRequest request : outgoing) {
            this.plugin
               .messages()
               .send(
                  player,
                  "tpa.info-entry-outgoing",
                  "player",
                  request.targetName(),
                  "seconds",
                  Integer.toString(request.remainingSeconds(now)),
                  "type",
                  request.here() ? "tpahere" : "tpa"
               );
         }
      }
   }

   private void sendHelp(Player player, String label) {
      this.plugin.messages().send(player, "tpa.help-header", "label", label);
      this.plugin.messages().send(player, "tpa.help-list", "label", label);
      this.plugin.messages().send(player, "tpa.help-request", "label", label);
      this.plugin.messages().send(player, "tpa.help-accept", "label", label);
      this.plugin.messages().send(player, "tpa.help-deny", "label", label);
      this.plugin.messages().send(player, "tpa.help-here", "label", label);
      this.plugin.messages().send(player, "tpa.help-cancel", "label", label);
      this.plugin.messages().send(player, "tpa.help-note");
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (!(sender instanceof Player player && player.hasPermission("w2nsmp.tpa"))) {
         return List.of();
      } else if (args.length == 1) {
         List<String> options = new ArrayList<>(List.of("list", "help"));

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
