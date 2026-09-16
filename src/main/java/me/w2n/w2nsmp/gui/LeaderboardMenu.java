package me.w2n.w2nsmp.gui;

import java.util.ArrayList;
import java.util.List;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.config.GuiConfig;
import me.w2n.w2nsmp.stats.LeaderboardEntry;
import me.w2n.w2nsmp.stats.StatType;
import me.w2n.w2nsmp.stats.TopCategory;
import me.w2n.w2nsmp.utility.GuiKit;
import me.w2n.w2nsmp.utility.Items;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class LeaderboardMenu {
   public static final int CATEGORIES_SIZE = 27;
   public static final int CATEGORY_SLOT_START = 0;
   public static final int SLOT_BACK = 18;
   public static final int SLOT_CATEGORIES_CLOSE = 26;

   private LeaderboardMenu() {
   }

   public static GuiConfig gui(W2NSMP plugin) {
      return plugin.guiConfigs().top();
   }

   private static int button(W2NSMP plugin, String path, int fallback, int size) {
      int configured = gui(plugin).slot(path, fallback);
      if (configured >= size - 9 && configured < size) {
         return configured;
      }

      plugin.getLogger().warning("gui/top.yml: slot " + path + "=" + configured + " berada di luar baris tombol; memakai " + fallback + ".");
      return fallback;
   }

   public static int slotPrev(W2NSMP plugin, int size) {
      return button(plugin, "buttons.prev.slot", size - 9, size);
   }

   public static int slotCategories(W2NSMP plugin, int size) {
      return button(plugin, "buttons.back.slot", size - 7, size);
   }

   public static int slotInfo(W2NSMP plugin, int size) {
      return button(plugin, "buttons.info.slot", size - 5, size);
   }

   public static int slotClose(W2NSMP plugin, int size) {
      return button(plugin, "buttons.close.slot", size - 3, size);
   }

   public static int slotNext(W2NSMP plugin, int size) {
      return button(plugin, "buttons.next.slot", size - 1, size);
   }

   public static int categoriesSize(W2NSMP plugin) {
      return gui(plugin).size("categories.size", 27);
   }

   public static int slotBack(W2NSMP plugin) {
      int slot = gui(plugin).slot("categories.back-slot", 18);
      return slot < categoriesSize(plugin) ? slot : 18;
   }

   public static int slotCategoriesClose(W2NSMP plugin) {
      int slot = gui(plugin).slot("categories.close-slot", 26);
      return slot < categoriesSize(plugin) ? slot : 26;
   }

   public static int pageSize(W2NSMP plugin) {
      return plugin.config().leaderboardEntriesPerPage();
   }

   public static int size(W2NSMP plugin) {
      int rows = (int)Math.ceil((pageSize(plugin) + 9) / 9.0);
      return Math.clamp(rows, 3, 6) * 9;
   }

   public static int slotPrev(int size) {
      return size - 9;
   }

   public static int slotCategories(int size) {
      return size - 7;
   }

   public static int slotInfo(int size) {
      return size - 5;
   }

   public static int slotClose(int size) {
      return size - 3;
   }

   public static int slotNext(int size) {
      return size - 1;
   }

   public static void open(W2NSMP plugin, Player player, TopCategory category, int page) {
      TopCategory selected = category == null ? defaultCategory(plugin) : category;
      int size = size(plugin);
      int pageSize = pageSize(plugin);
      LeaderboardHolder holder = new LeaderboardHolder(player.getUniqueId(), size, pageSize);
      holder.category(selected);
      holder.page(page);
      holder.categories(false);
      int pages = plugin.stats() == null ? 1 : plugin.stats().pages(selected, pageSize);
      int shown = Math.min(Math.max(1, page), Math.max(1, pages));
      Inventory inventory = Bukkit.createInventory(
         holder,
         size,
         gui(plugin)
            .title(
               "top.gui-title",
               "category",
               categoryLabel(plugin, selected),
               "key",
               selected.key(),
               "page",
               Integer.toString(shown),
               "pages",
               Integer.toString(pages)
            )
      );
      holder.setInventory(inventory);
      holder.page(shown);
      decorate(plugin, inventory, size);
      render(plugin, inventory, holder);
      player.openInventory(inventory);
      plugin.guiSounds().play(player, gui(plugin), "open");
   }

   public static void openCategories(W2NSMP plugin, Player player, int backPage) {
      int size = categoriesSize(plugin);
      LeaderboardHolder holder = new LeaderboardHolder(player.getUniqueId(), size, pageSize(plugin));
      holder.page(backPage);
      holder.category(defaultCategory(plugin));
      holder.categories(true);
      Inventory inventory = Bukkit.createInventory(holder, size, categoriesTitle(plugin));
      holder.setInventory(inventory);
      renderCategories(plugin, inventory, holder);
      player.openInventory(inventory);
      plugin.guiSounds().play(player, gui(plugin), "open");
   }

   private static Component categoriesTitle(W2NSMP plugin) {
      String override = gui(plugin).name("categories-title");
      return override == null ? plugin.messages().component("top.categories-title") : plugin.messages().colored(override);
   }

   public static void render(W2NSMP plugin, Inventory inventory, LeaderboardHolder holder) {
      TopCategory category = holder.category();
      int size = holder.size();
      int pageSize = holder.pageSize();
      List<LeaderboardEntry> entries = plugin.stats() == null ? List.of() : plugin.stats().page(category, holder.page(), pageSize);
      int total = plugin.stats() == null ? 0 : plugin.stats().rankedCount(category);
      int pages = Math.max(1, (int)Math.ceil((double)total / Math.max(1, pageSize)));

      for (int slot = 0; slot < Math.min(entries.size(), pageSize); slot++) {
         LeaderboardEntry entry = entries.get(slot);
         ItemStack row = Items.create(
            material(plugin, "top.entry-material", Material.PLAYER_HEAD),
            plugin.messages().raw("top.entry-name", "rank", Integer.toString(entry.rank()), "player", entry.name()),
            entryLore(plugin, category, entry, total)
         );
         inventory.setItem(slot, GuiKit.head(row, entry.uniqueId()));
      }

      inventory.setItem(
         slotPrev(plugin, size),
         GuiKit.nav(
            plugin,
            gui(plugin),
            "prev",
            Material.ARROW,
            "top.prev-name",
            "top.prev-lore",
            "page",
            Integer.toString(Math.max(1, holder.page() - 1)),
            "pages",
            Integer.toString(pages)
         )
      );
      inventory.setItem(
         slotCategories(plugin, size),
         Items.create(
            material(plugin, "top.categories-material", Material.NETHER_STAR),
            plugin.messages().raw("top.category-name", "category", categoryLabel(plugin, category)),
            loreLines(plugin, "top.category-lore")
         )
      );
      inventory.setItem(
         slotInfo(plugin, size),
         Items.create(
            material(plugin, "top.info-material", Material.PLAYER_HEAD),
            plugin.messages().raw("top.info-name"),
            infoLore(plugin, category, holder, total, pages)
         )
      );
      inventory.setItem(slotClose(plugin, size), GuiKit.nav(plugin, gui(plugin), "close", Material.BARRIER, "top.close-name", "top.close-lore"));
      inventory.setItem(
         slotNext(plugin, size),
         GuiKit.nav(
            plugin,
            gui(plugin),
            "next",
            Material.ARROW,
            "top.next-name",
            "top.page-lore",
            "page",
            Integer.toString(Math.min(pages, holder.page() + 1)),
            "pages",
            Integer.toString(pages)
         )
      );
   }

   public static void renderCategories(W2NSMP plugin, Inventory inventory, LeaderboardHolder holder) {
      TopCategory[] categories = TopCategory.values();

      for (int index = 0; index < categories.length; index++) {
         TopCategory category = categories[index];
         boolean selected = holder.category() == category;
         inventory.setItem(
            0 + index,
            Items.create(
               category.icon(),
               plugin.messages().raw(selected ? "top.category-selected" : "top.category-name", "category", categoryLabel(plugin, category)),
               loreLines(plugin, selected ? "top.category-lore-selected" : "top.category-lore")
            )
         );
      }

      inventory.setItem(slotBack(plugin), GuiKit.nav(plugin, gui(plugin), "back", Material.ARROW, "top.back-name", "top.back-lore"));
      inventory.setItem(slotCategoriesClose(plugin), GuiKit.nav(plugin, gui(plugin), "close", Material.BARRIER, "top.close-name", "top.close-lore"));
   }

   public static TopCategory defaultCategory(W2NSMP plugin) {
      TopCategory configured = TopCategory.fromKey(plugin.config().leaderboardDefaultCategory());
      return configured == null ? TopCategory.MONEY : configured;
   }

   public static String categoryLabel(W2NSMP plugin, TopCategory category) {
      return plugin.messages().raw(category.messageKey());
   }

   public static String displayValue(W2NSMP plugin, TopCategory category, long value) {
      boolean moneyLike = category != null && (category.isMoney() || category.isBounty() || category.statType() == StatType.HIGHEST_MONEY);
      if (moneyLike && plugin.economy() != null && plugin.economy().isEnabled()) {
         return plugin.economy().format(value);
      } else {
         return category != null && category.statType() == StatType.PLAYTIME && plugin.stats() != null
            ? plugin.stats().formatPlaytime(value)
            : Long.toString(value);
      }
   }

   private static List<String> entryLore(W2NSMP plugin, TopCategory category, LeaderboardEntry entry, int total) {
      List<String> lore = new ArrayList<>(3);
      lore.addAll(
         loreLines(plugin, "top.entry-lore-value", "category", categoryLabel(plugin, category), "value", displayValue(plugin, category, entry.value()))
      );
      lore.addAll(loreLines(plugin, "top.entry-lore-rank", "rank", Integer.toString(entry.rank()), "total", Integer.toString(total)));
      return lore;
   }

   private static List<String> infoLore(W2NSMP plugin, TopCategory category, LeaderboardHolder holder, int total, int pages) {
      int rank = plugin.stats() == null ? 0 : plugin.stats().rankOf(holder.owner(), category);
      List<String> lore = new ArrayList<>(3);
      if (rank <= 0) {
         lore.addAll(loreLines(plugin, "top.info-lore-none"));
      } else {
         lore.addAll(loreLines(plugin, "top.info-lore-rank", "rank", Integer.toString(rank)));
         lore.addAll(
            loreLines(
               plugin,
               "top.info-lore-value",
               "category",
               categoryLabel(plugin, category),
               "value",
               displayValue(plugin, category, plugin.stats() == null ? 0L : plugin.stats().value(holder.owner(), category))
            )
         );
      }

      lore.addAll(loreLines(plugin, "top.page-lore", "page", Integer.toString(holder.page()), "pages", Integer.toString(pages)));
      return lore;
   }

   private static void decorate(W2NSMP plugin, Inventory inventory, int size) {
      GuiConfig gui = gui(plugin);
      if (gui.fillerEnabled()) {
         String fillerName = gui.fillerName();
         ItemStack filler = GuiKit.filler(plugin, gui, Material.GRAY_STAINED_GLASS_PANE, "top.filler-material");
         Items.fillEmpty(inventory, filler);
      }
   }

   private static Material icon(TopCategory category) {
      Material icon = category.icon();
      return icon == null ? Material.PAPER : icon;
   }

   private static List<String> loreLines(W2NSMP plugin, String key, String... placeholders) {
      List<String> lines = plugin.messages().rawList(key, placeholders);
      if (!lines.isEmpty()) {
         return lines;
      } else {
         return plugin.messages().has(key) ? List.of(plugin.messages().raw(key, placeholders)) : List.of();
      }
   }

   private static Material material(W2NSMP plugin, String key, Material fallback) {
      return GuiKit.material(plugin, key, fallback);
   }
}
