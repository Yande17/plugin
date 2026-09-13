package me.w2n.w2nsmp.manager;

import java.util.Collections;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

final class RuntimeCommand extends Command {
   private final TabExecutor handler;

   RuntimeCommand(String name, List<String> aliases, TabExecutor handler) {
      super(name, "W2NSMP command", "/" + name, List.copyOf(aliases));
      this.handler = handler;
   }

   public boolean execute(CommandSender sender, String commandLabel, String[] args) {
      return this.handler.onCommand(sender, this, commandLabel, args);
   }

   public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
      List<String> completion = this.handler.onTabComplete(sender, this, alias, args);
      return completion == null ? Collections.emptyList() : completion;
   }
}
