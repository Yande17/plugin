package me.w2n.w2nsmp.gui;

import java.util.ArrayList;
import java.util.List;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.config.GuiConfig;
import me.w2n.w2nsmp.player.PlayerSettingsService;
import me.w2n.w2nsmp.scoreboard.ScoreboardLine;
import me.w2n.w2nsmp.utility.GuiKit;
import me.w2n.w2nsmp.utility.Items;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

public final class SettingsMenu {
   public static final int SLOT_SCOREBOARD = 10;
   public static final int SLOT_NAMETAG_MONEY = 11;
   public static final int SLOT_SOUNDS = 12;
   public static final int SLOT_NOTIFICATIONS = 13;
   public static final int SLOT_TELEPORT_COUNTDOWN = 14;
   public static final int SLOT_NOTIFY_BOUNTY = 15;
   public static final int SLOT_NOTIFY_AUCTION = 16;
   public static final int SLOT_NOTIFY_TPA = 17;
   public static final int SLOT_LINE_START = 20;
   public static final int SLOT_INFO = 49;
   public static final int SLOT_CLOSE = 53;

   /**
    * Kategori pengaturan (v1.4.0). Halaman utama menampilkan kartu kategori; klik kartu ->
    * sub-halaman berisi toggle milik kategori itu. Isi tiap kategori:
    * scoreboard (toggle sidebar + per-baris), notifications (semua notifikasi),
    * gameplay (hitung mundur teleport), visual (nametag uang), misc (bunyi GUI).
    */
   public static final List<String> CATEGORIES = List.of("scoreboard", "notifications", "gameplay", "visual", "misc");

   /** Slot konten sub-halaman: baris 1-4 kolom 1-7 (dibaca berurutan). */
   private static final int[] CONTENT_SLOTS = {
      10, 11, 12, 13, 14, 15, 16,
      19, 20, 21, 22, 23, 24, 25,
      28, 29, 30, 31, 32, 33, 34,
      37, 38, 39, 40, 41, 42, 43
   };

   private SettingsMenu() {
   }

   public static GuiConfig gui(W2NSMP plugin) {
      return plugin.guiConfigs().settings();
   }

   public static int size(W2NSMP plugin) {
      return gui(plugin).size(54);
   }

   public static int slot(W2NSMP plugin, String path, int fallback) {
      int slot = gui(plugin).slot("slots." + path, fallback);
      return slot < size(plugin) ? slot : fallback;
   }

   public static int lineStart(W2NSMP plugin) {
      return slot(plugin, "line-start", 20);
   }

   public static void open(W2NSMP plugin, Player player) {
      SettingsMenuHolder holder = new SettingsMenuHolder(player.getUniqueId());
      Inventory inventory = Bukkit.createInventory(holder, size(plugin), gui(plugin).title("setting.gui-title", "player", player.getName()));
      holder.setInventory(inventory);
      render(plugin, inventory, player);
      player.openInventory(inventory);
      plugin.guiSounds().play(player, gui(plugin), "open");
   }

   public static void render(W2NSMP plugin, Inventory inventory, Player player) {
      GuiConfig gui = gui(plugin);
      String category = inventory.getHolder() instanceof SettingsMenuHolder holder ? holder.category() : "";

      // Kumpulkan slot yang BENAR-BENAR dipakai halaman ini. Semua slot lain dikosongkan
      // dulu lalu diisi filler - tanpa ini, item halaman sebelumnya (kartu kategori) tetap
      // tersisa saat pindah Main -> Kategori (ghost item, bug v1.4.0).
      int[] cards = new int[CATEGORIES.size()];

      for (int index = 0; index < cards.length; index++) {
         cards[index] = categoryCardSlot(plugin, CATEGORIES.get(index), index);
      }

      java.util.Set<Integer> content = pageContent(
         category,
         category.isEmpty() ? 0 : categoryKeys(plugin, category).size(),
         cards,
         slot(plugin, "back", 45),
         slot(plugin, "info", 49),
         slot(plugin, "close", 53)
      );

      // Kosongkan slot non-konten secara eksplisit: shell() hanya menimpa bila filler aktif,
      // jadi pembersihan manual ini yang menjamin tidak ada item lama tersisa.
      for (int slot = 0; slot < inventory.getSize(); slot++) {
         if (!content.contains(slot)) {
            inventory.setItem(slot, null);
         }
      }

      GuiKit.shell(
         plugin,
         gui,
         inventory,
         Material.GRAY_STAINED_GLASS_PANE,
         "setting.filler-material",
         Material.BLACK_STAINED_GLASS_PANE,
         content::contains
      );

      if (category.isEmpty()) {
         renderHome(plugin, inventory, player);
      } else {
         renderCategory(plugin, inventory, player, category);
         inventory.setItem(slot(plugin, "back", 45),
            GuiKit.nav(plugin, gui, GuiKit.NAV_BACK, Material.ARROW, "setting.back-name", "setting.back-lore"));
      }

      inventory.setItem(
         slot(plugin, "info", 49),
         Items.create(
            material(plugin, "setting.info-material", Material.BOOK),
            plugin.messages().raw("setting.info-name", "player", player.getName()),
            plugin.messages().rawList("setting.info-lore", "file", plugin.settings().file().getName())
         )
      );
      inventory.setItem(slot(plugin, "close", 53), GuiKit.nav(plugin, gui, "close", Material.BARRIER, "setting.close-name", "setting.close-lore"));
   }

