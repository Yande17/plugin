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
import me.w2n.w2nsmp.fishing.CustomFish;
import me.w2n.w2nsmp.fishing.FishingService;
import me.w2n.w2nsmp.skill.SkillService;
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
 * Fishing Hub /fish (v1.4.1): halaman utama (Fish Gallery + My Rod) dan galeri ikan.
 *
 * <p>Galeri menampilkan SEMUA ikan custom terurut rarity (Common -> Mythic). Ikan yang belum
 * pernah ditangkap tampil sebagai "???" tanpa membocorkan nama/nilai. Menu murni tampilan:
 * tidak ada item pemain yang berpindah ke GUI, jadi tidak ada jalur dupe.
 */
public final class FishMenu {
   /** Slot konten galeri (28 slot, pola sama dengan menu lain). */
   private static final int[] CONTENT_SLOTS = {
      10, 11, 12, 13, 14, 15, 16,
      19, 20, 21, 22, 23, 24, 25,
      28, 29, 30, 31, 32, 33, 34,
      37, 38, 39, 40, 41, 42, 43
   };
   /** Pemain yang sedang membuka menu (pola OPEN sama dengan GUI lain). */
   private static final Map<UUID, FishMenuHolder> OPEN = new ConcurrentHashMap<>();

   private FishMenu() {
   }

   public static GuiConfig gui(W2NSMP plugin) {
      return plugin.guiConfigs().of("fish");
   }

   public static int perPage() {
      return CONTENT_SLOTS.length;
   }

   /** Jumlah halaman galeri untuk {@code count} ikan (minimal 1). */
   public static int pages(int count) {
      return Math.max(1, (Math.max(0, count) + CONTENT_SLOTS.length - 1) / CONTENT_SLOTS.length);
   }

   public static int clampPage(int page, int count) {
      return Math.max(0, Math.min(page, pages(count) - 1));
   }

   /** Indeks ikan pada (halaman, posisi-slot); -1 bila di luar daftar. */
   public static int indexAt(int page, int slotIndex, int count) {
      if (page < 0 || slotIndex < 0 || slotIndex >= CONTENT_SLOTS.length) {
         return -1;
      }

      int index = page * CONTENT_SLOTS.length + slotIndex;
      return index >= 0 && index < count ? index : -1;
   }

   /** Posisi di CONTENT_SLOTS untuk slot mentah; -1 bila bukan slot konten. */
   public static int slotIndexOf(int rawSlot) {
      for (int index = 0; index < CONTENT_SLOTS.length; index++) {
         if (CONTENT_SLOTS[index] == rawSlot) {
            return index;
         }
      }

      return -1;
   }

   // ------------------------------------------------------------------ //
   // Slot dari config
   // ------------------------------------------------------------------ //

   private static int slot(W2NSMP plugin, String path, int fallback) {
      int slot = gui(plugin).slot("slots." + path, fallback);
      return slot >= 0 && slot < 54 ? slot : fallback;
   }

   public static int gallerySlot(W2NSMP plugin) {
      return slot(plugin, "gallery", 11);
   }

   public static int rodSlot(W2NSMP plugin) {
      return slot(plugin, "rod", 15);
   }

   public static int hubInfoSlot(W2NSMP plugin) {
      return slot(plugin, "hub-info", 22);
   }

   public static int hubCloseSlot(W2NSMP plugin) {
      return slot(plugin, "hub-close", 26);
   }

   public static int backSlot(W2NSMP plugin) {
      return slot(plugin, "back", 45);
   }

   public static int prevSlot(W2NSMP plugin) {
      return slot(plugin, "prev", 48);
   }

   public static int infoSlot(W2NSMP plugin) {
      return slot(plugin, "info", 49);
   }

   public static int nextSlot(W2NSMP plugin) {
      return slot(plugin, "next", 50);
   }

   public static int closeSlot(W2NSMP plugin) {
      return slot(plugin, "close", 53);
   }

   // ------------------------------------------------------------------ //
   // Buka & gambar
   // ------------------------------------------------------------------ //

   public static void open(W2NSMP plugin, Player player) {
      open(plugin, player, FishMenuHolder.MODE_HUB, 0);
   }

