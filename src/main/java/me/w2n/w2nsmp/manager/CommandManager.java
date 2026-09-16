package me.w2n.w2nsmp.manager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.command.AuctionCommand;
import me.w2n.w2nsmp.command.BalanceCommand;
import me.w2n.w2nsmp.command.BountyCommand;
import me.w2n.w2nsmp.command.DelHomeCommand;
import me.w2n.w2nsmp.command.HomeCommand;
import me.w2n.w2nsmp.command.PayCommand;
import me.w2n.w2nsmp.command.ProfileCommand;
import me.w2n.w2nsmp.command.RtpCommand;
import me.w2n.w2nsmp.command.ScoreboardCommand;
import me.w2n.w2nsmp.command.SellCommand;
import me.w2n.w2nsmp.command.SetHomeCommand;
import me.w2n.w2nsmp.command.SettingCommand;
import me.w2n.w2nsmp.command.AutoFishCommand;
import me.w2n.w2nsmp.command.FishCommand;
import me.w2n.w2nsmp.command.RodCommand;
import me.w2n.w2nsmp.command.SkillCommand;
import me.w2n.w2nsmp.command.TopCommand;
import me.w2n.w2nsmp.command.TpAcceptCommand;
import me.w2n.w2nsmp.command.TpDenyCommand;
import me.w2n.w2nsmp.command.TpaCancelCommand;
import me.w2n.w2nsmp.command.TpaCommand;
import me.w2n.w2nsmp.command.TpaHereCommand;
import me.w2n.w2nsmp.command.W2NSMPCommand;
import me.w2n.w2nsmp.command.WorthCommand;
import me.w2n.w2nsmp.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;

public final class CommandManager {
   public static final String FALLBACK_PREFIX = "w2nsmp";
   private final W2NSMP plugin;
   private final List<String> failedCommands = new ArrayList<>();
   private final List<CommandManager.Registered> registered = new ArrayList<>();
   private final Map<String, Command> owned = new LinkedHashMap<>();
   private final Map<String, String> claimedLabels = new LinkedHashMap<>();

