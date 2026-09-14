package me.w2n.w2nsmp.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.gui.SkillMenu;
import me.w2n.w2nsmp.skill.SkillInfo;
import me.w2n.w2nsmp.skill.SkillService;
import me.w2n.w2nsmp.skill.SkillType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/**
 * Perintah {@code /skill}.
 *
 * <pre>
 * /skill                              GUI skill (pemain) / bantuan (konsol)
 * /skill &lt;nama-skill&gt;                 detail satu skill milik sendiri
 * /skill list                         ringkasan semua skill milik sendiri
 * /skill top [skill]                  peringkat total skill / satu skill
 * /skill help                         bantuan
 * /skill check                        diagnostik fitur (listener, buff, uji XP)
 * /skill info &lt;pemain&gt; [skill]        lihat skill pemain lain        (w2nsmp.skill.admin)
 * /skill set &lt;pemain&gt; &lt;skill&gt; &lt;level&gt; setel level                    (w2nsmp.skill.admin)
 * /skill add &lt;pemain&gt; &lt;skill&gt; &lt;xp&gt;    tambah/kurangi XP              (w2nsmp.skill.admin)
 * /skill reset &lt;pemain&gt; [skill]       reset satu/semua skill         (w2nsmp.skill.admin)
 * </pre>
 *
 * <p>Label polos {@code /skill} didaftarkan {@code CommandManager} seperti command fitur lain:
 * selalu tersedia sebagai {@code /w2nsmp:skill}, dan memakai label polos bila tidak bentrok
 * dengan plugin lain.
 */
public final class SkillCommand implements TabExecutor {
   private static final String PERMISSION = "w2nsmp.skill";
   private static final String ADMIN_PERMISSION = "w2nsmp.skill.admin";
   private final W2NSMP plugin;

   public SkillCommand(W2NSMP plugin) {
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!sender.hasPermission(PERMISSION)) {
         this.plugin.messages().send(sender, "command.no-permission", "permission", PERMISSION);
         return true;
      }

      SkillService service = this.plugin.skills();
      if (service == null) {
         this.plugin.messages().send(sender, "skill.disabled");
         return true;
      }

      // Diagnostik sengaja diperiksa SEBELUM gerbang "fitur aktif": justru saat fitur mati atau
      // bermasalah, admin harus tetap bisa menanyakan kenapa.
      if (args.length > 0 && this.isCheckWord(args[0])) {
         this.sendCheck(sender, service);
         return true;
      }

      if (!service.enabled()) {
         this.plugin.messages().send(sender, "skill.disabled");
         return true;
      }

      if (args.length == 0) {
         if (sender instanceof Player player) {
            if (SkillMenu.visible(this.plugin).isEmpty()) {
               this.plugin.messages().send(sender, "skill.no-skills");
               return true;
            }

            SkillMenu.open(this.plugin, player);
         } else {
            this.sendHelp(sender, label);
         }

         return true;
      }

      String sub = args[0].toLowerCase(Locale.ROOT);
      switch (sub) {
         case "help":
         case "bantuan":
            this.sendHelp(sender, label);
            break;
         case "list":
         case "daftar":
            this.sendOwnSummary(sender);
            break;
         case "top":
         case "peringkat":
         case "ranking":
            this.sendTop(sender, args);
            break;
         case "info":
         case "lihat":
            this.sendOther(sender, args);
            break;
         case "set":
         case "setel":
            this.setLevel(sender, args);
            break;
         case "add":
         case "tambah":
            this.addXp(sender, args);
            break;
         case "reset":
         case "hapus":
            this.reset(sender, args);
            break;
         default:
            this.sendOwnDetail(sender, args[0]);
      }

