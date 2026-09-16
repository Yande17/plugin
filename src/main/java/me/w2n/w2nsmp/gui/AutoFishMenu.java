package me.w2n.w2nsmp.gui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.config.GuiConfig;
import me.w2n.w2nsmp.fishing.AutoFishService;
import me.w2n.w2nsmp.fishing.FishingItem;
import me.w2n.w2nsmp.fishing.FishingService;
import me.w2n.w2nsmp.skill.SkillService;
import me.w2n.w2nsmp.skill.SkillType;
import me.w2n.w2nsmp.utility.GuiKit;
import me.w2n.w2nsmp.utility.Items;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/**
 * Menu /autofishing (v1.4.1): status ON/OFF, rod sekarang, kecepatan, auto-sell,
 * statistik sesi, dan daftar syarat. Murni tampilan - tidak ada item pemain di GUI.
 */
public final class AutoFishMenu {
   private static final Map<UUID, AutoFishMenuHolder> OPEN = new ConcurrentHashMap<>();

   private AutoFishMenu() {
   }

   public static GuiConfig gui(W2NSMP plugin) {
      return plugin.guiConfigs().of("autofish");
   }

   public static int size(W2NSMP plugin) {
      return gui(plugin).size(27);
   }

   private static int slot(W2NSMP plugin, String path, int fallback) {
      int slot = gui(plugin).slot("slots." + path, fallback);
      return slot >= 0 && slot < size(plugin) ? slot : fallback;
   }

   public static int toggleSlot(W2NSMP plugin) {
      return slot(plugin, "toggle", 11);
   }

   public static int rodSlot(W2NSMP plugin) {
      return slot(plugin, "rod", 13);
   }

   public static int sellSlot(W2NSMP plugin) {
      return slot(plugin, "auto-sell", 15);
   }

   public static int requirementsSlot(W2NSMP plugin) {
      return slot(plugin, "requirements", 21);
   }

   public static int statsSlot(W2NSMP plugin) {
      return slot(plugin, "stats", 23);
   }

   public static int closeSlot(W2NSMP plugin) {
      return slot(plugin, "close", 26);
   }

   public static void open(W2NSMP plugin, Player player) {
      if (player == null) {
         return;
      }

      AutoFishMenuHolder holder = new AutoFishMenuHolder(player.getUniqueId());
      Inventory inventory = Bukkit.createInventory(holder, size(plugin),
         gui(plugin).title("fishing.autofish.gui-title", "player", player.getName()));
      holder.setInventory(inventory);
      render(plugin, inventory, player);
      player.openInventory(inventory);
      OPEN.put(player.getUniqueId(), holder);
      plugin.guiSounds().play(player, gui(plugin), "open");
   }

   public static void render(W2NSMP plugin, Inventory inventory, Player player) {
      if (inventory == null || player == null) {
         return;
      }

      AutoFishService auto = plugin.autoFish();
      FishingService fishing = plugin.fishing();
      int toggle = toggleSlot(plugin);
      int rodCard = rodSlot(plugin);
      int sell = sellSlot(plugin);
      int requirements = requirementsSlot(plugin);
      int stats = statsSlot(plugin);
      int close = closeSlot(plugin);
      Set<Integer> content = new HashSet<>(List.of(toggle, rodCard, sell, requirements, stats, close));

      for (int index = 0; index < inventory.getSize(); index++) {
         if (!content.contains(index)) {
            inventory.setItem(index, null);
         }
      }

      GuiKit.shell(plugin, gui(plugin), inventory, Material.LIGHT_BLUE_STAINED_GLASS_PANE,
         "fishing.filler-material", Material.BLUE_STAINED_GLASS_PANE, content::contains);

      boolean on = auto != null && auto.isActive(player);
      AutoFishService.Session session = auto == null ? null : auto.session(player);
      String interval = auto == null ? "-" : Integer.toString(auto.intervalSeconds());

      // Kartu ON/OFF.
      inventory.setItem(toggle, Items.create(
         on ? gui(plugin).material("on-material", Material.LIME_DYE)
            : gui(plugin).material("off-material", Material.GRAY_DYE),
         plugin.messages().raw(on ? "fishing.autofish.toggle-on-name" : "fishing.autofish.toggle-off-name"),
         plugin.messages().rawList(on ? "fishing.autofish.toggle-on-lore" : "fishing.autofish.toggle-off-lore",
            "interval", interval)));

      // Kartu rod sekarang.
      ItemStack rod = player.getInventory().getItemInMainHand();
      boolean holdingRod = fishing != null && fishing.isRod(rod);
      if (holdingRod) {
         inventory.setItem(rodCard, Items.create(Material.FISHING_ROD,
            plugin.messages().raw("fishing.autofish.rod-name",
               "level", Integer.toString(fishing.rodLevel(rod)),
               "max", Integer.toString(fishing.rodMaxLevel())),
            plugin.messages().rawList("fishing.autofish.rod-lore",
               "level", Integer.toString(fishing.rodLevel(rod)),
               "max", Integer.toString(fishing.rodMaxLevel()),
               "interval", interval)));
      } else {
         inventory.setItem(rodCard, Items.create(
            gui(plugin).material("no-rod-material", Material.BARRIER),
            plugin.messages().raw("fishing.no-rod-name"),
            plugin.messages().rawList("fishing.no-rod-lore")));
      }

      // Kartu auto-sell.
      boolean sellAllowed = auto != null && auto.allowAutoSell();
      boolean sellOn = session != null && session.autoSell();
      inventory.setItem(sell, Items.create(
         sellAllowed ? gui(plugin).material("sell-material", Material.GOLD_INGOT)
            : gui(plugin).material("sell-off-material", Material.GRAY_DYE),
         plugin.messages().raw(sellAllowed
            ? (sellOn ? "fishing.autofish.sell-on-name" : "fishing.autofish.sell-off-name")
            : "fishing.autofish.sell-unavailable-name"),
         plugin.messages().rawList(sellAllowed
            ? (sellOn ? "fishing.autofish.sell-on-lore" : "fishing.autofish.sell-off-lore")
            : "fishing.autofish.sell-unavailable-lore")));

      // Kartu syarat.
      inventory.setItem(requirements, Items.create(
         gui(plugin).material("requirements-material", Material.BOOK),
         plugin.messages().raw("fishing.autofish.req-name"),
         requirementLore(plugin, auto, fishing, player)));

      // Kartu statistik sesi.
      String caught = session == null ? "0" : Integer.toString(session.caught());
      String custom = session == null ? "0" : Integer.toString(session.customCaught());
      String sold = session == null ? "0" : Long.toString(session.soldTotal());
      inventory.setItem(stats, Items.create(
         gui(plugin).material("stats-material", Material.PAPER),
         plugin.messages().raw("fishing.autofish.stats-name"),
         plugin.messages().rawList("fishing.autofish.stats-lore",
            "caught", caught, "custom", custom, "sold", sold)));

      inventory.setItem(close, GuiKit.nav(plugin, gui(plugin), GuiKit.NAV_CLOSE, Material.BARRIER,
         "fishing.close-name", "fishing.close-lore"));
   }

