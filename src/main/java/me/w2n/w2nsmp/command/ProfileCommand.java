package me.w2n.w2nsmp.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.gui.ProfileMenu;
import me.w2n.w2nsmp.utility.Players;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class ProfileCommand implements TabExecutor {
   private static final String PERMISSION = "w2nsmp.profile";
   private static final String PERMISSION_OTHERS = "w2nsmp.profile.others";
   private final W2NSMP plugin;

   public ProfileCommand(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("w2nsmp.profile")) {
            this.plugin.messages().send(sender, "command.no-permission", "permission", "w2nsmp.profile");
            return true;
         }

         if (!this.plugin.config().profileEnabled()) {
            this.plugin.messages().send(player, "profile.disabled");
            return true;
         }

         Player target = player;
         boolean chat = false;

         for (String argument : args) {
            String value = argument.toLowerCase(Locale.ROOT);
            if (value.equals("chat")) {
               chat = true;
            } else {
               if (!argument.equalsIgnoreCase(player.getName()) && !player.hasPermission("w2nsmp.profile.others")) {
                  this.plugin.messages().send(player, "command.no-permission", "permission", "w2nsmp.profile.others");
                  return true;
               }

               if (argument.equalsIgnoreCase(player.getName())) {
                  target = player;
               } else {
                  Player found = Bukkit.getPlayerExact(argument);
                  if (found == null) {
                     String known = this.plugin.stats() == null ? null : this.plugin.stats().name(this.plugin.stats().findUniqueId(argument));
                     this.plugin.messages().send(player, "profile.unknown-player", "player", known == null ? argument : known);
                     return true;
                  }

                  target = found;
               }
            }
         }

         if (chat) {
            this.sendChat(player, target);
            return true;
         } else {
            ProfileMenu.open(this.plugin, player, target);
            return true;
         }
      } else {
         this.plugin.messages().send(sender, "command.player-only");
         return true;
      }
   }

   private void sendChat(Player viewer, Player target) {
      Map<String, String> values = ProfileMenu.values(this.plugin, target);
      this.plugin.messages().send(viewer, "profile.chat-header", "player", target.getName());

      for (String field : List.of("money", "highest-money", "kills", "deaths", "playtime", "bounty", "homes", "items-sold")) {
         this.plugin
            .messages()
            .send(viewer, "profile.chat-line", "label", this.plugin.messages().raw("profile.chat-label." + field), "value", values.getOrDefault(field, "-"));
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (!(sender instanceof Player player && player.hasPermission("w2nsmp.profile"))) {
         return List.of();
      } else if (args.length == 1) {
         List<String> names = new ArrayList<>();
         names.add("chat");
         if (player.hasPermission("w2nsmp.profile.others")) {
            for (Player online : Bukkit.getOnlinePlayers()) {
               names.add(online.getName());
            }
         }

         return Players.filter(names, args[0]);
      } else {
         return args.length == 2 ? Players.filter(List.of("chat"), args[1]) : List.of();
      }
   }
}