      return true;
   }

   private void sendOwnSummary(CommandSender sender) {
      if (!(sender instanceof Player player)) {
         this.plugin.messages().send(sender, "skill.player-only", "hint", "/skill info <pemain>");
         return;
      }

      SkillInfo.sendSummary(this.plugin, sender, player);
   }

   /** Peringkat skill: {@code /skill top} (total semua skill) atau {@code /skill top <skill>}. */
   private void sendTop(CommandSender sender, String[] args) {
      SkillType type = null;
      if (args.length > 1) {
         type = SkillType.fromKey(args[1]);
         if (type == null) {
            this.plugin.messages().send(sender, "skill.unknown-skill", "input", args[1], "list", String.join(", ", SkillType.keys()));
            return;
         }

         SkillService service = this.plugin.skills();
         if (service != null && !service.settings(type).enabled()) {
            this.plugin.messages().send(sender, "skill.skill-disabled", "skill", service.label(type));
            return;
         }
      }

      SkillInfo.sendTop(this.plugin, sender, type);
   }

   private void sendOwnDetail(CommandSender sender, String rawSkill) {
      SkillType type = SkillType.fromKey(rawSkill);
      if (type == null) {
         this.plugin.messages().send(sender, "skill.unknown-skill", "input", rawSkill, "list", String.join(", ", SkillType.keys()));
         return;
      }

      if (!(sender instanceof Player player)) {
         this.plugin.messages().send(sender, "skill.player-only", "hint", "/skill info <pemain> " + type.key());
         return;
      }

      SkillService service = this.plugin.skills();
      if (!service.settings(type).enabled()) {
         this.plugin.messages().send(sender, "skill.skill-disabled", "skill", service.label(type));
         return;
      }

      SkillInfo.sendDetail(this.plugin, sender, player, type);
   }

   private void sendOther(CommandSender sender, String[] args) {
      if (!sender.hasPermission(ADMIN_PERMISSION)) {
         this.plugin.messages().send(sender, "command.no-permission", "permission", ADMIN_PERMISSION);
         return;
      }

      if (args.length < 2) {
         this.plugin.messages().send(sender, "skill.usage-info");
         return;
      }

      Player target = this.findPlayer(sender, args[1]);
      if (target == null) {
         return;
      }

      if (args.length > 2) {
         SkillType type = SkillType.fromKey(args[2]);
         if (type == null) {
            this.plugin.messages().send(sender, "skill.unknown-skill", "input", args[2], "list", String.join(", ", SkillType.keys()));
            return;
         }

         SkillInfo.sendDetail(this.plugin, sender, target, type);
         return;
      }

      SkillInfo.sendSummary(this.plugin, sender, target);
   }

   private void setLevel(CommandSender sender, String[] args) {
      if (!this.admin(sender)) {
         return;
      }

      if (args.length < 4) {
         this.plugin.messages().send(sender, "skill.usage-set");
         return;
      }

      Player target = this.findPlayer(sender, args[1]);
      if (target == null) {
         return;
      }

      SkillType type = SkillType.fromKey(args[2]);
      if (type == null) {
         this.plugin.messages().send(sender, "skill.unknown-skill", "input", args[2], "list", String.join(", ", SkillType.keys()));
         return;
      }

      int level;
      try {
         level = Integer.parseInt(args[3].trim());
      } catch (NumberFormatException exception) {
         this.plugin.messages().send(sender, "skill.invalid-number", "input", args[3]);
         return;
      }

      SkillService service = this.plugin.skills();
      int applied = service.setLevel(target.getUniqueId(), type, level);
      this.plugin
         .messages()
         .send(
            sender,
            "skill.admin-set",
            "player",
            target.getName(),
            "skill",
            service.label(type),
            "level",
            Integer.toString(applied),
            "max",
            Integer.toString(service.maxLevel())
         );
      if (!target.equals(sender)) {
         this.plugin.messages().send(target, "skill.target-set", "skill", service.label(type), "level", Integer.toString(applied));
      }
   }

   private void addXp(CommandSender sender, String[] args) {
      if (!this.admin(sender)) {
         return;
      }

      if (args.length < 4) {
         this.plugin.messages().send(sender, "skill.usage-add");
         return;
      }

      Player target = this.findPlayer(sender, args[1]);
      if (target == null) {
         return;
      }

      SkillType type = SkillType.fromKey(args[2]);
      if (type == null) {
         this.plugin.messages().send(sender, "skill.unknown-skill", "input", args[2], "list", String.join(", ", SkillType.keys()));
         return;
      }

      double amount;
      try {
         amount = Double.parseDouble(args[3].trim().replace(',', '.'));
      } catch (NumberFormatException exception) {
         this.plugin.messages().send(sender, "skill.invalid-number", "input", args[3]);
         return;
      }

      SkillService service = this.plugin.skills();
      service.addXpAdmin(target.getUniqueId(), type, amount);
      this.plugin
         .messages()
         .send(
            sender,
            "skill.admin-add",
            "player",
            target.getName(),
            "skill",
            service.label(type),
            "amount",
            SkillService.format(amount),
            "xp",
            SkillService.format(service.xp(target, type)),
            "level",
            Integer.toString(service.level(target, type))
         );
   }

   private void reset(CommandSender sender, String[] args) {
      if (!this.admin(sender)) {
         return;
      }

      if (args.length < 2) {
         this.plugin.messages().send(sender, "skill.usage-reset");
         return;
      }

      Player target = this.findPlayer(sender, args[1]);
      if (target == null) {
         return;
      }

      SkillType type = args.length > 2 ? SkillType.fromKey(args[2]) : null;
      if (args.length > 2 && type == null) {
         this.plugin.messages().send(sender, "skill.unknown-skill", "input", args[2], "list", String.join(", ", SkillType.keys()));
         return;
      }

      SkillService service = this.plugin.skills();
      int count = service.reset(target.getUniqueId(), type);
      service.saveNow();
      this.plugin
         .messages()
         .send(
            sender,
            "skill.admin-reset",
            "player",
            target.getName(),
            "skill",
            type == null ? this.plugin.messages().raw("skill.all-skills") : service.label(type),
            "count",
            Integer.toString(count)
         );
      if (!target.equals(sender)) {
         this.plugin.messages().send(target, "skill.target-reset", "skill", type == null ? this.plugin.messages().raw("skill.all-skills") : service.label(type));
      }
   }

   private boolean admin(CommandSender sender) {
      if (!sender.hasPermission(ADMIN_PERMISSION)) {
         this.plugin.messages().send(sender, "command.no-permission", "permission", ADMIN_PERMISSION);
         return false;
      }

      return true;
   }

   /**
    * Cari pemain target. Hanya pemain online yang dipakai: mencari pemain offline dengan nama
    * bisa memblokir thread utama server, jadi admin diminta memakai nama pemain yang sedang
    * online (atau UUID lewat konsol bila perlu).
    */
   private Player findPlayer(CommandSender sender, String name) {
      Player target = Bukkit.getPlayerExact(name);
      if (target == null) {
         UUID uniqueId = this.findUniqueId(name);
         if (uniqueId != null) {
            target = Bukkit.getPlayer(uniqueId);
         }
      }

      if (target == null) {
         this.plugin.messages().send(sender, "skill.target-not-found", "player", name);
         return null;
      }

      return target;
   }

   private UUID findUniqueId(String name) {
      try {
         return this.plugin.stats() == null ? null : this.plugin.stats().findUniqueId(name);
      } catch (RuntimeException exception) {
         return null;
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (!sender.hasPermission(PERMISSION)) {
         return List.of();
      }

      SkillService service = this.plugin.skills();
      if (service == null || !service.enabled()) {
         return List.of();
      }

      String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
      boolean admin = sender.hasPermission(ADMIN_PERMISSION);
      if (args.length <= 1) {
         List<String> options = new ArrayList<>(SkillType.keys());
         options.add("list");
         options.add("top");
         options.add("help");
         options.add("check");
         options.add("cek");
         if (admin) {
            options.add("info");
            options.add("set");
            options.add("add");
            options.add("reset");
         }

         return options.stream().filter(option -> option.startsWith(typed)).toList();
      }

      String sub = args[0].toLowerCase(Locale.ROOT);
      if (args.length == 2) {
         if (this.isTargetWord(sub)) {
            return admin ? this.playerNames(typed) : List.of();
         }

         if (this.isTopWord(sub)) {
            return SkillType.keys().stream().filter(key -> key.startsWith(typed)).toList();
         }

         return List.of();
      }

      if (!admin || !this.isTargetWord(sub)) {
         return List.of();
      }

      if (args.length == 3) {
         return "reset".equals(sub) || "hapus".equals(sub)
            ? SkillType.keys().stream().filter(key -> key.startsWith(typed)).toList()
            : SkillType.keys().stream().filter(key -> key.startsWith(typed)).toList();
      }

      if (args.length == 4) {
         if ("set".equals(sub) || "setel".equals(sub)) {
            List<String> levels = new ArrayList<>();

            for (int level = 1; level <= Math.min(service.maxLevel(), 10); level++) {
               String value = Integer.toString(level);
               if (value.startsWith(typed)) {
                  levels.add(value);
               }
            }

            levels.add(Integer.toString(service.maxLevel()));
            return levels;
         }

         if ("add".equals(sub) || "tambah".equals(sub)) {
            return List.of("100", "500", "1000", "-100").stream().filter(value -> value.startsWith(typed)).toList();
         }
      }

      return List.of();
   }

   private boolean isTargetWord(String sub) {
      return switch (sub) {
         case "info", "lihat", "set", "setel", "add", "tambah", "reset", "hapus" -> true;
         default -> false;
      };
   }

   private List<String> playerNames(String typed) {
      List<String> names = new ArrayList<>();

      for (Player player : Bukkit.getOnlinePlayers()) {
         if (player.getName().toLowerCase(Locale.ROOT).startsWith(typed)) {
            names.add(player.getName());
         }
      }

      return names;
   }

   private boolean isTopWord(String sub) {
      return switch (sub) {
         case "top", "peringkat", "ranking" -> true;
         default -> false;
      };
   }

   private boolean isCheckWord(String word) {
      if (word == null) {
         return false;
      }

      return switch (word.toLowerCase(Locale.ROOT)) {
         case "check", "cek", "diagnosa", "diagnostic" -> true;
         default -> false;
      };
   }

   /**
    * Diagnostik fitur di server ini: membuktikan listener terdaftar, buff hidup, dan XP benar-benar
    * bisa masuk. Setiap pemain boleh melihat status dirinya (termasuk uji XP +1); rincian listener,
    * API server, dan kesalahan terakhir hanya untuk admin.
    */
   private void sendCheck(CommandSender sender, SkillService service) {
      boolean admin = sender.hasPermission(ADMIN_PERMISSION);
      this.plugin.messages().send(sender, "skill.check-header", "player", sender.getName());
      this.plugin
         .messages()
         .send(
            sender,
            "skill.check-status",
            "status",
            service.enabled() ? "aktif" : "MATI (skills.enabled: false)",
            "buffs",
            service.buffsEnabled() ? "aktif" : "MATI (skills.buffs.enabled: false)",
            "max",
            Integer.toString(service.maxLevel()),
            "skills",
            Integer.toString(service.enabledSkillCount())
         );

      if (sender instanceof Player player) {
         String mode = player.getGameMode() == null ? "-" : player.getGameMode().name();
         this.plugin
            .messages()
            .send(
               sender,
               "skill.check-gamemode",
               "gamemode",
               mode,
               "effect",
               service.gameModeAllowed(player) ? "aktif" : "DILEWATI",
               "list",
               String.join(", ", service.skippedGameModes())
            );
         String world = player.getWorld() == null ? "-" : player.getWorld().getName();
         this.plugin
            .messages()
            .send(
               sender,
               "skill.check-world",
               "world",
               world,
               "effect",
               service.worldAllowed(player.getWorld()) ? "aktif" : "DILEWATI",
               "list",
               service.allowedWorlds().isEmpty() ? "semua world" : String.join(", ", service.allowedWorlds())
            );

         // Uji hidup: benar-benar memasukkan 1 XP dan melihat apakah tercatat.
         SkillType tested = SkillType.ENDURANCE;
         double before = service.xp(player, tested);
         service.addXp(player, tested, 1.0D);
         double after = service.xp(player, tested);
         this.plugin
            .messages()
            .send(
               sender,
               "skill.check-xp",
               "skill",
               service.label(tested),
               "status",
               after > before ? "OK (XP tercatat)" : "GAGAL (XP tidak masuk)",
               "xp",
               SkillService.format(after),
               "level",
               Integer.toString(service.level(player, tested))
            );
      }

      if (!admin) {
         this.plugin.messages().send(sender, "skill.check-note");
         return;
      }

      this.plugin.messages().send(sender, "skill.check-api", "api", service.probe().summary());
      if (!service.probe().missingPotions().isEmpty() && this.plugin.messages().has("skill.check-potions")) {
         this.plugin.messages().send(sender, "skill.check-potions",
            "list", String.join(", ", service.probe().missingPotions()));
      }
      service.diagnostics().inspect();
      this.plugin
         .messages()
         .send(
            sender,
            "skill.check-listeners",
            "status",
            service.diagnostics().listenersOk() ? "terdaftar semua di server" : "ADA YANG HILANG (lihat baris di bawah)"
         );

      for (String line : service.diagnostics().listenerReport().split(" \\| ")) {
         this.plugin.messages().send(sender, "skill.check-listener-line", "line", line);
      }

      List<String> errors = service.diagnostics().errorLines();
      this.plugin.messages().send(sender, "skill.check-errors", "count", Integer.toString(errors.size()));

      for (String error : errors) {
         this.plugin.messages().send(sender, "skill.check-error-line", "line", error);
      }

      Map<String, Object> snapshot = service.debugSnapshot();
      this.plugin
         .messages()
         .send(
            sender,
            "skill.check-data",
            "file",
            service.fileName(),
            "profiles",
            String.valueOf(snapshot.get("profiles")),
            "dirty",
            String.valueOf(snapshot.get("dirty"))
         );

      for (SkillType type : SkillType.values()) {
         this.plugin.messages().send(sender, "skill.check-skill-line", "line", service.settings(type).summary());
      }
   }

   private void sendHelp(CommandSender sender, String label) {
      this.plugin.messages().send(sender, "skill.help-header", "label", label);
      this.plugin.messages().send(sender, "skill.help-open", "label", label);
      this.plugin.messages().send(sender, "skill.help-detail", "label", label);
      this.plugin.messages().send(sender, "skill.help-list", "label", label);
      this.plugin.messages().send(sender, "skill.help-top", "label", label);
      this.plugin.messages().send(sender, "skill.help-check", "label", label);
      if (sender.hasPermission(ADMIN_PERMISSION)) {
         this.plugin.messages().send(sender, "skill.help-info", "label", label);
         this.plugin.messages().send(sender, "skill.help-set", "label", label);
         this.plugin.messages().send(sender, "skill.help-add", "label", label);
         this.plugin.messages().send(sender, "skill.help-reset", "label", label);
      }

      this.plugin.messages().send(sender, "skill.help-note", "list", String.join(", ", SkillType.keys()));
   }
}