   /** Lore kartu syarat: tiap syarat aktif ditampilkan dengan status terpenuhi/belum. */
   private static List<String> requirementLore(W2NSMP plugin, AutoFishService auto, FishingService fishing, Player player) {
      List<String> lore = new ArrayList<>();
      if (auto == null || fishing == null) {
         return lore;
      }

      String yes = plugin.messages().raw("fishing.autofish.req-ok");
      String no = plugin.messages().raw("fishing.autofish.req-missing");
      ItemStack rod = player.getInventory().getItemInMainHand();
      boolean holdingRod = fishing.isRod(rod);

      if (auto.requireRod()) {
         lore.add(plugin.messages().raw("fishing.autofish.req-rod", "status", holdingRod ? yes : no));
      }

      if (auto.minFishingLevel() > 0) {
         SkillService skills = plugin.skills();
         int level = skills == null ? 0 : skills.level(player, SkillType.FISHING);
         lore.add(plugin.messages().raw("fishing.autofish.req-level",
            "level", Integer.toString(auto.minFishingLevel()),
            "status", level >= auto.minFishingLevel() ? yes : no));
      }

      if (!auto.requiredAttachment().isEmpty()) {
         FishingItem item = fishing.item(auto.requiredAttachment());
         boolean has = holdingRod && fishing.attachments(rod).contains(auto.requiredAttachment());
         lore.add(plugin.messages().raw("fishing.autofish.req-attachment",
            "name", item == null ? auto.requiredAttachment() : item.itemName(),
            "status", has ? yes : no));
      }

      if (!auto.requiredItem().isEmpty()) {
         FishingItem item = fishing.item(auto.requiredItem());
         lore.add(plugin.messages().raw("fishing.autofish.req-item",
            "name", item == null ? auto.requiredItem() : item.itemName(),
            "status", auto.requirementBlocking(player) == null
               || !"fishing.autofish.need-item".equals(auto.requirementBlocking(player)) ? yes : no));
      }

      if (auto.requireWater()) {
         lore.add(plugin.messages().raw("fishing.autofish.req-water"));
      }

      return lore;
   }

   /** Kunci aksi slot; null bila bukan tombol. */
   public static String keyAt(W2NSMP plugin, int rawSlot) {
      if (rawSlot == toggleSlot(plugin)) {
         return "toggle";
      }

      if (rawSlot == sellSlot(plugin)) {
         return "auto-sell";
      }

      if (rawSlot == closeSlot(plugin)) {
         return "close";
      }

      return null;
   }

   public static boolean isMenu(Inventory inventory) {
      if (inventory == null) {
         return false;
      }

      for (AutoFishMenuHolder holder : OPEN.values()) {
         if (holder.getInventory() == inventory) {
            return true;
         }
      }

      return false;
   }

   public static void markClosed(HumanEntity entity, Inventory inventory) {
      if (entity == null) {
         return;
      }

      AutoFishMenuHolder holder = OPEN.get(entity.getUniqueId());
      if (holder != null && (inventory == null || holder.getInventory() == inventory)) {
         OPEN.remove(entity.getUniqueId());
      }
   }

   public static void closeAll(W2NSMP plugin) {
      for (Player player : Bukkit.getOnlinePlayers()) {
         try {
            InventoryView view = player.getOpenInventory();
            if (view != null && (view.getTopInventory().getHolder() instanceof AutoFishMenuHolder
               || isMenu(view.getTopInventory()))) {
               player.closeInventory();
            }
         } catch (Throwable throwable) {
            plugin.debug("Autofish: gagal menutup menu untuk " + player.getName() + " (" + throwable + ").");
         }
      }

      OPEN.clear();
   }
}
