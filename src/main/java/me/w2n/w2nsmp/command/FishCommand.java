package me.w2n.w2nsmp.command;

import java.util.List;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.gui.FishMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/**
 * /fish (v1.4.1) - membuka Fishing Hub: Fish Gallery (ikan custom yang sudah/belum
 * ditemukan) dan pintu ke menu rod (/rod).
 */
public final class FishCommand implements TabExecutor {
   private final W2NSMP plugin;

   public FishCommand(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!(sender instanceof Player player)) {
         this.plugin.messages().send(sender, "command.player-only");
         return true;
      }

      if (!player.hasPermission("w2nsmp.fish")) {
         this.plugin.messages().send(sender, "command.no-permission", "permission", "w2nsmp.fish");
         return true;
      }

      if (this.plugin.fishing() == null || !this.plugin.fishing().enabled()) {
         this.plugin.messages().send(player, "fishing.disabled");
         return true;
      }

      FishMenu.open(this.plugin, player);
      return true;
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      return List.of();
   }
}