   public static void open(W2NSMP plugin, Player player, String mode, int page) {
      if (player == null) {
         return;
      }

      FishMenuHolder holder = new FishMenuHolder(player.getUniqueId());
      holder.mode(mode);
      holder.page(page);
      boolean hub = FishMenuHolder.MODE_HUB.equals(holder.mode());
      int size = hub ? Math.max(27, gui(plugin).size("hub-size", 27)) : 54;
      String titleKey = hub ? "fishing.hub-title" : "fishing.gallery-title";
      Inventory inventory = Bukkit.createInventory(holder, size,
         gui(plugin).title(titleKey, "player", player.getName()));
      holder.setInventory(inventory);
      render(plugin, inventory, player, holder);
      player.openInventory(inventory);
      OPEN.put(player.getUniqueId(), holder);
      plugin.guiSounds().play(player, gui(plugin), "open");
   }

   public static void render(W2NSMP plugin, Inventory inventory, Player player, FishMenuHolder holder) {
      if (inventory == null || player == null || holder == null) {
         return;
      }

      if (FishMenuHolder.MODE_GALLERY.equals(holder.mode())) {
         renderGallery(plugin, inventory, player, holder);
      } else {
         renderHub(plugin, inventory, player);
      }
   }

   private static void renderHub(W2NSMP plugin, Inventory inventory, Player player) {
      FishingService fishing = plugin.fishing();
      int gallery = gallerySlot(plugin);
      int rod = rodSlot(plugin);
      int info = hubInfoSlot(plugin);
      int close = hubCloseSlot(plugin);
      Set<Integer> content = new HashSet<>(List.of(gallery, rod, info, close));

      clearExcept(inventory, content);
      GuiKit.shell(plugin, gui(plugin), inventory, Material.LIGHT_BLUE_STAINED_GLASS_PANE,
         "fishing.filler-material", Material.BLUE_STAINED_GLASS_PANE, content::contains);

      int total = fishing == null ? 0 : fishing.fishRegistry().size();
      int found = fishing == null ? 0
         : fishing.discovery().countIn(player.getUniqueId(), fishing.fishRegistry().keySet());
      String[] placeholders = {
         "player", player.getName(),
         "found", Integer.toString(found),
         "total", Integer.toString(total)
      };

      inventory.setItem(gallery, Items.create(
         gui(plugin).material("gallery-material", Material.TROPICAL_FISH),
         plugin.messages().raw("fishing.hub-gallery-name", placeholders),
         plugin.messages().rawList("fishing.hub-gallery-lore", placeholders)));
      inventory.setItem(rod, Items.create(
         gui(plugin).material("rod-material", Material.FISHING_ROD),
         plugin.messages().raw("fishing.hub-rod-name", placeholders),
         plugin.messages().rawList("fishing.hub-rod-lore", placeholders)));
      inventory.setItem(info, Items.create(
         gui(plugin).material("info-material", Material.BOOK),
         plugin.messages().raw("fishing.hub-info-name", placeholders),
         plugin.messages().rawList("fishing.hub-info-lore", placeholders)));
      inventory.setItem(close, GuiKit.nav(plugin, gui(plugin), GuiKit.NAV_CLOSE, Material.BARRIER,
         "fishing.close-name", "fishing.close-lore"));
   }

