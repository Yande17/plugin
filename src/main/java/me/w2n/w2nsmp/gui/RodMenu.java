package me.w2n.w2nsmp.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.config.GuiConfig;
import me.w2n.w2nsmp.fishing.FishingItem;
import me.w2n.w2nsmp.fishing.FishingService;
import me.w2n.w2nsmp.utility.GuiKit;
import me.w2n.w2nsmp.utility.Items;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/**
 * Menu /rod (v1.4.0): rod di tangan, level, XP, upgrade, attachment, dan statistik efek.
 *
 * <p>Menu ini <b>tidak pernah menyimpan item milik pemain di dalam GUI</b> - semua operasi
 * (pasang/copot attachment, upgrade) membaca rod di tangan utama saat klik, mengubah PDC-nya,
 * lalu menulis balik. Karena tidak ada item yang berpindah ke GUI, tidak ada jalur dupe/hilang
 * (item requirement 15).
 */
public final class RodMenu {
   /** Pemain yang sedang membuka menu rod (pola OPEN sama seperti GUI skill). */
   private static final Map<UUID, RodMenuHolder> OPEN = new ConcurrentHashMap<>();

   private RodMenu() {
   }

   public static GuiConfig gui(W2NSMP plugin) {
      return plugin.guiConfigs().of("rod");
   }

   public static int size(W2NSMP plugin) {
      return gui(plugin).size(27);
   }

   public static int slot(W2NSMP plugin, String path, int fallback) {
      int slot = gui(plugin).slot("slots." + path, fallback);
      return slot >= 0 && slot < size(plugin) ? slot : fallback;
   }

   public static int rodSlot(W2NSMP plugin) {
      return slot(plugin, "rod", 4);
   }

   /** Slot attachment pertama; slot berikutnya berurutan (10, 11, 12, ...). */
   public static int attachmentSlot(W2NSMP plugin) {
      return slot(plugin, "attachments", 10);
   }

   public static int statsSlot(W2NSMP plugin) {
      return slot(plugin, "stats", 14);
   }

   public static int upgradeSlot(W2NSMP plugin) {
      return slot(plugin, "upgrade", 16);
   }

   public static int closeSlot(W2NSMP plugin) {
      return slot(plugin, "close", 22);
   }

   public static void open(W2NSMP plugin, Player player) {
      FishingService fishing = plugin.fishing();
      if (fishing == null) {
         return;
      }

      RodMenuHolder holder = new RodMenuHolder(player.getUniqueId());
      Inventory inventory = Bukkit.createInventory(holder, size(plugin), gui(plugin).title("fishing.gui-title"));
      holder.setInventory(inventory);
      render(plugin, inventory, player);
      OPEN.put(player.getUniqueId(), holder);
      player.openInventory(inventory);
      plugin.guiSounds().play(player, gui(plugin), "open");
   }

   public static void render(W2NSMP plugin, Inventory inventory, Player player) {
      FishingService fishing = plugin.fishing();
      if (fishing == null) {
         return;
      }

      GuiConfig gui = gui(plugin);
      int size = inventory.getSize();
      int attachmentStart = attachmentSlot(plugin);
      int slots = fishing.attachmentSlots();

      GuiKit.shell(plugin, gui, inventory, Material.LIGHT_BLUE_STAINED_GLASS_PANE, "fishing.filler-material",
         Material.BLACK_STAINED_GLASS_PANE,
         slot -> slot == rodSlot(plugin) || slot == statsSlot(plugin) || slot == upgradeSlot(plugin)
            || slot == closeSlot(plugin) || slot >= attachmentStart && slot < attachmentStart + slots);

      ItemStack rod = player.getInventory().getItemInMainHand();
      boolean holding = fishing.isRod(rod);

      inventory.setItem(rodSlot(plugin), rodCard(plugin, fishing, rod, holding));
      inventory.setItem(statsSlot(plugin), statsCard(plugin, fishing, rod, holding));
      inventory.setItem(upgradeSlot(plugin), upgradeCard(plugin, fishing, player, rod, holding));

      List<String> attached = holding ? fishing.attachments(rod) : List.of();
      for (int index = 0; index < slots; index++) {
         int slot = attachmentStart + index;
         if (slot >= size) {
            break;
         }

         if (index < attached.size()) {
            FishingItem attachment = fishing.item(attached.get(index));
            inventory.setItem(slot, attachmentCard(plugin, attachment));
         } else {
            inventory.setItem(slot, Items.create(
               gui.material("empty-attachment-material", Material.GRAY_STAINED_GLASS_PANE),
               plugin.messages().raw("fishing.slot-empty-name"),
               plugin.messages().rawList("fishing.slot-empty-lore")));
         }
      }

      inventory.setItem(closeSlot(plugin),
         GuiKit.nav(plugin, gui, GuiKit.NAV_CLOSE, Material.BARRIER, "fishing.close-name", "fishing.close-lore"));
   }

   private static ItemStack rodCard(W2NSMP plugin, FishingService fishing, ItemStack rod, boolean holding) {
      if (!holding) {
         return Items.create(Material.FISHING_ROD,
            plugin.messages().raw("fishing.no-rod-name"),
            plugin.messages().rawList("fishing.no-rod-lore"));
      }

      int level = fishing.rodLevel(rod);
      double xp = fishing.rodXp(rod);
      double needed = fishing.xpNeeded(level + 1);
      return Items.create(Material.FISHING_ROD,
         plugin.messages().raw("fishing.rod-name", "level", Integer.toString(level)),
         plugin.messages().rawList("fishing.rod-card-lore",
            "level", Integer.toString(level),
            "max", Integer.toString(fishing.rodMaxLevel()),
            "xp", format(xp),
            "needed", level >= fishing.rodMaxLevel() ? "-" : format(needed)));
   }