   public CommandManager(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   public void register() {
      this.registered.clear();
      this.failedCommands.clear();
      this.owned.clear();
      this.claimedLabels.clear();
      this.registerDeclared("w2nsmp", new W2NSMPCommand(this.plugin), "w2nsmp.use", null);
      this.registerRuntime("balance", new BalanceCommand(this.plugin), "w2nsmp.balance", "help.balance", List.of("saldo"));
      this.registerRuntime("pay", new PayCommand(this.plugin), "w2nsmp.pay", "help.pay", List.of("bayar"));
      this.registerRuntime("sell", new SellCommand(this.plugin), "w2nsmp.sell", "help.sell", List.of("jual"));
      this.registerRuntime("home", new HomeCommand(this.plugin), "w2nsmp.home", "help.home", List.of());
      this.registerRuntime("sethome", new SetHomeCommand(this.plugin), "w2nsmp.home", "help.sethome", List.of());
      this.registerRuntime("delhome", new DelHomeCommand(this.plugin), "w2nsmp.home", "help.delhome", List.of());
      this.registerRuntime("rtp", new RtpCommand(this.plugin), "w2nsmp.rtp", "help.rtp", List.of("wild"));
      this.registerRuntime("ah", new AuctionCommand(this.plugin), "w2nsmp.auction", "help.auction", List.of("auction", "ahouse"));
      this.registerRuntime("harga", new WorthCommand(this.plugin), "w2nsmp.worth", "help.worth", List.of("worth"));
      this.registerRuntime("top", new TopCommand(this.plugin), "w2nsmp.top", "help.top", List.of("peringkat"));
      this.registerRuntime("sb", new ScoreboardCommand(this.plugin), "w2nsmp.scoreboard", "help.scoreboard", List.of("scoreboard", "sidebar"));
      this.registerRuntime("profile", new ProfileCommand(this.plugin), "w2nsmp.profile", "help.profile", List.of("profil", "stats-saya"));
      this.registerRuntime("setting", new SettingCommand(this.plugin), "w2nsmp.setting", "help.setting", List.of("settings", "pengaturan"));
      this.registerRuntime("skill", new SkillCommand(this.plugin), "w2nsmp.skill", "help.skill", List.of("skills", "keahlian"));
      this.registerRuntime("rod", new RodCommand(this.plugin), "w2nsmp.rod", "help.rod", List.of("pancingan"));
      this.registerRuntime("fish", new FishCommand(this.plugin), "w2nsmp.fish", "help.fish", List.of("ikan", "fishing"));
      this.registerRuntime("autofishing", new AutoFishCommand(this.plugin), "w2nsmp.autofish", "help.autofish", List.of("autofish", "afish"));
      this.registerRuntime("tpa", new TpaCommand(this.plugin), "w2nsmp.tpa", "help.tpa", List.of());
      this.registerRuntime("tpahere", new TpaHereCommand(this.plugin), "w2nsmp.tpahere", "help.tpahere", List.of());
      this.registerRuntime("tpaccept", new TpAcceptCommand(this.plugin), "w2nsmp.tpaccept", "help.tpaccept", List.of());
      this.registerRuntime("tpdeny", new TpDenyCommand(this.plugin), "w2nsmp.tpdeny", "help.tpdeny", List.of());
      this.registerRuntime("tpacancel", new TpaCancelCommand(this.plugin), "w2nsmp.tpa", "help.tpacancel", List.of());
      this.registerRuntime("bounty", new BountyCommand(this.plugin), "w2nsmp.bounty", "help.bounty", List.of());
   }

   public void unregister() {
      CommandMap map = Bukkit.getCommandMap();

      for (Command command : this.owned.values()) {
         if (command instanceof PluginCommand pluginCommand) {
            pluginCommand.setExecutor(null);
            pluginCommand.setTabCompleter(null);
         } else {
            command.unregister(map);
         }
      }

      this.owned.clear();
      this.registered.clear();
   }

   public List<String> failures() {
      return Collections.unmodifiableList(this.failedCommands);
   }

   public Map<String, String> claimedLabels() {
      return Collections.unmodifiableMap(this.claimedLabels);
   }

   public List<String> labelsTakenFrom(String pluginName) {
      List<String> result = new ArrayList<>();

      for (Entry<String, String> entry : this.claimedLabels.entrySet()) {
         if (entry.getValue().equalsIgnoreCase(pluginName)) {
            result.add(entry.getKey());
         }
      }

      Collections.sort(result);
      return result;
   }

   public List<String> pluginsLosingLabels() {
      List<String> names = new ArrayList<>();

      for (String owner : this.claimedLabels.values()) {
         if (!names.contains(owner)) {
            names.add(owner);
         }
      }

      Collections.sort(names);
      return names;
   }

   public List<CommandManager.Registered> registered() {
      return Collections.unmodifiableList(this.registered);
   }

   private void registerDeclared(String name, TabExecutor executor, String permission, String descriptionKey) {
      if (!this.plugin.config().isCommandEnabled(name)) {
         this.plugin.getLogger().info("Command /" + name + " dimatikan lewat config.yml - dilewati.");
      } else {
         PluginCommand command = this.plugin.getCommand(name);
         if (command == null) {
            this.failedCommands.add(name);
            this.plugin.getLogger().warning("Command /" + name + " tidak ditemukan di plugin.yml - dilewati (plugin tetap aktif).");
         } else {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
            this.owned.put(name, command);
            this.registered.add(new CommandManager.Registered(name, permission, descriptionKey));
         }
      }
   }

   private void registerRuntime(String name, TabExecutor executor, String permission, String descriptionKey, List<String> aliases) {
      if (!this.plugin.config().isCommandEnabled(name)) {
         this.plugin.getLogger().info("Command /" + name + " dimatikan lewat config.yml - dilewati.");
      } else {
         CommandMap map = Bukkit.getCommandMap();
         Command command = new RuntimeCommand(name, aliases, executor);
         map.register("w2nsmp", command);
         this.owned.put(name, command);
         this.registered.add(new CommandManager.Registered(name, permission, descriptionKey));
         Map<String, Command> known = map.getKnownCommands();
         known.put("w2nsmp:" + name, command);
         this.releasePlainLabel(name, command);
         this.registerAliases(aliases, command);
      }
   }

   private void releasePlainLabel(String name, Command ours) {
      Map<String, Command> known = Bukkit.getCommandMap().getKnownCommands();
      Command other = this.findForeignCommand(name);
      ConfigManager.PlainLabelMode mode = this.plugin.config().plainLabelMode();
      boolean stealAllowed = this.plugin.config().mayClaimLabel(name);
      if (mode == ConfigManager.PlainLabelMode.NAMESPACED || other != null && !stealAllowed) {
         if (other != null) {
            known.put(name, other);
         }

         this.plugin
            .getLogger()
            .info(
               "Command /"
                  + name
                  + ": label polos tidak diambil alih ("
                  + mode.name().toLowerCase(Locale.ROOT)
                  + (other != null && !stealAllowed ? ", daftar never-claim" : "")
                  + "); versi W2NSMP dipakai lewat /w2nsmp:"
                  + name
                  + "."
            );
      } else if (other != null && mode == ConfigManager.PlainLabelMode.AUTO) {
         known.put(name, other);
         this.plugin
            .getLogger()
            .info(
               "Command /"
                  + name
                  + " sudah dipakai plugin "
                  + this.describe(other)
                  + " -> label polos dibiarkan milik "
                  + this.describe(other)
                  + "; versi W2NSMP dipakai lewat /w2nsmp:"
                  + name
                  + " atau alias khas W2NSMP."
            );
      } else {
         known.put(name, ours);
         if (other == null) {
            this.plugin.getLogger().info("Command /" + name + " didaftarkan (label polos bebas): /" + name + " dan /w2nsmp:" + name + " aktif.");
         } else {
            this.claimedLabels.put(name, this.describe(other));
            this.plugin
               .getLogger()
               .warning(
                  "Command /"
                     + name
                     + " diambil alih dari plugin "
                     + this.describe(other)
                     + " (commands.plain-labels: "
                     + mode.name().toLowerCase(Locale.ROOT)
                     + ") -> pemain memakai /"
                     + name
                     + "; command "
                     + this.describe(other)
                     + " tetap bisa dipakai lewat bentuk namespaced (mis. /essentials:"
                     + name
                     + ")."
               );
         }
      }
   }

   private void registerAliases(List<String> aliases, Command ours) {
      if (!aliases.isEmpty()) {
         Map<String, Command> known = Bukkit.getCommandMap().getKnownCommands();

         for (String rawAlias : aliases) {
            String alias = rawAlias.toLowerCase(Locale.ROOT);
            Command other = this.findForeignCommand(alias);
            boolean claimAlias = other != null
               && this.plugin.config().mayClaimLabel(alias)
               && this.plugin.config().plainLabelMode() == ConfigManager.PlainLabelMode.PREFER;
            if (other != null && !claimAlias) {
               this.plugin
                  .getLogger()
                  .info(
                     "Alias /"
                        + alias
                        + " sudah dipakai plugin "
                        + this.describe(other)
                        + " -> alias dilewati (command tetap bisa dipakai lewat /w2nsmp:"
                        + ours.getName()
                        + ")."
                  );
            } else {
               if (other != null) {
                  this.claimedLabels.put(alias, this.describe(other));
               }

               known.put(alias, ours);
               known.put("w2nsmp:" + alias, ours);
            }
         }
      }
   }

   private Command findForeignCommand(String name) {
      for (Command candidate : Bukkit.getCommandMap().getKnownCommands().values()) {
         if (candidate instanceof PluginCommand pluginCommand
            && !pluginCommand.getPlugin().equals(this.plugin)
            && pluginCommand.getName().equalsIgnoreCase(name)) {
            return pluginCommand;
         }
      }

      return null;
   }

   private String describe(Command command) {
      return command instanceof PluginCommand pluginCommand ? pluginCommand.getPlugin().getName() : command.getClass().getSimpleName();
   }

   public record Registered(String label, String permission, String descriptionKey) {
      public Registered {
      }
   }
}