   private static void renderGallery(W2NSMP plugin, Inventory inventory, Player player, FishMenuHolder holder) {
      FishingService fishing = plugin.fishing();
      List<CustomFish> all = sortedFish(fishing);
      int page = clampPage(holder.page(), all.size());
      holder.page(page);

      int back = backSlot(plugin);
      int prev = prevSlot(plugin);
      int info = infoSlot(plugin);
      int next = nextSlot(plugin);
      int close = closeSlot(plugin);
      boolean hasPrev = page > 0;
      boolean hasNext = page + 1 < pages(all.size());

      Set<Integer> content = new HashSet<>();

      for (int index = 0; index < CONTENT_SLOTS.length; index++) {
         if (indexAt(page, index, all.size()) >= 0) {
            content.add(CONTENT_SLOTS[index]);
         }
      }

      content.add(back);
      content.add(info);
      content.add(close);
      if (hasPrev) {
         content.add(prev);
      }

      if (hasNext) {
         content.add(next);
      }

      clearExcept(inventory, content);
      GuiKit.shell(plugin, gui(plugin), inventory, Material.LIGHT_BLUE_STAINED_GLASS_PANE,
         "fishing.filler-material", Material.BLUE_STAINED_GLASS_PANE, content::contains);

      for (int index = 0; index < CONTENT_SLOTS.length; index++) {
         int fishIndex = indexAt(page, index, all.size());
         if (fishIndex >= 0) {
            inventory.setItem(CONTENT_SLOTS[index], fishItem(plugin, fishing, player, all.get(fishIndex)));
         }
      }

      String[] pagePlaceholders = {
         "page", Integer.toString(page + 1),
         "pages", Integer.toString(pages(all.size())),
         "found", Integer.toString(fishing == null ? 0
            : fishing.discovery().countIn(player.getUniqueId(), fishing.fishRegistry().keySet())),
         "total", Integer.toString(all.size())
      };
      inventory.setItem(back, GuiKit.nav(plugin, gui(plugin), GuiKit.NAV_BACK, Material.ARROW,
         "fishing.gallery-back-name", "fishing.gallery-back-lore"));
      if (hasPrev) {
         inventory.setItem(prev, GuiKit.nav(plugin, gui(plugin), GuiKit.NAV_PREV, Material.ARROW,
            "fishing.gallery-prev-name", "fishing.gallery-prev-lore", pagePlaceholders));
      }

      inventory.setItem(info, Items.create(
         gui(plugin).material("info-material", Material.BOOK),
         plugin.messages().raw("fishing.gallery-info-name", pagePlaceholders),
         plugin.messages().rawList("fishing.gallery-info-lore", pagePlaceholders)));
      if (hasNext) {
         inventory.setItem(next, GuiKit.nav(plugin, gui(plugin), GuiKit.NAV_NEXT, Material.ARROW,
            "fishing.gallery-next-name", "fishing.gallery-next-lore", pagePlaceholders));
      }

      inventory.setItem(close, GuiKit.nav(plugin, gui(plugin), GuiKit.NAV_CLOSE, Material.BARRIER,
         "fishing.close-name", "fishing.close-lore"));
   }

   /** Semua ikan terurut rarity (Common -> Mythic) lalu id, supaya urutan galeri stabil. */
   public static List<CustomFish> sortedFish(FishingService fishing) {
      List<CustomFish> all = fishing == null ? List.of() : new ArrayList<>(fishing.fishRegistry().values());
      List<CustomFish> sorted = new ArrayList<>(all);
      sorted.sort((a, b) -> {
         int byRarity = Integer.compare(a.rarity().ordinal(), b.rarity().ordinal());
         return byRarity != 0 ? byRarity : a.id().compareTo(b.id());
      });
      return sorted;
   }

   /** Item satu ikan di galeri: kartu penuh bila sudah ditemukan, "???" bila belum. */
   private static ItemStack fishItem(W2NSMP plugin, FishingService fishing, Player player, CustomFish fish) {
      boolean discovered = fishing != null && fishing.discovery().isDiscovered(player.getUniqueId(), fish.id());
      String rarity = fishing == null ? fish.rarity().key() : fishing.rarityLabel(fish.rarity());

      if (!discovered) {
         // v1.5.3: kartu terkunci menampilkan HINT (dari config fish.<id>.hint, atau hint
         // otomatis dari syaratnya) tanpa membocorkan nama/nilai/data lengkap ikan.
         return Items.create(
            gui(plugin).material("undiscovered-material", Material.GRAY_DYE),
            plugin.messages().raw("fishing.gallery-unknown-name", "rarity", rarity),
            plugin.messages().rawList("fishing.gallery-unknown-lore",
               "rarity", rarity,
               "hint", hintFor(plugin, fish)));
      }

      List<String> lore = new ArrayList<>(plugin.messages().rawList("fishing.gallery-fish-lore",
         "rarity", rarity,
         "value", Long.toString(fish.baseValue()),
         "min-size", format(fish.minSize()),
         "max-size", format(fish.maxSize()),
         "xp", format(fish.xp())));
      appendRequirements(plugin, fish, lore);
      return Items.create(fish.material(),
         plugin.messages().raw("fishing.gallery-fish-name", "fish", fish.fishName(), "rarity", rarity),
         lore);
   }