   /** Halaman utama: satu kartu per kategori (baris tengah). */
   private static void renderHome(W2NSMP plugin, Inventory inventory, Player player) {
      int index = 0;

      for (String category : CATEGORIES) {
         int slot = categoryCardSlot(plugin, category, index);
         index++;
         if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, Items.create(
               categoryMaterial(plugin, category),
               plugin.messages().raw("setting.category." + category + ".name"),
               plugin.messages().rawList("setting.category." + category + ".lore",
                  "count", Integer.toString(categoryKeys(plugin, category).size()))));
         }
      }
   }

   /** Sub-halaman kategori: semua toggle milik kategori itu di slot konten berurutan. */
   private static void renderCategory(W2NSMP plugin, Inventory inventory, Player player, String category) {
      List<String> keys = categoryKeys(plugin, category);

      for (int index = 0; index < keys.size() && index < CONTENT_SLOTS.length; index++) {
         String key = keys.get(index);
         int slot = CONTENT_SLOTS[index];
         if (slot >= inventory.getSize()) {
            break;
         }

         inventory.setItem(slot, toggleItem(plugin, key, valueOf(plugin, player, key),
            locked(plugin, key, player), iconFor(key)));
      }
   }

   /** Slot kartu kategori di halaman utama (bisa dipindah lewat gui/settings.yml slots.category-<id>). */
   public static int categoryCardSlot(W2NSMP plugin, String category, int index) {
      return slot(plugin, "category-" + category, 20 + index);
   }

   /**
    * Logika murni v1.4.1: slot konten sebuah halaman /setting. Halaman utama hanya berisi
    * kartu kategori; sub-halaman hanya toggle kategori itu + tombol back. Slot di luar
    * himpunan ini WAJIB dikosongkan saat render agar tidak ada ghost item antar-halaman.
    */
   public static java.util.Set<Integer> pageContent(
      String category, int keyCount, int[] cardSlots, int backSlot, int infoSlot, int closeSlot
   ) {
      java.util.Set<Integer> content = new java.util.HashSet<>();
      if (category == null || category.isEmpty()) {
         for (int slot : cardSlots) {
            content.add(Integer.valueOf(slot));
         }
      } else {
         int count = Math.min(Math.max(keyCount, 0), CONTENT_SLOTS.length);

         for (int index = 0; index < count; index++) {
            content.add(Integer.valueOf(CONTENT_SLOTS[index]));
         }

         content.add(Integer.valueOf(backSlot));
      }

      content.add(Integer.valueOf(infoSlot));
      content.add(Integer.valueOf(closeSlot));
      return content;
   }

   /** Kunci toggle milik satu kategori. Baris scoreboard ikut kategori "scoreboard". */
   public static List<String> categoryKeys(W2NSMP plugin, String category) {
      List<String> keys = new ArrayList<>();

      switch (category == null ? "" : category) {
         case "scoreboard" -> {
            keys.add("scoreboard");
            if (plugin.scoreboard() != null) {
               for (ScoreboardLine line : plugin.scoreboard().lines()) {
                  keys.add("line:" + line.key());
               }
            }
         }
         case "notifications" -> {
            keys.add("notifications");
            keys.add("notify-bounty");
            keys.add("notify-auction");
            keys.add("notify-tpa");
         }
         case "gameplay" -> {
            keys.add("teleport-countdown");
            // v1.5.1: Night Vision pribadi.
            keys.add("night-vision");
         }
         case "visual" -> {
            keys.add("nametag-money");
            // v1.7.0 (PHASE 3): toggle bounty terpisah & independen dari Money.
            keys.add("bounty-display");
         }
         case "misc" -> keys.add("sounds");
         default -> {
         }
      }

      return keys;
   }

   /** Nilai toggle sekarang (dipakai renderCategory). */
   private static boolean valueOf(W2NSMP plugin, Player player, String key) {
      if (key.startsWith("line:")) {
         return plugin.scoreboard() != null
            && plugin.scoreboard().isLineVisible(player.getUniqueId(), key.substring("line:".length()));
      }

      return switch (key) {
         case "scoreboard" -> plugin.scoreboard() != null && plugin.scoreboard().visibleFor(player);
         case "nametag-money" -> plugin.settings().nametagMoney(player);
         case "bounty-display" -> plugin.settings().bountyDisplay(player);
         case "sounds" -> plugin.settings().sounds(player);
         case "notifications" -> plugin.settings().notifications(player);
         case "teleport-countdown" -> plugin.settings().teleportCountdown(player);
         case "night-vision" -> plugin.settings().nightVision(player);
         case "notify-bounty" -> plugin.settings().bountyNotifications(player);
         case "notify-auction" -> plugin.settings().auctionNotifications(player);
         case "notify-tpa" -> plugin.settings().tpaNotifications(player);
         default -> false;
      };
   }

   private static Material iconFor(String key) {
      if (key.startsWith("line:")) {
         return Material.OAK_SIGN;
      }

      return switch (key) {
         case "scoreboard" -> Material.CLOCK;
         case "nametag-money" -> Material.NAME_TAG;
         case "bounty-display" -> Material.TARGET;
         case "sounds" -> Material.NOTE_BLOCK;
         case "notifications" -> Material.BELL;
         case "teleport-countdown" -> Material.ENDER_PEARL;
         case "night-vision" -> Material.GOLDEN_CARROT;
         case "notify-bounty" -> Material.GOLD_INGOT;
         case "notify-auction" -> Material.EMERALD;
         case "notify-tpa" -> Material.COMPASS;
         default -> Material.GRAY_DYE;
      };
   }

   private static Material categoryMaterial(W2NSMP plugin, String category) {
      Material fallback = switch (category) {
         case "scoreboard" -> Material.CLOCK;
         case "notifications" -> Material.BELL;
         case "gameplay" -> Material.ENDER_PEARL;
         case "visual" -> Material.NAME_TAG;
         default -> Material.CHEST;
      };
      return gui(plugin).material("category-materials." + category, fallback);
   }

   private static ItemStack toggleItem(W2NSMP plugin, String key, boolean value, boolean locked, Material icon) {
      String state = locked ? plugin.messages().raw("setting.state-locked") : plugin.messages().raw(value ? "setting.state-on" : "setting.state-off");
      // v1.5.1: %action% = ajakan klik sesuai keadaan ("Click to disable"/"Click to enable").
      String action = locked ? "" : plugin.messages().raw(value ? "setting.action-disable" : "setting.action-enable");
      String[] placeholders = new String[]{"state", state, "action", action, "setting", label(plugin, key), "line", label(plugin, key), "key", key};
      GuiConfig gui = gui(plugin);
      Material material = materialFor(plugin, value, locked, icon);
      String name = gui.name("buttons." + key.replace(':', '-') + ".name");
      List<String> lore = gui.lore("buttons." + key.replace(':', '-') + ".lore");
      return Items.create(
         material,
         name == null ? plugin.messages().raw("setting.item-name", placeholders) : plugin.messages().apply(name, placeholders),
         lore.isEmpty() ? plugin.messages().rawList("setting.item-lore", placeholders) : plugin.messages().applyList(lore, placeholders)
      );
   }

   public static String label(W2NSMP plugin, String key) {
      if (key != null && key.startsWith("line:")) {
         String lineKey = key.substring("line:".length());
         String translated = plugin.messages().has("stats.category." + lineKey) ? plugin.messages().raw("stats.category." + lineKey) : lineKey;
         return plugin.messages().raw("setting.label.line", "line", translated);
      } else {
         String id = key == null ? "" : key;
         return plugin.messages().has("setting.label." + id) ? plugin.messages().raw("setting.label." + id) : id;
      }
   }

   private static Material materialFor(W2NSMP plugin, boolean value, boolean locked, Material fallback) {
      GuiConfig gui = gui(plugin);
      if (locked) {
         return gui.material("materials.locked", Material.BARRIER);
      }

      Material inactive = gui.material("materials.inactive", Material.GRAY_DYE);
      return value ? gui.material("materials.active", fallback) : inactive;
   }

   public static List<String> keys(W2NSMP plugin) {
      List<String> keys = new ArrayList<>(
         List.of("scoreboard", "nametag-money", "bounty-display", "sounds", "notifications", "teleport-countdown", "notify-bounty", "notify-auction", "notify-tpa")
      );
      if (plugin.scoreboard() != null) {
         for (ScoreboardLine line : plugin.scoreboard().lines()) {
            keys.add("line:" + line.key());
         }
      }

      return keys;
   }

   public static boolean toggle(W2NSMP plugin, Player player, String key) {
      if (key == null) {
         return false;
      }

      PlayerSettingsService settings = plugin.settings();
      if (key.startsWith("line:")) {
         if (plugin.scoreboard() == null) {
            return false;
         }

         String lineKey = key.substring("line:".length());
         boolean visible = plugin.scoreboard().isLineVisible(player.getUniqueId(), lineKey);
         return plugin.scoreboard().setLineVisible(player, lineKey, !visible);
      } else {
         return switch (key) {
            case "scoreboard" -> plugin.scoreboard() != null && plugin.scoreboard().setVisible(player, !plugin.scoreboard().visibleFor(player));
            case "nametag-money" -> {
               // v1.6.2 (PHASE 2 FIX): preferensi VIEWER - mengatur apakah PEMAIN INI melihat
               // display uang pemain lain di layarnya sendiri. Display miliknya untuk orang
               // lain TIDAK berubah, saldo juga tidak.
               boolean value = !settings.nametagMoney(player);
               boolean changed = settings.set(player, "nametag-money", value);
               if (changed && plugin.nametag() != null) {
                  plugin.nametag().updateViewer(player);
               }

               yield changed;
            }
            case "bounty-display" -> {
               // v1.7.0 (PHASE 3): preferensi VIEWER untuk baris bounty - independen dari Money.
               boolean value = !settings.bountyDisplay(player);
               boolean changed = settings.set(player, "bounty-display", value);
               if (changed && plugin.nametag() != null) {
                  plugin.nametag().updateViewer(player);
               }

               yield changed;
            }
            case "sounds" -> settings.set(player, "sounds", !settings.sounds(player));
            case "notifications" -> settings.set(player, "notifications", !settings.notifications(player));
            case "teleport-countdown" -> settings.set(player, "teleport-countdown", !settings.teleportCountdown(player));
            case "night-vision" -> {
               // v1.5.1: simpan pilihan lalu pasang/cabut efek SEKARANG (bukan menunggu task).
               boolean value = !settings.nightVision(player);
               boolean changed = settings.set(player, "night-vision", value);
               if (changed && plugin.nightVision() != null) {
                  if (value) {
                     plugin.nightVision().apply(player);
                  } else {
                     plugin.nightVision().remove(player);
                  }
               }

               yield changed;
            }
            case "notify-bounty" -> settings.set(player, "notify-bounty", !settings.bountyNotifications(player));
            case "notify-auction" -> settings.set(player, "notify-auction", !settings.auctionNotifications(player));
            case "notify-tpa" -> settings.set(player, "notify-tpa", !settings.tpaNotifications(player));
            default -> false;
         };
      }
   }

   public static boolean locked(W2NSMP plugin, String path, Player player) {
      if (path == null) {
         return true;
      }

      if (!path.startsWith("line:")) {
         return switch (path) {
            case "scoreboard" -> !plugin.config().scoreboardPersonalToggle();
            case "nametag-money" -> !plugin.config().nametagMoneyEnabled();
            case "sounds" -> !plugin.config().soundsEnabled();
            case "night-vision" -> !plugin.config().nightVisionEnabled();
            default -> false;
         };
      } else {
         if (plugin.scoreboard() == null) {
            return true;
         }

         String lineKey = path.substring("line:".length());

         for (ScoreboardLine line : plugin.scoreboard().lines()) {
            if (line.key().equalsIgnoreCase(lineKey)) {
               return !line.enabled() || !line.playerToggle();
            }
         }

         return true;
      }
   }

   /**
    * Kunci pada slot menurut halaman yang sedang terbuka (v1.4.0):
    * halaman utama -> "category:<id>"; sub-halaman -> kunci toggle + "back".
    */
   public static String keyAt(W2NSMP plugin, SettingsMenuHolder holder, int slot) {
      if (slot == slot(plugin, "close", 53)) {
         return "close";
      }

      if (slot == slot(plugin, "info", 49)) {
         return "info";
      }

      String category = holder == null ? "" : holder.category();
      if (category.isEmpty()) {
         int index = 0;

         for (String id : CATEGORIES) {
            if (slot == categoryCardSlot(plugin, id, index)) {
               return "category:" + id;
            }

            index++;
         }

         return null;
      }

      if (slot == slot(plugin, "back", 45)) {
         return "back";
      }

      List<String> keys = categoryKeys(plugin, category);

      for (int index = 0; index < keys.size() && index < CONTENT_SLOTS.length; index++) {
         if (CONTENT_SLOTS[index] == slot) {
            return keys.get(index);
         }
      }

      return null;
   }

   public static void closeAll(W2NSMP plugin) {
      for (Player player : Bukkit.getOnlinePlayers()) {
         InventoryView view = player.getOpenInventory();
         if (view != null && view.getTopInventory().getHolder() instanceof SettingsMenuHolder) {
            player.closeInventory();
         }
      }
   }

   private static Material material(W2NSMP plugin, String key, Material fallback) {
      return GuiKit.material(plugin, key, fallback);
   }
}
