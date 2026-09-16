package me.w2n.w2nsmp.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.gui.SettingsMenu;
import me.w2n.w2nsmp.utility.Players;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class SettingCommand implements TabExecutor {
   private static final String PERMISSION = "w2nsmp.setting";
   private final W2NSMP plugin;

   public SettingCommand(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("w2nsmp.setting")) {
            this.plugin.messages().send(sender, "command.no-permission", "permission", "w2nsmp.setting");
            return true;
         }

         if (!this.plugin.config().settingsEnabled()) {
            this.plugin.messages().send(player, "setting.disabled");
            return true;
         }

         if (this.plugin.settings() == null || !this.plugin.settings().isLoaded()) {
            this.plugin.messages().send(player, "setting.unavailable");
            return true;
         }

         if (args.length == 0) {
            SettingsMenu.open(this.plugin, player);
            return true;
         }

         String sub = args[0].toLowerCase(Locale.ROOT);
         switch (sub) {
            case "list":
               this.sendList(player);
               break;
            case "info":
               this.sendInfo(player);
               break;
            case "toggle":
            case "set":
               if (args.length < 2) {
                  this.plugin.messages().send(player, "setting.usage-toggle");
                  return true;
               }

               this.toggle(player, join(args, 1));
               break;
            default:
               String key = resolve(args[0]);
               if (key == null) {
                  this.plugin.messages().send(player, "setting.unknown", "setting", args[0]);
                  return true;
               }

               this.toggle(player, key);
         }

         return true;
      } else {
         this.plugin.messages().send(sender, "command.player-only");
         return true;
      }
   }

   private void toggle(Player player, String rawKey) {
      String key = resolve(rawKey);
      if (key == null) {
         this.plugin.messages().send(player, "setting.unknown", "setting", rawKey);
      } else if (SettingsMenu.locked(this.plugin, key, player)) {
         this.plugin.messages().send(player, "setting.locked", "setting", SettingsMenu.label(this.plugin, key));
      } else if (!SettingsMenu.toggle(this.plugin, player, key)) {
         this.plugin.messages().send(player, "setting.not-changed", "setting", SettingsMenu.label(this.plugin, key));
      } else {
         this.plugin.messages().send(player, "setting.toggled", "setting", SettingsMenu.label(this.plugin, key));
      }
   }

   private void sendList(Player player) {
      this.plugin.messages().send(player, "setting.list-header", "player", player.getName());

      for (String key : SettingsMenu.keys(this.plugin)) {
         boolean on;
         if (key.startsWith("line:")) {
            on = this.plugin.scoreboard() != null && this.plugin.scoreboard().isLineVisible(player.getUniqueId(), key.substring("line:".length()));
         } else {
            on = this.currentValue(player, key);
         }

         boolean locked = SettingsMenu.locked(this.plugin, key, player);
         this.plugin
            .messages()
            .send(
               player,
               "setting.list-line",
               "setting",
               SettingsMenu.label(this.plugin, key),
               "state",
               this.plugin.messages().raw(locked ? "setting.state-locked" : (on ? "setting.state-on" : "setting.state-off")),
               "key",
               key
            );
      }
   }

   private void sendInfo(Player player) {
      this.plugin
         .messages()
         .send(player, "setting.info-chat", "file", this.plugin.settings().file().getName(), "count", Integer.toString(this.plugin.settings().playerCount()));
   }

   private boolean currentValue(Player player, String key) {
      return switch (key) {
         case "scoreboard" -> this.plugin.scoreboard() != null && this.plugin.scoreboard().visibleFor(player);
         case "nametag-money" -> this.plugin.settings().nametagMoney(player);
         case "sounds" -> this.plugin.settings().sounds(player);
         case "notifications" -> this.plugin.settings().notifications(player);
         case "teleport-countdown" -> this.plugin.settings().teleportCountdown(player);
         case "notify-bounty" -> this.plugin.settings().bountyNotifications(player);
         case "notify-auction" -> this.plugin.settings().auctionNotifications(player);
         case "notify-tpa" -> this.plugin.settings().tpaNotifications(player);
         default -> false;
      };
   }

   public static String resolve(String raw) {
      if (raw == null) {
         return null;
      }

      String key = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '-');
      if (key.isEmpty()) {
         return null;
      }

      if (key.startsWith("line:")) {
         return key;
      }

      return switch (key) {
         case "scoreboard", "sb", "sidebar" -> "scoreboard";
         case "nametag-money", "nametag", "money" -> "nametag-money";
         case "sounds", "sound", "bunyi" -> "sounds";
         case "notifications", "notif", "notification", "notifikasi" -> "notifications";
         case "teleport-countdown", "countdown", "teleport", "tp-countdown" -> "teleport-countdown";
         case "notify-bounty", "bounty" -> "notify-bounty";
         case "notify-auction", "auction", "ah" -> "notify-auction";
         case "notify-tpa", "tpa" -> "notify-tpa";
         default -> key.startsWith("line-") ? "line:" + key.substring("line-".length()) : null;
      };
   }

   private static String join(String[] args, int from) {
      StringBuilder builder = new StringBuilder();

      for (int index = from; index < args.length; index++) {
         if (builder.length() > 0) {
            builder.append(' ');
         }

         builder.append(args[index]);
      }

      return builder.toString();
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (!(sender instanceof Player player && player.hasPermission("w2nsmp.setting"))) {
         return List.of();
      } else if (args.length <= 1) {
         List<String> options = new ArrayList<>(
            List.of(
               "list",
               "info",
               "toggle",
               "scoreboard",
               "nametag-money",
               "sounds",
               "notifications",
               "teleport-countdown",
               "notify-bounty",
               "notify-auction",
               "notify-tpa"
            )
         );
         options.addAll(SettingsMenu.keys(this.plugin));
         return Players.filter(options, args.length == 0 ? "" : args[0]);
      } else {
         return args.length == 2 ? Players.filter(SettingsMenu.keys(this.plugin), args[1]) : List.of();
      }
   }
}