   /**
    * Hint kartu terkunci (v1.5.3): pakai {@code hint} dari config bila diisi; kalau kosong,
    * susun otomatis dari syarat lingkungan ikan tanpa membocorkan nama/nilai.
    */
   public static String hintFor(W2NSMP plugin, CustomFish fish) {
      if (fish == null) {
         return plugin.messages().raw("fishing.hint-default");
      }

      if (!fish.hint().isEmpty()) {
         return fish.hint();
      }

      // Susun otomatis: "Try fishing <biome> <waktu> <cuaca>." - hanya bagian yang disyaratkan.
      StringBuilder parts = new StringBuilder();
      if (!fish.biomes().isEmpty()) {
         parts.append(' ').append(plugin.messages().raw("fishing.hint-biome",
            "biomes", String.join(", ", fish.biomes()).toLowerCase(java.util.Locale.ROOT)));
      }

      if (!"any".equals(fish.time())) {
         parts.append(' ').append(plugin.messages().raw("fishing.hint-time",
            "time", plugin.messages().raw("fishing.time." + fish.time())));
      }

      if (!"any".equals(fish.weather())) {
         parts.append(' ').append(plugin.messages().raw("fishing.hint-weather",
            "weather", plugin.messages().raw("fishing.weather." + fish.weather())));
      }

      if (parts.length() == 0) {
         return plugin.messages().raw("fishing.hint-default");
      }

      return plugin.messages().raw("fishing.hint-prefix") + parts;
   }

   /** Baris syarat (biome/cuaca/waktu/level) - hanya yang benar-benar disyaratkan. */
   private static void appendRequirements(W2NSMP plugin, CustomFish fish, List<String> lore) {
      if (!fish.biomes().isEmpty()) {
         lore.add(plugin.messages().raw("fishing.gallery-req-biome",
            "biomes", String.join(", ", fish.biomes())));
      }

      if (!"any".equals(fish.weather())) {
         lore.add(plugin.messages().raw("fishing.gallery-req-weather", "weather",
            plugin.messages().raw("fishing.weather." + fish.weather())));
      }

      if (!"any".equals(fish.time())) {
         lore.add(plugin.messages().raw("fishing.gallery-req-time", "time",
            plugin.messages().raw("fishing.time." + fish.time())));
      }

      if (fish.minFishingLevel() > 0) {
         lore.add(plugin.messages().raw("fishing.gallery-req-level",
            "level", Integer.toString(fish.minFishingLevel())));
      }

      if (fish.minRodLevel() > 1) {
         lore.add(plugin.messages().raw("fishing.gallery-req-rod",
            "level", Integer.toString(fish.minRodLevel())));
      }
   }

   private static String format(double value) {
      return SkillService.format(value);
   }

   /** Kosongkan semua slot di luar konten (anti ghost item saat pindah hub <-> galeri). */
   private static void clearExcept(Inventory inventory, Set<Integer> content) {
      for (int slot = 0; slot < inventory.getSize(); slot++) {
         if (!content.contains(slot)) {
            inventory.setItem(slot, null);
         }
      }
   }

   // ------------------------------------------------------------------ //
   // Aksi klik
   // ------------------------------------------------------------------ //

   /** Kunci aksi slot untuk mode holder sekarang; null bila slot bukan tombol. */
   public static String keyAt(W2NSMP plugin, FishMenuHolder holder, int rawSlot) {
      if (holder == null) {
         return null;
      }

      if (FishMenuHolder.MODE_HUB.equals(holder.mode())) {
         if (rawSlot == gallerySlot(plugin)) {
            return "gallery";
         }

         if (rawSlot == rodSlot(plugin)) {
            return "rod";
         }

         if (rawSlot == hubCloseSlot(plugin)) {
            return "close";
         }

         return null;
      }

      if (rawSlot == backSlot(plugin)) {
         return "back";
      }

      if (rawSlot == prevSlot(plugin)) {
         return "prev";
      }

      if (rawSlot == nextSlot(plugin)) {
         return "next";
      }

      if (rawSlot == closeSlot(plugin)) {
         return "close";
      }

      return null;
   }

   // ------------------------------------------------------------------ //
   // Pendaftaran menu terbuka
   // ------------------------------------------------------------------ //

   public static boolean isMenu(Inventory inventory) {
      if (inventory == null) {
         return false;
      }

      for (FishMenuHolder holder : OPEN.values()) {
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

      FishMenuHolder holder = OPEN.get(entity.getUniqueId());
      if (holder != null && (inventory == null || holder.getInventory() == inventory)) {
         OPEN.remove(entity.getUniqueId());
      }
   }

   public static void closeAll(W2NSMP plugin) {
      for (Player player : Bukkit.getOnlinePlayers()) {
         try {
            InventoryView view = player.getOpenInventory();
            if (view != null && (view.getTopInventory().getHolder() instanceof FishMenuHolder
               || isMenu(view.getTopInventory()))) {
               player.closeInventory();
            }
         } catch (Throwable throwable) {
            plugin.debug("Fishing: gagal menutup menu /fish untuk " + player.getName() + " (" + throwable + ").");
         }
      }

      OPEN.clear();
   }
}
