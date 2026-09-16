package me.w2n.w2nsmp.command;

import java.util.List;
import java.util.Locale;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.fishing.AutoFishService;
import me.w2n.w2nsmp.gui.AutoFishMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/**
 * /autofishing (v1.4.1) - GUI kontrol memancing otomatis; subcommand on/off untuk
 * yang tidak mau membuka GUI.
 */
public final class AutoFishCommand implements TabExecutor {
   private final W2NSMP plugin;

   public AutoFishCommand(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!(sender instanceof Player player)) {
         this.plugin.messages().send(sender, "command.player-only");
         return true;
      }

      if (!player.hasPermission("w2nsmp.autofish")) {
         this.plugin.messages().send(sender, "command.no-permission", "permission", "w2nsmp.autofish");
         return true;
      }

      AutoFishService auto = this.plugin.autoFish();
      if (auto == null || !auto.enabled()) {
         this.plugin.messages().send(player, "fishing.autofish.feature-disabled");
         return true;
      }

      if (args.length > 0) {
         String sub = args[0].toLowerCase(Locale.ROOT);

         if ("on".equals(sub) || "start".equals(sub)) {
            if (auto.isActive(player)) {
               this.plugin.messages().send(player, "fishing.autofish.already-on");
               return true;
            }

            String blocked = auto.start(player);
            if (blocked != null) {
               this.plugin.messages().send(player, blocked);
            } else {
               this.plugin.messages().send(player, "fishing.autofish.started",
                  "interval", Integer.toString(auto.intervalSeconds()));
            }

            return true;
         }

         if ("off".equals(sub) || "stop".equals(sub)) {
            if (!auto.isActive(player)) {
               this.plugin.messages().send(player, "fishing.autofish.already-off");
               return true;
            }

            auto.stop(player, "fishing.autofish.stopped");
            return true;
         }
      }

      AutoFishMenu.open(this.plugin, player);
      return true;
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (args.length == 1) {
         return List.of("on", "off");
      }

      return List.of();
   }
}