   private static ItemStack statsCard(W2NSMP plugin, FishingService fishing, ItemStack rod, boolean holding) {
      List<String> lore = new ArrayList<>();
      if (holding) {
         for (String effect : List.of("bite-speed", "custom-chance", "rarity-boost", "value", "xp", "double-catch")) {
            double total = fishing.effectTotal(rod, effect);
            if (total > 0.0D) {
               lore.add(plugin.messages().raw("fishing.stats-line",
                  "effect", plugin.messages().raw("fishing.effect." + effect),
                  "value", format(total)));
            }
         }
      }

      if (lore.isEmpty()) {
         lore.addAll(plugin.messages().rawList("fishing.stats-empty-lore"));
      }

      return Items.create(gui(plugin).material("stats-material", Material.BOOK),
         plugin.messages().raw("fishing.stats-name"), lore);
   }

   private static ItemStack upgradeCard(W2NSMP plugin, FishingService fishing, Player player, ItemStack rod, boolean holding) {
      if (!holding) {
         return Items.create(Material.ANVIL,
            plugin.messages().raw("fishing.upgrade-name"),
            plugin.messages().rawList("fishing.no-rod-lore"));
      }

      int level = fishing.rodLevel(rod);
      if (level >= fishing.rodMaxLevel()) {
         return Items.create(Material.ANVIL,
            plugin.messages().raw("fishing.upgrade-name"),
            plugin.messages().rawList("fishing.upgrade-max-lore", "max", Integer.toString(fishing.rodMaxLevel())));
      }

      List<String> lore = new ArrayList<>(plugin.messages().rawList("fishing.upgrade-lore",
         "next", Integer.toString(level + 1)));
      double needed = fishing.xpNeeded(level + 1);
      if (needed > 0.0D) {
         lore.add(plugin.messages().raw("fishing.upgrade-xp-line",
            "xp", format(fishing.rodXp(rod)), "needed", format(needed)));
      }

      for (Map.Entry<String, Integer> entry : fishing.upgradeCost(level + 1).entrySet()) {
         FishingItem item = fishing.item(entry.getKey());
         lore.add(plugin.messages().raw("fishing.upgrade-cost-line",
            "amount", Integer.toString(entry.getValue()),
            "item", item == null ? entry.getKey() : item.itemName(),
            "have", Integer.toString(countItems(plugin, player, entry.getKey()))));
      }

      lore.addAll(plugin.messages().rawList("fishing.upgrade-click-lore"));
      return Items.create(Material.ANVIL, plugin.messages().raw("fishing.upgrade-name"), lore);
   }

   private static ItemStack attachmentCard(W2NSMP plugin, FishingItem attachment) {
      if (attachment == null) {
         return Items.create(Material.GRAY_STAINED_GLASS_PANE,
            plugin.messages().raw("fishing.slot-empty-name"), List.of());
      }

      List<String> lore = new ArrayList<>(attachment.lore());
      lore.addAll(plugin.messages().rawList("fishing.attachment-remove-lore"));
      return Items.create(attachment.material(), attachment.itemName(), lore);
   }

   /** Hitung item pancing custom ber-id tertentu di inventory pemain (identitas via PDC). */
   public static int countItems(W2NSMP plugin, Player player, String itemId) {
      FishingService fishing = plugin.fishing();
      if (fishing == null || player == null || itemId == null) {
         return 0;
      }

      int total = 0;

      try {
         int size = player.getInventory().getSize();

         for (int slot = 0; slot < size; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!Items.isEmpty(stack) && itemId.equalsIgnoreCase(fishing.itemId(stack))) {
               total += stack.getAmount();
            }
         }
      } catch (Throwable ignored) {
      }

      return total;
   }

   // ------------------------------------------------------------------ //
   // Registrasi menu (pola sama dengan GUI lain)
   // ------------------------------------------------------------------ //

   public static boolean isMenu(Inventory inventory) {
      return inventory != null && inventory.getHolder() instanceof RodMenuHolder;
   }

   public static boolean isOwnerMenu(Object player) {
      return player instanceof Player p && OPEN.containsKey(p.getUniqueId());
   }

   public static RodMenuHolder holderOf(Player player) {
      return player == null ? null : OPEN.get(player.getUniqueId());
   }

   public static void markClosed(Object player, Inventory inventory) {
      if (player instanceof Player p) {
         RodMenuHolder holder = OPEN.get(p.getUniqueId());
         if (holder != null && (inventory == null || holder.getInventory() == inventory)) {
            OPEN.remove(p.getUniqueId());
         }
      }
   }

   public static void closeAll(W2NSMP plugin) {
      for (Player player : Bukkit.getOnlinePlayers()) {
         try {
            InventoryView view = player.getOpenInventory();
            if (view != null && isMenu(view.getTopInventory())) {
               player.closeInventory();
            }
         } catch (Throwable throwable) {
            plugin.debug("Rod: gagal menutup menu untuk " + player.getName() + " (" + throwable + ").");
         }
      }

      OPEN.clear();
   }

   private static String format(double value) {
      return value == Math.floor(value) && !Double.isInfinite(value)
         ? Long.toString((long)value)
         : String.format(Locale.ROOT, "%.1f", value);
   }
}
