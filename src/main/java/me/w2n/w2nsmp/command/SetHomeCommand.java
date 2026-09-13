package me.w2n.w2nsmp.command;

import java.util.ArrayList;
import java.util.List;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.home.HomeManager;
import me.w2n.w2nsmp.home.PlayerHomes;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class SetHomeCommand implements TabExecutor {
   private static final String PERMISSION = "w2nsmp.home";
   private final W2NSMP plugin;

   public SetHomeCommand(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("w2nsmp.home")) {
            this.plugin.messages().send(player, "command.no-permission");
            return true;
         }

         if (args.length < 1) {
            this.plugin.messages().send(player, "home.usage-set");
            return true;
         }

         String name = args[0];
         HomeManager.SetHomeResult result = this.plugin.homes().setHome(player, name, player.getLocation());
         switch (result) {
            case CREATED:
               this.plugin.messages().send(player, "home.set-success", "home", name, "slot", Integer.toString(this.slotOf(player, name) + 1));
               break;
            case OVERWRITTEN:
               this.plugin.messages().send(player, "home.set-overwritten", "home", name);
               break;
            case NO_FREE_SLOT:
               this.plugin
                  .messages()
                  .send(
                     player,
                     "home.set-no-slot",
                     "used",
                     Integer.toString(this.plugin.homes().data(player).used()),
                     "slots",
                     Integer.toString(this.plugin.homes().data(player).unlocked())
                  );
               break;
            case INVALID_NAME:
               this.plugin.messages().send(player, "home.invalid-name");
         }

         return true;
      } else {
         this.plugin.messages().send(sender, "command.player-only");
         return true;
      }
   }

   private int slotOf(Player player, String name) {
      PlayerHomes data = this.plugin.homes().data(player);
      return data.slotOf(name);
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      return args.length == 1 ? new ArrayList<>(List.of("rumah", "base", "markas")) : List.of();
   }
}
