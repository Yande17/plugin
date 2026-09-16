package me.w2n.w2nsmp.command;

import java.util.List;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.gui.RodMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/**
 * /rod (v1.4.0) - membuka GUI rod: level, XP, upgrade, attachment, statistik efek.
 *
 * <p>Menu bekerja pada rod di tangan utama; tanpa rod pun menu tetap terbuka dengan
 * petunjuk (supaya pemain tahu fiturnya ada).
 */
public final class RodCommand implements TabExecutor {
   private final W2NSMP plugin;

   public RodCommand(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!(sender instanceof Player player)) {
         this.plugin.messages().send(sender, "command.player-only");
         return true;
      }

      if (!player.hasPermission("w2nsmp.rod")) {
         this.plugin.messages().send(sender, "command.no-permission", "permission", "w2nsmp.rod");
         return true;
      }

      if (this.plugin.fishing() == null || !this.plugin.fishing().enabled()) {
         this.plugin.messages().send(player, "fishing.disabled");
         return true;
      }

      RodMenu.open(this.plugin, player);
      return true;
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      return List.of();
   }
}
