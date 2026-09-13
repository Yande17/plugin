package me.w2n.w2nsmp.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.auction.AuctionListing;
import me.w2n.w2nsmp.auction.AuctionManager;
import me.w2n.w2nsmp.bounty.BountyEntry;
import me.w2n.w2nsmp.bounty.BountyService;
import me.w2n.w2nsmp.config.Messages;
import me.w2n.w2nsmp.economy.DynamicEconomy;
import me.w2n.w2nsmp.economy.MoneyFormatter;
import me.w2n.w2nsmp.hook.BedrockHook;
import me.w2n.w2nsmp.manager.CommandManager;
import me.w2n.w2nsmp.manager.PluginInfo;
import me.w2n.w2nsmp.stats.PlayerStats;
import me.w2n.w2nsmp.stats.StatType;
import me.w2n.w2nsmp.stats.StatisticsService;
import me.w2n.w2nsmp.stats.TopCategory;
import me.w2n.w2nsmp.utility.CompatAudit;
import me.w2n.w2nsmp.utility.EnvironmentReport;
import me.w2n.w2nsmp.utility.Players;
import me.w2n.w2nsmp.utility.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class W2NSMPCommand implements TabExecutor {
   private static final String PERM_ADMIN = "w2nsmp.admin";
   private static final String PERM_RELOAD = "w2nsmp.reload";
   private static final String PERM_VERSION = "w2nsmp.version";
   private static final String PERM_DEBUG = "w2nsmp.debug";
   private static final String PERM_SETPRICE = "w2nsmp.setprice";
   private static final String PERM_GETPRICE = "w2nsmp.getprice";
   private static final String PERM_GIVEHOME = "w2nsmp.givehome";
   private static final String PERM_REMOVEHOME = "w2nsmp.removehome";
   private static final String PERM_COMBAT = "w2nsmp.combat.admin";
   private static final String PERM_AUCTION = "w2nsmp.auction.admin";
   private static final String PERM_WORTH = "w2nsmp.worth.admin";
   private static final String PERM_STATS = "w2nsmp.stats.admin";
   private static final String PERM_COMPAT = "w2nsmp.compat";
   private static final String PERM_SETTINGS = "w2nsmp.settings.admin";
   private static final String PERM_ECONOMY = "w2nsmp.economy.admin";
   private static final String PERM_BOUNTY = "w2nsmp.bounty.admin";
   private static final List<String> SUBCOMMANDS = List.of(
      "help",
      "version",
      "debug",
      "reload",
      "setprice",
      "getprice",
      "givehome",
      "removehome",
      "combatinfo",
      "untag",
      "auctioninfo",
      "auctionexpire",
      "auctionremove",
      "worthinfo",
      "worthclear",
      "statsinfo",
      "statsreset",
      "compat",
      "settinginfo",
      "economy",
      "bounty"
   );
   private static final int TAB_SUGGESTION_LIMIT = 40;
   private final W2NSMP plugin;

   public W2NSMPCommand(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (args.length == 0) {
         this.sendOverview(sender, label);
         return true;
      }

      switch (args[0].toLowerCase(Locale.ROOT)) {
         case "help":
            if (this.require(sender, "w2nsmp.use")) {
               this.sendHelp(sender, label);
            }
            break;
         case "version":
            if (this.require(sender, "w2nsmp.version")) {
               this.sendVersion(sender);
            }
            break;
         case "debug":
            if (this.require(sender, "w2nsmp.debug")) {
               this.sendDebug(sender);
            }
            break;
         case "reload":
            if (this.require(sender, "w2nsmp.reload")) {
               this.reload(sender);
            }
            break;
         case "setprice":
            if (this.require(sender, "w2nsmp.setprice")) {
               this.setPrice(sender, label, args);
            }
            break;
         case "getprice":
            if (this.require(sender, "w2nsmp.getprice")) {
               this.getPrice(sender, label, args);
            }
            break;
         case "givehome":
            if (this.require(sender, "w2nsmp.givehome")) {
               this.setHomeSlot(sender, label, args, true);
            }
            break;
         case "removehome":
            if (this.require(sender, "w2nsmp.removehome")) {
               this.setHomeSlot(sender, label, args, false);
            }
            break;
         case "combatinfo":
            if (this.require(sender, "w2nsmp.combat.admin")) {
               this.showCombatInfo(sender, label, args);
            }
            break;
         case "untag":
            if (this.require(sender, "w2nsmp.combat.admin")) {
               this.untagPlayer(sender, label, args);
            }
            break;
         case "auctioninfo":
            if (this.require(sender, "w2nsmp.auction.admin")) {
               this.showAuctionInfo(sender, label, args);
            }
            break;
         case "auctionexpire":
            if (this.require(sender, "w2nsmp.auction.admin")) {
               this.expireAuctions(sender, label, args);
            }
            break;
         case "auctionremove":
            if (this.require(sender, "w2nsmp.auction.admin")) {
               this.removeAuction(sender, label, args);
            }
            break;
         case "worthinfo":
            if (this.require(sender, "w2nsmp.worth.admin")) {
               this.showWorthInfo(sender, label, args);
            }
            break;
         case "worthclear":
            if (this.require(sender, "w2nsmp.worth.admin")) {
               this.clearWorth(sender, label, args);
            }
            break;
         case "statsinfo":
            if (this.require(sender, "w2nsmp.stats.admin")) {
               this.showStatsInfo(sender, label, args);
            }
            break;
         case "statsreset":
            if (this.require(sender, "w2nsmp.stats.admin")) {
               this.resetStats(sender, label, args);
            }
            break;
         case "compat":
            if (this.require(sender, "w2nsmp.compat")) {
               this.showCompat(sender, label, args);
            }
            break;
         case "economy":
            if (this.require(sender, "w2nsmp.economy.admin")) {
               this.sendEconomyInfo(sender);
            }
            break;
         case "bounty":
            if (this.require(sender, "w2nsmp.bounty.admin")) {
               this.sendBountyAdmin(sender, label, args);
            }
            break;
         case "settinginfo":
            if (this.require(sender, "w2nsmp.settings.admin")) {
               this.sendSettingInfo(sender, args);
            }
            break;
         case "commands":
            if (this.require(sender, "w2nsmp.compat")) {
               this.showCommands(sender, label, args);
            }
            break;
         default:
            this.plugin.messages().send(sender, "command.unknown-subcommand", "subcommand", args[0], "label", label);
      }

      return true;
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (args.length == 1) {
         String typed = args[0].toLowerCase(Locale.ROOT);
         return SUBCOMMANDS.stream().filter(sub -> sub.startsWith(typed)).filter(sub -> {
            return switch (sub) {
               case "version" -> sender.hasPermission("w2nsmp.version");
               case "debug" -> sender.hasPermission("w2nsmp.debug");
               case "reload" -> sender.hasPermission("w2nsmp.reload");
               case "setprice" -> sender.hasPermission("w2nsmp.setprice");
               case "getprice" -> sender.hasPermission("w2nsmp.getprice");
               case "givehome" -> sender.hasPermission("w2nsmp.givehome");
               case "removehome" -> sender.hasPermission("w2nsmp.removehome");
               case "combatinfo", "untag" -> sender.hasPermission("w2nsmp.combat.admin");
               case "auctioninfo", "auctionexpire", "auctionremove" -> sender.hasPermission("w2nsmp.auction.admin");
               case "worthinfo", "worthclear" -> sender.hasPermission("w2nsmp.worth.admin");
               case "statsinfo", "statsreset" -> sender.hasPermission("w2nsmp.stats.admin");
               case "compat", "commands" -> sender.hasPermission("w2nsmp.compat");
               case "settinginfo" -> sender.hasPermission("w2nsmp.settings.admin");
               case "economy" -> sender.hasPermission("w2nsmp.economy.admin");
               case "bounty" -> sender.hasPermission("w2nsmp.bounty.admin");
               default -> true;
            };
         }).toList();
      }

      if (args.length >= 2 && args[0].equalsIgnoreCase("bounty") && sender.hasPermission("w2nsmp.bounty.admin")) {
         if (args.length == 2) {
            return Players.filter(List.of("info", "set", "clear", "clearall"), args[1]);
         }

         if (args.length == 3 && !args[1].equalsIgnoreCase("clearall")) {
            List<String> names = new ArrayList<>();

            for (Player online : Bukkit.getOnlinePlayers()) {
               names.add(online.getName());
            }

            return Players.filter(names, args[2]);
         } else if (args.length == 4 && args[1].equalsIgnoreCase("set")) {
            List<String> presets = new ArrayList<>();

            for (long preset : this.plugin.config().bountyPresets()) {
               presets.add(Long.toString(preset));
            }

            return Players.filter(presets, args[3]);
         } else {
            return List.of();
         }
      } else if (args.length != 2 || !args[0].equalsIgnoreCase("setprice") && !args[0].equalsIgnoreCase("getprice")) {
         if (args.length == 2 && args[0].equalsIgnoreCase("untag") && sender.hasPermission("w2nsmp.combat.admin")) {
            return this.playerSuggestions(args[1], false);
         } else {
            return args.length == 2
                  && (args[0].equalsIgnoreCase("statsinfo") || args[0].equalsIgnoreCase("statsreset"))
                  && sender.hasPermission("w2nsmp.stats.admin")
               ? this.playerSuggestions(args[1], args[0].equalsIgnoreCase("statsreset"))
               : List.of();
         }
      } else {
         if (!sender.hasPermission("w2nsmp.setprice") && !sender.hasPermission("w2nsmp.getprice")) {
            return List.of();
         }

         String typed = args[1].toUpperCase(Locale.ROOT);
         return this.plugin.sell().prices().materials().stream().map(Enum::name).filter(name -> name.startsWith(typed)).sorted().limit(40L).toList();
      }
   }

   private List<String> playerSuggestions(String typedRaw, boolean withAll) {
      String typed = typedRaw == null ? "" : typedRaw.toLowerCase(Locale.ROOT);
      List<String> names = new ArrayList<>(
         Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(name -> name.toLowerCase(Locale.ROOT).startsWith(typed)).sorted().toList()
      );
      if (withAll && "semua".startsWith(typed)) {
         names.add("semua");
      }

      return names;
   }

   private void sendOverview(CommandSender sender, String label) {
      if (this.require(sender, "w2nsmp.use")) {
         Messages messages = this.plugin.messages();
         String economyState = this.plugin.economy().isEnabled()
            ? "&aaktif &7(" + this.plugin.economy().providerName() + ")"
            : "&cnono &7(tanpa Vault/provider)";
         Text.send(sender, messages.prefix() + "&bW2NSMP &7v" + PluginInfo.version() + " &8| &7Paper API &f26.2");
         Text.send(sender, "&7Ekonomi: " + economyState);
         Text.send(sender, "&7Gunakan &f/" + label + " help &7untuk daftar perintah.");
      }
   }

   private void sendEconomyInfo(CommandSender sender) {
      DynamicEconomy dynamic = this.plugin.dynamic();
      Messages messages = this.plugin.messages();
      Text.send(sender, "&8&m                                        ");
      if (dynamic == null) {
         messages.send(sender, "admin.economy-off");
         Text.send(sender, "&8&m                                        ");
      } else {
         messages.send(sender, "admin.economy-header");
         messages.send(
            sender,
            "admin.economy-status",
            "status",
            messages.raw(dynamic.enabled() ? "admin.economy-on" : "admin.economy-off"),
            "interval",
            Integer.toString(dynamic.intervalSeconds()),
            "file",
            dynamic.file().getName()
         );
         messages.send(
            sender,
            "admin.economy-range",
            "min",
            economyMultiplier(dynamic.minMultiplier()),
            "max",
            economyMultiplier(dynamic.maxMultiplier()),
            "impact",
            economyMultiplier(dynamic.impactStrength()),
            "recovery",
            economyMultiplier(dynamic.recoveryRate())
         );
         Map<String, Double> moved = dynamic.mostMoved(5);
         if (moved.isEmpty()) {
            messages.send(sender, "admin.economy-none");
         } else {
            for (Entry<String, Double> entry : moved.entrySet()) {
               Material material = Material.matchMaterial(entry.getKey());
               messages.send(
                  sender,
                  "admin.economy-entry",
                  "item",
                  entry.getKey(),
                  "multiplier",
                  economyMultiplier(entry.getValue()),
                  "price",
                  material == null ? "-" : this.plugin.economy().formatExact(dynamic.currentPrice(material)),
                  "base",
                  material == null ? "-" : this.plugin.economy().formatExact(this.plugin.sell().prices().price(material))
               );
            }

            int tracked = dynamic.tracked();
            if (tracked > moved.size()) {
               messages.send(sender, "admin.economy-more", "count", Integer.toString(tracked - moved.size()));
            }
         }

         Text.send(sender, "&8&m                                        ");
      }
   }

   private static String economyMultiplier(double value) {
      return String.format(Locale.ROOT, "%.2f", value) + "x";
   }

   private void sendBountyAdmin(CommandSender sender, String label, String[] args) {
      BountyService bounty = this.plugin.bounty();
      if (bounty == null) {
         this.plugin.messages().send(sender, "admin.bounty-disabled");
      } else {
         String usage = "&f/" + label + " bounty [info <pemain>|set <pemain> <jumlah>|clear <pemain>|clearall]";
         String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
         switch (action) {
            case "":
               this.plugin.messages().send(sender, "admin.bounty-header");
               this.plugin
                  .messages()
                  .send(
                     sender,
                     "admin.bounty-status",
                     "status",
                     this.plugin.messages().raw(bounty.enabled() ? "admin.bounty-on" : "admin.bounty-off"),
                     "players",
                     Integer.toString(bounty.count()),
                     "total",
                     this.plugin.economy().formatExact(bounty.total()),
                     "file",
                     bounty.file().getName()
                  );
               this.plugin
                  .messages()
                  .send(
                     sender,
                     "admin.bounty-limits",
                     "min",
                     this.plugin.economy().formatExact(this.plugin.config().bountyMinimum()),
                     "max",
                     this.plugin.economy().formatExact(this.plugin.config().bountyMaximum()),
                     "tax",
                     Double.toString(this.plugin.config().bountyTaxPercent())
                  );

               for (BountyEntry entry : bounty.top()) {
                  this.plugin
                     .messages()
                     .send(
                        sender,
                        "admin.bounty-entry",
                        "rank",
                        Integer.toString(bounty.top().indexOf(entry) + 1),
                        "player",
                        entry.displayName(entry.uniqueId().toString()),
                        "target",
                        entry.displayName(entry.uniqueId().toString()),
                        "amount",
                        this.plugin.economy().formatExact(entry.amount())
                     );
               }
               break;
            case "info": {
               if (args.length < 3) {
                  this.plugin.messages().send(sender, "admin.bounty-usage", "usage", usage);
                  return;
               }

               OfflinePlayer target = Players.findOnlineOrCached(args[2]);
               if (target == null) {
                  this.plugin.messages().send(sender, "admin.bounty-unknown", "player", args[2]);
                  return;
               }

               String name = target.getName() == null ? args[2] : target.getName();
               this.plugin
                  .messages()
                  .send(
                     sender,
                     "admin.bounty-target",
                     "player",
                     name,
                     "target",
                     name,
                     "amount",
                     this.plugin.economy().formatExact(bounty.bounty(target.getUniqueId()))
                  );
               break;
            }
            case "set":
            case "clear": {
               if (args.length < 3 || action.equals("set") && args.length < 4) {
                  this.plugin.messages().send(sender, "admin.bounty-usage", "usage", usage);
                  return;
               }

               OfflinePlayer target = Players.findOnlineOrCached(args[2]);
               if (target == null) {
                  this.plugin.messages().send(sender, "admin.bounty-unknown", "player", args[2]);
                  return;
               }

               long amount = 0L;
               if (action.equals("set")) {
                  amount = MoneyFormatter.parseLongAmount(args[3]);
                  if (amount < 0L) {
                     this.plugin.messages().send(sender, "admin.bounty-invalid-amount", "input", args[3]);
                     return;
                  }
               }

               String name = target.getName() == null ? args[2] : target.getName();
               bounty.setBounty(target.getUniqueId(), amount);
               bounty.saveNow();
               this.plugin
                  .messages()
                  .send(
                     sender,
                     "admin.bounty-set",
                     "player",
                     name,
                     "target",
                     name,
                     "amount",
                     this.plugin.economy().formatExact(bounty.bounty(target.getUniqueId()))
                  );
               break;
            }
            case "clearall":
               int cleared = bounty.clearAll();
               bounty.saveNow();
               this.plugin.messages().send(sender, "admin.bounty-cleared-all", "count", Integer.toString(cleared));
               break;
            default:
               this.plugin.messages().send(sender, "admin.bounty-usage", "usage", usage);
         }
      }
   }

   private void sendSettingInfo(CommandSender sender, String[] args) {
      Text.send(sender, "&8&m                                        ");
      Text.send(sender, "&bPreferensi pemain (PHASE 11)");
      if (this.plugin.settings() != null && this.plugin.nametag() != null) {
         Text.send(sender, "&7Berkas: &f" + this.plugin.settings().file().getPath() + " &8(" + this.plugin.settings().playerCount() + " pemain)");
         Text.send(sender, "&7Belum ditulis &8(dirty)&7: &f" + this.plugin.settings().isDirty());
         Text.send(sender, "&7Bunyi GUI: &f" + this.plugin.config().soundsEnabled() + " &8(bawaan pemain: &f" + this.plugin.config().soundsDefault() + "&8)");
         Text.send(
            sender,
            "&7Nametag uang: &f"
               + this.plugin.config().nametagMoneyEnabled()
               + " &8| format: &f"
               + this.plugin.config().nametagMoneyFormat()
               + " &8| diatur: &f"
               + this.plugin.nametag().activeCount()
               + " pemain"
         );
         Text.send(sender, "&7Task nametag: &f" + this.plugin.nametag().taskRunning() + " &8| salam tim: &fw2nmoney");
         Text.send(
            sender,
            "&7Baris sidebar per pemain: &f"
               + this.plugin.scoreboard().hiddenLinePlayers()
               + " &8| sidebar dimatikan: &f"
               + this.plugin.scoreboard().hiddenCount()
         );
         if (this.plugin.config().guiConfigEnabled()) {
            Text.send(sender, "&7Berkas GUI: &f" + String.join("&8, &f", this.plugin.guiConfigs().files()));
         } else {
            Text.send(sender, "&7Berkas GUI: &cdiabaikan &8(gui.enabled: false)");
         }

         if (args.length > 1 && sender instanceof Player) {
            Text.send(sender, "&7Rincian satu pemain: &f/profile <pemain> chat");
         }

         Text.send(sender, "&8&m                                        ");
      } else {
         Text.send(sender, "&7Layanan preferensi belum siap.");
      }
   }

   private void sendHelp(CommandSender sender, String label) {
      Messages messages = this.plugin.messages();
      Text.send(sender, "&8&m                                        ");
      Text.send(sender, messages.raw("help.header"));

      for (CommandManager.Registered entry : this.plugin.commandManager().registered()) {
         if (entry.descriptionKey() != null && (entry.permission() == null || sender.hasPermission(entry.permission()))) {
            Text.send(sender, messages.raw(entry.descriptionKey()));
         }
      }

      if (sender.hasPermission("w2nsmp.version") || sender.hasPermission("w2nsmp.debug") || sender.hasPermission("w2nsmp.reload")) {
         Text.send(sender, messages.raw("help.admin-header"));
         if (sender.hasPermission("w2nsmp.version")) {
            Text.send(sender, "&f/" + label + " version &7- versi plugin & lingkungan server");
         }

         if (sender.hasPermission("w2nsmp.debug")) {
            Text.send(sender, "&f/" + label + " debug &7- diagnostik & status dependency");
         }

         if (sender.hasPermission("w2nsmp.reload")) {
            Text.send(sender, "&f/" + label + " reload &7- muat ulang config, messages & prices");
         }

         if (sender.hasPermission("w2nsmp.setprice")) {
            Text.send(sender, "&f/" + label + " setprice <item> <harga> &7- ubah harga jual");
         }

         if (sender.hasPermission("w2nsmp.getprice")) {
            Text.send(sender, "&f/" + label + " getprice <item> &7- lihat harga jual");
         }

         if (sender.hasPermission("w2nsmp.givehome")) {
            Text.send(sender, "&f/" + label + " givehome <pemain> <slot> &7- buka slot home pemain");
         }

         if (sender.hasPermission("w2nsmp.removehome")) {
            Text.send(sender, "&f/" + label + " removehome <pemain> <slot> &7- kunci kembali slot home");
         }

         if (sender.hasPermission("w2nsmp.combat.admin")) {
            Text.send(sender, "&f/" + label + " combatinfo [pemain] &7- status combat tag");
            Text.send(sender, "&f/" + label + " untag <pemain> &7- hapus combat tag pemain");
         }

         if (sender.hasPermission("w2nsmp.worth.admin")) {
            Text.send(sender, "&f/" + label + " worthinfo &7- status fitur harga item (lore harga)");
            Text.send(sender, "&f/" + label + " worthclear <pemain|semua> &7- lepas lore harga");
         }

         if (sender.hasPermission("w2nsmp.stats.admin")) {
            Text.send(sender, "&f/" + label + " statsinfo [pemain] &7- status statistik / data pemain");
            Text.send(sender, "&f/" + label + " statsreset <pemain|semua> &7- bersihkan statistik");
         }

         if (sender.hasPermission("w2nsmp.compat")) {
            Text.send(sender, "&f/" + label + " compat &7- kompatibilitas Java/Bedrock + audit GUI");
            Text.send(sender, "&f/" + label + " commands &7- pemilik label command (/sell, /home, ...)");
         }

         if (sender.hasPermission("w2nsmp.settings.admin")) {
            Text.send(sender, "&f/" + label + " settinginfo [pemain] &7- preferensi pemain, nametag uang & berkas gui/*.yml");
         }

         if (sender.hasPermission("w2nsmp.economy.admin")) {
            Text.send(sender, "&f/" + label + " economy &7- status ekonomi dinamis (pengali harga per item)");
         }

         if (sender.hasPermission("w2nsmp.bounty.admin")) {
            Text.send(sender, "&f/" + label + " bounty [set|clear|clearall] &7- kelola bounty");
         }

         if (sender.hasPermission("w2nsmp.auction.admin")) {
            Text.send(sender, "&f/" + label + " auctioninfo &7- status auction house");
            Text.send(sender, "&f/" + label + " auctionexpire &7- pindahkan listing kedaluwarsa ke kotak");
            Text.send(sender, "&f/" + label + " auctionremove <id> &7- hapus listing tertentu");
         }
      }

      Text.send(sender, "&8&m                                        ");
      Text.send(sender, messages.raw("help.footer", "label", label));
   }

   private void showCommands(CommandSender sender, String label, String[] args) {
      if (args.length != 1) {
         this.plugin.messages().send(sender, "admin.commands-usage", "usage", "/" + label + " commands");
      } else {
         CommandManager manager = this.plugin.commandManager();
         Text.send(sender, this.plugin.messages().prefix() + "&bKepemilikan label command &7(PHASE 11)");
         Text.send(
            sender,
            "&7Mode     : &f" + this.plugin.config().plainLabelMode().name().toLowerCase(Locale.ROOT) + " &7(commands.plain-labels, diubah di &fconfig.yml&7)"
         );
         List<String> ours = new ArrayList<>();

         for (CommandManager.Registered entry : manager.registered()) {
            ours.add("/" + entry.label());
         }

         Text.send(sender, "&7Dipakai W2NSMP &7(" + ours.size() + "): &f" + String.join("&7, &f", ours));
         Map<String, String> claimed = manager.claimedLabels();
         if (claimed.isEmpty()) {
            Text.send(sender, "&7Label polos plugin lain: &ftidak ada yang diambil alih");
         } else {
            Text.send(sender, "&7Diambil alih dari plugin lain &7(" + claimed.size() + " label):");

            for (String owner : manager.pluginsLosingLabels()) {
               List<String> taken = manager.labelsTakenFrom(owner);
               Text.send(sender, "  &f" + owner + " &8-> &f/" + String.join("&8, &f/", taken));
               boolean complete = true;

               for (String name : taken) {
                  if (Bukkit.getCommandMap().getCommand(owner.toLowerCase(Locale.ROOT) + ":" + name) == null) {
                     complete = false;
                  }
               }

               Text.send(
                  sender,
                  "    &7namespace: &f/"
                     + owner.toLowerCase(Locale.ROOT)
                     + ":<nama> &7- &f"
                     + (complete ? "&asemua tetap bisa dipakai" : "&esebagian tidak punya bentuk namespaced")
               );
               if (owner.toLowerCase(Locale.ROOT).contains("essential")) {
                  Text.send(sender, "    &7Saran: tambahkan ke &fdisabled-commands &7di &fplugins/Essentials/config.yml");
                  Text.send(sender, "    &8disabled-commands: [" + String.join(", ", taken) + "]");
               }
            }

            Text.send(sender, "&7Namespaced W2NSMP selalu tersedia: &f/w2nsmp:<nama> &7(mis. &f/w2nsmp:sell&7)");
         }
      }
   }

   private void reload(CommandSender sender) {
      long startedAt = System.currentTimeMillis();

      try {
         this.plugin.reloadAll();
         this.plugin.messages().send(sender, "command.reload-success");
      } catch (RuntimeException exception) {
         this.plugin.getLogger().warning("Reload gagal: " + exception);
         this.plugin.messages().send(sender, "command.reload-failed");
         return;
      }

      this.plugin.debug("Reload selesai dalam " + (System.currentTimeMillis() - startedAt) + " ms.");
   }

   private void setPrice(CommandSender sender, String label, String[] args) {
      Messages messages = this.plugin.messages();
      if (args.length != 3) {
         messages.send(sender, "admin.setprice-usage", "usage", "/" + label + " setprice <item> <harga>");
      } else {
         Material material = Material.matchMaterial(args[1]);
         if (material != null && material.isItem()) {
            long parsedPrice = MoneyFormatter.parseLongAmount(args[2]);
            if (parsedPrice >= 0L && parsedPrice <= 2147483647L) {
               int price = (int)parsedPrice;
               int oldPrice = this.plugin.sell().prices().price(material);
               if (!this.plugin.sell().prices().setPrice(material, price)) {
                  messages.send(sender, "admin.setprice-failed");
               } else if (price == 0) {
                  messages.send(sender, "admin.setprice-removed", "item", material.name(), "old", this.plugin.economy().formatExact(oldPrice));
                  this.plugin.getLogger().info(sender.getName() + " menghapus harga " + material.name() + " (sebelumnya " + oldPrice + ")");
               } else {
                  messages.send(
                     sender,
                     "admin.setprice-success",
                     "item",
                     material.name(),
                     "old",
                     this.plugin.economy().formatExact(oldPrice),
                     "new",
                     this.plugin.economy().formatExact(price)
                  );
                  this.plugin.getLogger().info(sender.getName() + " mengubah harga " + material.name() + ": " + oldPrice + " -> " + price);
               }
            } else {
               messages.send(sender, "admin.invalid-price");
            }
         } else {
            messages.send(sender, "admin.invalid-item", "item", args[1]);
         }
      }
   }

   private void getPrice(CommandSender sender, String label, String[] args) {
      Messages messages = this.plugin.messages();
      if (args.length != 2) {
         messages.send(sender, "admin.getprice-usage", "usage", "/" + label + " getprice <item>");
      } else {
         Material material = Material.matchMaterial(args[1]);
         if (material != null && material.isItem()) {
            int price = this.plugin.sell().prices().price(material);
            if (price <= 0) {
               messages.send(sender, "admin.getprice-none", "item", material.name());
            } else {
               messages.send(sender, "admin.getprice-result", "item", material.name(), "price", this.plugin.economy().formatExact(price));
            }
         } else {
            messages.send(sender, "admin.invalid-item", "item", args[1]);
         }
      }
   }

   private void setHomeSlot(CommandSender sender, String label, String[] args, boolean unlock) {
      Messages messages = this.plugin.messages();
      String usageKey = unlock ? "admin.givehome-usage" : "admin.removehome-usage";
      String subcommand = unlock ? "givehome" : "removehome";
      if (args.length != 3) {
         messages.send(sender, usageKey, "usage", "/" + label + " " + subcommand + " <pemain> <slot>");
      } else {
         OfflinePlayer target = Players.findOnlineOrCached(args[1]);
         if (target == null) {
            messages.send(sender, "admin.player-not-found", "player", args[1]);
         } else {
            int slotNumber;
            try {
               slotNumber = Integer.parseInt(args[2]);
            } catch (NumberFormatException exception) {
               messages.send(sender, usageKey, "usage", "/" + label + " " + subcommand + " <pemain> <slot>");
               return;
            }

            int slot = slotNumber - 1;
            if (slot >= 0 && slot < this.plugin.homes().maxSlots()) {
               String name = target.getName() == null ? args[1] : target.getName();
               boolean changed = unlock
                  ? this.plugin.homes().adminUnlock(target.getUniqueId(), name, slot)
                  : this.plugin.homes().adminLock(target.getUniqueId(), slot);
               if (!changed) {
                  messages.send(sender, unlock ? "admin.givehome-failed" : "admin.removehome-failed", "player", name, "slot", Integer.toString(slotNumber));
               } else {
                  messages.send(sender, unlock ? "admin.givehome-success" : "admin.removehome-success", "player", name, "slot", Integer.toString(slotNumber));
                  this.plugin.getLogger().info(sender.getName() + (unlock ? " membuka" : " mengunci") + " slot home " + slotNumber + " untuk " + name + ".");
               }
            } else {
               messages.send(sender, "admin.home-slot-invalid", "max", Integer.toString(this.plugin.homes().maxSlots()));
            }
         }
      }
   }

   private void showCombatInfo(CommandSender sender, String label, String[] args) {
      Messages messages = this.plugin.messages();
      if (args.length > 2) {
         messages.send(sender, "admin.combatinfo-usage", "usage", "/" + label + " combatinfo [pemain]");
      } else if (args.length == 2) {
         OfflinePlayer target = Players.findOnlineOrCached(args[1]);
         if (target == null) {
            messages.send(sender, "admin.player-not-found", "player", args[1]);
         } else {
            Player online = target.getPlayer();
            if (online == null) {
               messages.send(sender, "admin.combat-offline", "player", Players.displayName(target, args[1]));
            } else {
               String name = online.getName();
               if (!this.plugin.combat().isTagged(online)) {
                  messages.send(sender, "admin.combat-target-clear", "player", name);
               } else {
                  String attacker = this.plugin.combat().attackerName(online);
                  messages.send(
                     sender,
                     "admin.combat-target",
                     "player",
                     name,
                     "seconds",
                     Integer.toString(this.plugin.combat().remainingSeconds(online)),
                     "attacker",
                     attacker == null ? "-" : attacker
                  );
               }
            }
         }
      } else {
         Text.send(sender, messages.prefix() + "&bCombat &7(PHASE 6)");
         Text.send(
            sender,
            "&7Status   : "
               + (this.plugin.combat().enabled() ? "&aaktif" : "&cnono")
               + " &8| &7durasi: &f"
               + this.plugin.config().combatDurationSeconds()
               + " detik &8| &7tertag: &f"
               + this.plugin.combat().taggedCount()
               + " &8| &7hukuman dijalankan: &f"
               + this.plugin.combat().punishedCount()
         );
         if (this.plugin.combat().taggedCount() == 0) {
            Text.send(sender, "&7Tidak ada pemain yang sedang tertag.");
         } else {
            String remaining = Bukkit.getOnlinePlayers()
               .stream()
               .filter(this.plugin.combat()::isTagged)
               .map(player -> player.getName() + " (" + this.plugin.combat().remainingSeconds(player) + "s)")
               .sorted()
               .collect(Collectors.joining(", "));
            Text.send(sender, "&7Tertag  : &f" + (remaining.isEmpty() ? "-" : remaining));
         }

         Text.send(sender, "&7Gunakan &f/" + label + " untag <pemain> &7untuk menghapus tag.");
      }
   }

   private void untagPlayer(CommandSender sender, String label, String[] args) {
      Messages messages = this.plugin.messages();
      if (args.length != 2) {
         messages.send(sender, "admin.untag-usage", "usage", "/" + label + " untag <pemain>");
      } else {
         OfflinePlayer target = Players.findOnlineOrCached(args[1]);
         if (target == null) {
            messages.send(sender, "admin.player-not-found", "player", args[1]);
         } else {
            Player online = target.getPlayer();
            if (online == null) {
               messages.send(sender, "admin.combat-offline", "player", Players.displayName(target, args[1]));
            } else {
               String name = online.getName();
               if (!this.plugin.combat().untag(online)) {
                  messages.send(sender, "admin.untag-none", "player", name);
               } else {
                  messages.send(sender, "admin.untag-success", "player", name);
                  messages.send(online, "combat.untagged", "player", sender.getName());
                  this.plugin.getLogger().info(sender.getName() + " menghapus combat tag " + name + ".");
               }
            }
         }
      }
   }

   private void showAuctionInfo(CommandSender sender, String label, String[] args) {
      if (args.length != 1) {
         this.plugin.messages().send(sender, "admin.auctioninfo-usage", "usage", "/" + label + " auctioninfo");
      } else {
         Text.send(sender, this.plugin.messages().prefix() + "&bAuction House &7(PHASE 7)");
         Text.send(
            sender,
            "&7Status   : "
               + (this.plugin.config().auctionEnabled() ? "&aaktif" : "&cnono")
               + " &8| &7listing aktif: &f"
               + this.plugin.auction().listingCount()
               + " &8| &7kedaluwarsa: &f"
               + this.plugin.auction().expiredCount()
               + " &8| &7item di kotak: &f"
               + this.plugin.auction().mailboxTotal()
         );
         Text.send(
            sender,
            "&7Aturan   : &7pajak &f"
               + this.plugin.config().auctionTaxPercent()
               + "% &8| &7harga &f"
               + this.plugin.config().auctionMinPrice()
               + "-"
               + this.plugin.config().auctionMaxPrice()
               + " &8| &7kedaluwarsa &f"
               + this.plugin.config().auctionExpirationHours()
               + " jam &8| &7maks/pemain &f"
               + this.plugin.config().auctionMaxListings()
         );
         Text.send(sender, "&7File     : &fplugins/W2NSMP/auctions.yml &8| &7blacklist: &f" + this.plugin.config().auctionBlacklist().size() + " material");
         Text.send(sender, "&7Gunakan &f/" + label + " auctionexpire &7untuk memproses listing kedaluwarsa.");
      }
   }

   private void expireAuctions(CommandSender sender, String label, String[] args) {
      if (args.length > 2) {
         this.plugin.messages().send(sender, "admin.auctionexpire-usage", "usage", "/" + label + " auctionexpire");
      } else {
         boolean forceAll = args.length == 2;
         int moved = forceAll ? this.plugin.auction().forceExpireAll() : this.plugin.auction().sweepExpired();
         Text.send(
            sender,
            this.plugin.messages().prefix() + "&a" + moved + " listing dipindahkan ke kotak penjual" + (forceAll ? " &7(mode paksa: SEMUA listing)" : "")
         );
      }
   }

   private void removeAuction(CommandSender sender, String label, String[] args) {
      if (args.length != 2) {
         this.plugin.messages().send(sender, "admin.auctionremove-usage", "usage", "/" + label + " auctionremove <id>");
      } else {
         int id;
         try {
            id = Integer.parseInt(args[1]);
         } catch (NumberFormatException exception) {
            this.plugin.messages().send(sender, "admin.auctionremove-usage", "usage", "/" + label + " auctionremove <id>");
            return;
         }

         AuctionListing removed = this.plugin.auction().forceRemove(id);
         if (removed == null) {
            this.plugin.messages().send(sender, "admin.auction-remove-failed", "id", Integer.toString(id));
         } else {
            this.plugin
               .messages()
               .send(
                  sender, "admin.auction-removed", "id", Integer.toString(id), "item", AuctionManager.itemName(removed.item()), "seller", removed.sellerName()
               );
            this.plugin.getLogger().info(sender.getName() + " menghapus listing #" + id + " milik " + removed.sellerName() + ".");
         }
      }
   }

   private void showCompat(CommandSender sender, String label, String[] args) {
      if (args.length != 1) {
         this.plugin.messages().send(sender, "admin.compat-usage", "usage", "/" + label + " compat");
      } else {
         BedrockHook bedrock = this.plugin.hooks().bedrock();
         Text.send(sender, this.plugin.messages().prefix() + "&bKompatibilitas Java & Bedrock &7(PHASE 10)");
         Text.send(sender, "&7Geyser   : " + (bedrock.hasGeyser() ? "&a" + bedrock.geyser() : "&ctidak terpasang"));
         Text.send(sender, "&7Floodgate: " + (bedrock.hasFloodgate() ? "&a" + bedrock.floodgate() : "&ctidak terpasang"));
         Text.send(
            sender,
            "&7Status   : "
               + (
                  bedrock.bedrockReady()
                     ? "&apemain Bedrock didukung penuh (autentikasi Floodgate)"
                     : (
                        bedrock.javaOnly()
                           ? "&fserver Java saja &7- GUI W2NSMP tetap siap dipakai Bedrock kapan pun"
                           : "&eSebagian terpasang &7- Geyser & Floodgate sebaiknya dipasang bersamaan"
                     )
               )
         );
         CompatAudit audit = this.plugin.compat();
         if (audit == null) {
            Text.send(sender, "&7Audit    : &ctidak tersedia (plugin belum selesai memuat)");
         } else {
            for (String line : audit.designLines()) {
               Text.send(sender, "&7" + line);
            }

            if (sender instanceof Player player) {
               if (!this.plugin.config().compatibilityLiveAudit()) {
                  Text.send(sender, "&7Audit GUI: &edimatikan di config &f(compatibility.live-audit: false)");
               } else {
                  List<CompatAudit.GuiCheck> checks = audit.audit(player);
                  Text.send(
                     sender,
                     "&7Audit GUI: &f"
                        + audit.summary(checks)
                        + " menu diperiksa &8| "
                        + (audit.bedrockSafe(checks) ? "&aaman untuk Java & Bedrock" : "&cada menu bermasalah")
                  );

                  for (CompatAudit.GuiCheck check : checks) {
                     Text.send(sender, "  " + (check.safe() ? "&aOK" : "&cGAGAL") + " &f/" + check.name() + " &7- " + check.detail());
                  }
               }
            } else {
               Text.send(sender, "&7Audit GUI: &fdijalankan dari dalam game &7(&f/" + label + " compat&7) supaya setiap menu bisa dibuka & diperiksa");
            }
         }
      }
   }

   private void showWorthInfo(CommandSender sender, String label, String[] args) {
      if (args.length != 1) {
         this.plugin.messages().send(sender, "admin.worthinfo-usage", "usage", "/" + label + " worthinfo");
      } else {
         Text.send(sender, this.plugin.messages().prefix() + "&bHarga Item / Worth &7(PHASE 8)");
         Text.send(
            sender,
            "&7Status   : "
               + (this.plugin.config().worthEnabled() ? "&aaktif" : "&cnono")
               + " &8| &7lore inventory: "
               + (this.plugin.config().worthInventoryLore() ? "&aaktif" : "&cnono")
               + " &8| &7lore GUI /sell: "
               + (this.plugin.config().worthSellGui() ? "&aaktif" : "&cnono")
         );
         Text.send(
            sender,
            "&7Harga    : &f"
               + this.plugin.sell().prices().size()
               + " item punya harga &8| &7item tanpa harga: "
               + (this.plugin.config().worthShowUnsellable() ? "&fdiberi keterangan" : "&7tanpa keterangan")
         );
         Text.send(
            sender,
            "&7Kerja    : &flore dipasang &f"
               + this.plugin.worth().appliedTotal()
               + " &7| dilepas &f"
               + this.plugin.worth().strippedTotal()
               + " &7| dilewati &f"
               + this.plugin.worth().skippedTotal()
         );
         Text.send(
            sender,
            "&7Jadwal   : tiap &f"
               + this.plugin.config().worthRefreshSeconds()
               + " &7detik &8| &7pemain dilacak: &f"
               + this.plugin.worth().trackedPlayers()
               + " &8| &7mematikan lore: &f"
               + this.plugin.worth().preferences().disabledCount()
         );
         Text.send(sender, "&7File     : &fplugins/W2NSMP/worth.yml &8| &7lore selalu ditandai di NBT item");
      }
   }

   private void showStatsInfo(CommandSender sender, String label, String[] args) {
      if (args.length > 2) {
         this.plugin.messages().send(sender, "admin.statsinfo-usage", "usage", "/" + label + " statsinfo [pemain]");
      } else if (args.length == 2) {
         this.showPlayerStats(sender, args[1]);
      } else {
         StatisticsService stats = this.plugin.stats();
         boolean active = stats != null && stats.isEnabled();
         this.plugin.messages().send(sender, "admin.statsinfo-header");
         this.plugin
            .messages()
            .send(
               sender,
               "admin.statsinfo-status",
               "status",
               active ? this.plugin.messages().raw("admin.statsinfo-on") : this.plugin.messages().raw("admin.statsinfo-off"),
               "storage",
               stats == null ? "-" : stats.mode().name(),
               "players",
               Integer.toString(stats == null ? 0 : stats.size())
            );
         this.plugin
            .messages()
            .send(
               sender,
               "admin.statsinfo-file",
               "file",
               stats == null ? "-" : stats.file().getName(),
               "autosave",
               this.plugin.config().statisticsAutosaveMinutes() <= 0
                  ? this.plugin.messages().raw("admin.statsinfo-autosave-off")
                  : this.plugin.config().statisticsAutosaveMinutes() + " " + this.plugin.messages().raw("admin.statsinfo-autosave-unit")
            );
         Text.send(
            sender,
            "&7Catat    : blok "
               + (this.plugin.config().statisticsTrackBlocks() ? "&aaktif" : "&cnono")
               + " &8| &7waktu bermain "
               + (this.plugin.config().statisticsTrackPlaytime() ? "&aaktif" : "&cnono")
         );
         Text.send(
            sender,
            "&7Sidebar  : "
               + (this.plugin.config().scoreboardEnabled() ? "&aaktif" : "&cnono")
               + " &8| &7baris: &f"
               + (this.plugin.scoreboard() == null ? 0 : this.plugin.scoreboard().lineCount())
               + " &8| &7pemain mematikannya: &f"
               + (this.plugin.scoreboard() == null ? 0 : this.plugin.scoreboard().hiddenCount())
         );
         Text.send(
            sender,
            "&7Peringkat: &f"
               + TopCategory.keys().size()
               + " kategori &8| &7baris per halaman: &f"
               + this.plugin.config().leaderboardEntriesPerPage()
               + " &8| &7bawaan: &f"
               + this.plugin.config().leaderboardDefaultCategory()
         );
         Text.send(
            sender,
            "&7Cache    : peringkat &f"
               + this.plugin.config().leaderboardCacheSeconds()
               + "s &8| &7maks baris: &f"
               + this.plugin.config().leaderboardMaxEntries()
               + " &8| &7saldo offline: "
               + (this.plugin.config().leaderboardCacheOfflineBalance() ? "&adi-cache" : "&cbaca ulang")
               + " &8| &7umur cache: &f"
               + (stats == null || stats.cacheAgeMillis() < 0L ? "-" : stats.cacheAgeMillis() + "ms")
         );
         Text.send(
            sender,
            "&7Playtime : flush &f"
               + (this.plugin.config().statisticsPlaytimeFlushSeconds() <= 0
                  ? "hanya saat keluar"
                  : this.plugin.config().statisticsPlaytimeFlushSeconds() + " detik")
               + " &8| &7task: "
               + (stats != null && stats.playtimeTaskRunning() ? "&aberjalan" : "&cnono")
               + " &8| &7target nametag: &f"
               + (this.plugin.nametag() == null ? 0 : this.plugin.nametag().targetCount())
               + " scoreboard"
         );
      }
   }

   private void showPlayerStats(CommandSender sender, String name) {
      StatisticsService stats = this.plugin.stats();
      if (stats != null && stats.isEnabled()) {
         UUID uniqueId = stats.findUniqueId(name);
         if (uniqueId == null) {
            this.plugin.messages().send(sender, "admin.player-not-found", "player", name);
         } else {
            PlayerStats data = stats.data(uniqueId);
            if (data.values().isEmpty()) {
               this.plugin.messages().send(sender, "admin.statsinfo-player-none", "player", name);
            } else {
               this.plugin.messages().send(sender, "admin.statsinfo-player-header", "player", stats.name(uniqueId), "uuid", uniqueId.toString());

               for (StatType type : StatType.values()) {
                  long value = data.value(type);
                  if (value > 0L) {
                     this.plugin
                        .messages()
                        .send(sender, "stats.line", "label", this.plugin.messages().raw(type.messageKey()), "value", this.statValue(type, value));
                  }
               }

               if (this.plugin.economy().isEnabled()) {
                  this.plugin
                     .messages()
                     .send(
                        sender,
                        "stats.line",
                        "label",
                        this.plugin.messages().raw("stats.category.money"),
                        "value",
                        this.plugin.economy().formatExact(this.plugin.economy().balance(Bukkit.getOfflinePlayer(uniqueId)))
                     );
               }

               for (TopCategory category : TopCategory.values()) {
                  int rank = stats.rankOf(uniqueId, category);
                  if (rank > 0) {
                     this.plugin
                        .messages()
                        .send(
                           sender,
                           "stats.rank",
                           "label",
                           this.plugin.messages().raw(category.messageKey()),
                           "rank",
                           Integer.toString(rank),
                           "total",
                           Integer.toString(stats.rankedCount(category))
                        );
                  }
               }
            }
         }
      } else {
         this.plugin.messages().send(sender, "stats.disabled");
      }
   }

   private void resetStats(CommandSender sender, String label, String[] args) {
      if (args.length != 2) {
         this.plugin.messages().send(sender, "admin.statsreset-usage", "usage", "/" + label + " statsreset <pemain|semua>");
      } else {
         StatisticsService stats = this.plugin.stats();
         if (stats != null && stats.isEnabled()) {
            if (!args[1].equalsIgnoreCase("semua") && !args[1].equalsIgnoreCase("all")) {
               UUID uniqueId = stats.findUniqueId(args[1]);
               if (uniqueId == null) {
                  this.plugin.messages().send(sender, "admin.player-not-found", "player", args[1]);
               } else {
                  int categories = stats.data(uniqueId).values().size();
                  if (!stats.reset(uniqueId)) {
                     this.plugin.messages().send(sender, "admin.statsreset-failed", "player", args[1]);
                  } else {
                     this.plugin.messages().send(sender, "admin.statsreset-done", "player", args[1], "count", Integer.toString(categories));
                  }
               }
            } else {
               int count = stats.resetAll();
               this.plugin.messages().send(sender, "admin.statsreset-all", "count", Integer.toString(count));
            }
         } else {
            this.plugin.messages().send(sender, "stats.disabled");
         }
      }
   }

   private String statValue(StatType type, long value) {
      if (type == StatType.PLAYTIME && this.plugin.stats() != null) {
         return this.plugin.stats().formatPlaytime(value);
      } else {
         return type == StatType.MONEY_EARNED && this.plugin.economy().isEnabled() ? this.plugin.economy().format(value) : Long.toString(value);
      }
   }

   private void clearWorth(CommandSender sender, String label, String[] args) {
      if (args.length != 2) {
         this.plugin.messages().send(sender, "admin.worthclear-usage", "usage", "/" + label + " worthclear <pemain|semua>");
      } else if (!"semua".equalsIgnoreCase(args[1]) && !"all".equalsIgnoreCase(args[1])) {
         Player target = Bukkit.getPlayerExact(args[1]);
         if (target == null) {
            this.plugin.messages().send(sender, "admin.worthclear-player-offline", "player", args[1]);
         } else {
            int cleared = this.plugin.worth().clear(target);
            this.plugin.messages().send(sender, "admin.worthclear-done", "player", target.getName(), "count", Integer.toString(cleared));
            this.plugin.getLogger().info(sender.getName() + " melepas lore harga dari " + cleared + " item milik " + target.getName() + ".");
         }
      } else {
         int cleared = this.plugin.worth().clearAll();
         this.plugin.messages().send(sender, "admin.worthclear-all", "count", Integer.toString(cleared));
      }
   }

   private void sendVersion(CommandSender sender) {
      Text.send(sender, this.plugin.messages().prefix() + "&7Plugin   : &fW2NSMP &7v" + PluginInfo.version());
      Text.send(sender, "&7Target   : &fPaper API 26.2 &8(Minecraft " + EnvironmentReport.minecraftVersion() + ")");
      Text.send(sender, "&7Java     : &f" + System.getProperty("java.version") + " &8(butuh Java 25+)");
      Text.send(sender, "&7Server   : &f" + EnvironmentReport.serverName());
   }

   private void sendDebug(CommandSender sender) {
      Runtime runtime = Runtime.getRuntime();
      long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024L / 1024L;
      long maxMb = runtime.maxMemory() / 1024L / 1024L;
      List<String> failures = this.plugin.commandManager().failures();
      Text.send(sender, this.plugin.messages().prefix() + "&bDebug");
      Text.send(sender, "&7Bukkit   : &f" + Bukkit.getBukkitVersion());
      Text.send(sender, "&7Java     : &f" + System.getProperty("java.version") + " &8(" + System.getProperty("java.vendor") + ")");
      Text.send(sender, "&7Memori   : &f" + usedMb + " MB &7/&f " + maxMb + " MB");
      Text.send(
         sender,
         "&7Command  : &f"
            + this.plugin.commandManager().registered().size()
            + " terdaftar"
            + (failures.isEmpty() ? " &8(tidak ada yang gagal)" : " &c(gagal: " + String.join(", ", failures) + ")")
      );
      Text.send(sender, "&7" + EnvironmentReport.softDependencySummary());
      if (this.plugin.economy().isEnabled()) {
         Text.send(
            sender,
            "&7Ekonomi  : &aaktif &7| provider: &f" + this.plugin.economy().providerName() + " &7| contoh format: &f" + this.plugin.economy().format(12500.0)
         );
      } else {
         Text.send(sender, "&7Ekonomi  : &cnono &7(tidak ada Vault/provider ekonomi)");
      }

      Text.send(sender, "&7Harga item: &f" + this.plugin.sell().prices().size() + " &7item di prices.yml");
      Text.send(
         sender,
         "&7Combat   : &f"
            + (this.plugin.combat().enabled() ? "aktif" : "nonaktif")
            + " &7| durasi &f"
            + this.plugin.config().combatDurationSeconds()
            + "s &7| tertag &f"
            + this.plugin.combat().taggedCount()
      );
      Text.send(
         sender,
         "&7Auction  : &f"
            + (this.plugin.config().auctionEnabled() ? "aktif" : "nonaktif")
            + " &7| listing &f"
            + this.plugin.auction().listingCount()
            + " &7| item di kotak &f"
            + this.plugin.auction().mailboxTotal()
      );
      Text.send(
         sender,
         "&7Worth    : &f"
            + (this.plugin.config().worthEnabled() ? "aktif" : "nonaktif")
            + " &7| harga &f"
            + this.plugin.sell().prices().size()
            + " &7| lore terpasang &f"
            + this.plugin.worth().appliedTotal()
      );
      Text.send(sender, "&7Fitur    : command layer, ekonomi, sell GUI, home, rtp, combat, auction, worth");
      if (sender.hasPermission("w2nsmp.admin")) {
         Text.send(sender, "&7Permission admin: &aOK");
      }
   }

   private boolean require(CommandSender sender, String permission) {
      if (sender.hasPermission(permission)) {
         return true;
      }

      this.plugin.messages().send(sender, "command.no-permission", "permission", permission);
      return false;
   }
}
