package me.w2n.w2nsmp.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.gui.LeaderboardMenu;
import me.w2n.w2nsmp.stats.LeaderboardEntry;
import me.w2n.w2nsmp.stats.TopCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class TopCommand implements TabExecutor {
   private static final String PERMISSION = "w2nsmp.top";
   private final W2NSMP plugin;

   public TopCommand(W2NSMP plugin) {
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!sender.hasPermission("w2nsmp.top")) {
         this.plugin.messages().send(sender, "command.no-permission", "permission", "w2nsmp.top");
         return true;
      }

      if (!this.plugin.config().statisticsEnabled()) {
         this.plugin.messages().send(sender, "stats.disabled");
         return true;
      }

      if (this.plugin.stats() == null) {
         this.plugin.messages().send(sender, "stats.disabled");
         return true;
      }

      if (args.length == 0) {
         if (sender instanceof Player player) {
            LeaderboardMenu.open(this.plugin, player, LeaderboardMenu.defaultCategory(this.plugin), 1);
         } else {
            this.sendChat(sender, label, null, 1);
         }

         return true;
      }

      switch (args[0].toLowerCase(Locale.ROOT)) {
         case "help":
         case "bantuan":
            this.sendHelp(sender, label);
            break;
         case "list":
         case "kategori":
            this.sendCategoryList(sender);
            break;
         case "chat":
         case "daftar":
         case "peringkat":
            this.sendChat(sender, label, args.length > 1 ? args[1] : null, this.parsePage(sender, args.length > 2 ? args[2] : null));
            break;
         case "money":
         case "uang":
         case "saldo":
            this.openOrChat(sender, TopCategory.MONEY, args, label);
            break;
         default:
            TopCategory category = TopCategory.fromKey(args[0]);
            if (category == null) {
               this.plugin.messages().send(sender, "top.unknown-category", "input", args[0], "list", String.join(", ", TopCategory.keys()));
               return true;
            }

            this.openOrChat(sender, category, args, label);
      }

      return true;
   }

   private void openOrChat(CommandSender sender, TopCategory category, String[] args, String label) {
      boolean chat = args.length > 1 && (args[1].equalsIgnoreCase("chat") || args[1].equalsIgnoreCase("text"));
      // /top <kategori> [chat] [halaman]: halaman boleh ditulis langsung setelah kategori
      // supaya GUI juga bisa dibuka pada halaman tertentu.
      String rawPage = chat ? (args.length > 2 ? args[2] : null) : (args.length > 1 ? args[1] : null);
      int page = this.parsePage(sender, rawPage);
      if (chat || !(sender instanceof Player player)) {
         this.sendChat(sender, label, category.key(), page);
      } else {
         LeaderboardMenu.open(this.plugin, player, category, page);
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (!sender.hasPermission("w2nsmp.top")) {
         return List.of();
      }

      String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
      if (args.length <= 1) {
         List<String> options = new ArrayList<>(TopCategory.keys());
         options.add("chat");
         options.add("list");
         options.add("help");
         return options.stream().filter(option -> option.startsWith(typed)).toList();
      }

      if (args.length == 2) {
         if (this.isChatWord(args[0])) {
            return TopCategory.keys().stream().filter(key -> key.startsWith(typed)).toList();
         }

         return this.category(args[0]) == null ? List.of() : List.of("chat").stream().filter(key -> key.startsWith(typed)).toList();
      }

      if (args.length == 3 && (this.isChatWord(args[0]) ? this.category(args[1]) != null : this.isChatWord(args[1]))) {
         TopCategory category = this.isChatWord(args[0]) ? this.category(args[1]) : this.category(args[0]);
         return this.pageOptions(category, typed);
      }

      return List.of();
   }

   private List<String> pageOptions(TopCategory category, String typed) {
      if (category == null || this.plugin.stats() == null) {
         return List.of();
      }

      int pages = this.plugin.stats().pages(category, this.plugin.config().leaderboardChatLines());
      List<String> options = new ArrayList<>(Math.min(pages, 10));

      for (int page = 1; page <= Math.min(pages, 10); page++) {
         String value = Integer.toString(page);
         if (value.startsWith(typed)) {
            options.add(value);
         }
      }

      return options;
   }

   private boolean isChatWord(String raw) {
      return raw != null
         && (raw.equalsIgnoreCase("chat") || raw.equalsIgnoreCase("text") || raw.equalsIgnoreCase("daftar") || raw.equalsIgnoreCase("peringkat"));
   }

   private TopCategory category(String raw) {
      if (raw == null) {
         return null;
      }

      String key = raw.toLowerCase(Locale.ROOT);
      return key.equals("money") || key.equals("uang") || key.equals("saldo") ? TopCategory.MONEY : TopCategory.fromKey(key);
   }

   /** Nomor halaman dari argumen; 1 bila kosong, dan pesan kesalahan bila bukan angka. */
   private int parsePage(CommandSender sender, String raw) {
      if (raw == null || raw.isBlank()) {
         return 1;
      }

      try {
         return Math.max(1, Integer.parseInt(raw.trim()));
      } catch (NumberFormatException exception) {
         this.plugin.messages().send(sender, "top.page-invalid", "input", raw);
         return 1;
      }
   }

   private void sendChat(CommandSender sender, String label, String categoryKey, int page) {
      TopCategory category = categoryKey == null ? LeaderboardMenu.defaultCategory(this.plugin) : TopCategory.fromKey(categoryKey);
      if (category == null) {
         this.plugin
            .messages()
            .send(sender, "top.unknown-category", "input", String.valueOf(categoryKey), "list", String.join(", ", TopCategory.keys()));
         return;
      }

      int limit = this.plugin.config().leaderboardChatLines();
      int pages = this.plugin.stats().pages(category, limit);
      List<LeaderboardEntry> entries = this.plugin.stats().page(category, page, limit);
      if (entries.isEmpty()) {
         if (page > pages) {
            this.plugin.messages().send(sender, "top.page-out-of-range", "page", Integer.toString(page), "pages", Integer.toString(pages));
         } else {
            this.plugin.messages().send(sender, "top.empty");
         }

         return;
      }

      String labelCategory = LeaderboardMenu.categoryLabel(this.plugin, category);
      this.plugin
         .messages()
         .send(
            sender,
            "top.chat-header",
            "category",
            labelCategory,
            "key",
            category.key(),
            "page",
            Integer.toString(page),
            "pages",
            Integer.toString(pages)
         );

      for (LeaderboardEntry entry : entries) {
         this.plugin
            .messages()
            .send(
               sender,
               "top.chat-entry",
               "rank",
               Integer.toString(entry.rank()),
               "player",
               entry.name(),
               "value",
               LeaderboardMenu.displayValue(this.plugin, category, entry.value())
            );
      }

      this.plugin
         .messages()
         .send(
            sender,
            "top.chat-footer",
            "label",
            label,
            "category",
            category.key(),
            "page",
            Integer.toString(page),
            "pages",
            Integer.toString(pages),
            "next",
            page < pages ? Integer.toString(page + 1) : "-",
            "previous",
            page > 1 ? Integer.toString(page - 1) : "-"
         );
   }

   private void sendCategoryList(CommandSender sender) {
      this.plugin.messages().send(sender, "top.help-header", "label", "top");

      for (TopCategory category : TopCategory.values()) {
         this.plugin.messages().send(sender, "stats.line", "label", LeaderboardMenu.categoryLabel(this.plugin, category), "value", category.key());
      }
   }

   private void sendHelp(CommandSender sender, String label) {
      this.plugin.messages().send(sender, "top.help-header", "label", label);
      this.plugin.messages().send(sender, "top.help-list", "label", label);
      this.plugin.messages().send(sender, "top.help-category", "label", label);
      this.plugin.messages().send(sender, "top.help-page", "label", label);
      this.plugin.messages().send(sender, "top.help-chat", "label", label);
      this.plugin.messages().send(sender, "top.help-help", "label", label);
      this.plugin.messages().send(sender, "top.help-note", "list", String.join(", ", TopCategory.keys()));
   }
}
