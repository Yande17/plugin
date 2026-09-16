package me.w2n.w2nsmp.gui;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.config.GuiConfig;
import me.w2n.w2nsmp.skill.SkillBuff;
import me.w2n.w2nsmp.skill.SkillCurve;
import me.w2n.w2nsmp.skill.SkillInfo;
import me.w2n.w2nsmp.skill.SkillPath;
import me.w2n.w2nsmp.skill.SkillService;
import me.w2n.w2nsmp.skill.SkillSettings;
import me.w2n.w2nsmp.skill.SkillType;
import me.w2n.w2nsmp.utility.GuiKit;
import me.w2n.w2nsmp.utility.Items;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/**
 * Menu <b>jalur progres (snake path)</b> satu skill (v1.4.0).
 *
 * <p>Setiap level menjadi satu node yang disusun berkelok seperti ular (lihat {@link SkillPath}).
 * Node punya empat status dengan material berbeda: sudah dilewati, level sekarang, terkunci,
 * dan milestone (level tempat buff terbuka). Mengklik node milestone menampilkan detail buff
 * di chat. Level di atas 28 dipecah ke beberapa halaman dengan tombol prev/next.
 *
 * <p>Pola penjagaannya identik dengan {@link SkillProgressMenu}: holder sendiri, pendaftaran
 * menu terbuka sebagai jalur cadangan, klik dibatalkan di prioritas paling awal oleh
 * {@code SkillGuiListener}.
 */
public final class SkillPathMenu {
   /** Menu jalur yang sedang terbuka (kunci: UUID pemain) - jalur cadangan penjagaan klik. */
   private static final Map<UUID, Inventory> OPEN = new HashMap<>();

   private SkillPathMenu() {
   }

   // ------------------------------------------------------------------ //
   //  Penjagaan menu (pola yang sama dengan SkillMenu/SkillProgressMenu)
   // ------------------------------------------------------------------ //

   public static boolean isMenu(Inventory inventory) {
      if (inventory == null) {
         return false;
      }

      for (Inventory open : OPEN.values()) {
         if (open == inventory) {
            return true;
         }
      }

      return false;
   }

   public static boolean isOwnerMenu(HumanEntity entity) {
      return entity != null && OPEN.containsKey(entity.getUniqueId());
   }

   public static void markClosed(HumanEntity entity, Inventory inventory) {
      if (entity == null) {
         return;
      }

      Inventory open = OPEN.get(entity.getUniqueId());
      if (open == null || inventory == null || open == inventory) {
         OPEN.remove(entity.getUniqueId());
      }
   }

   public static void closeAll(W2NSMP plugin) {
      for (Player player : Bukkit.getOnlinePlayers()) {
         try {
            InventoryView view = player.getOpenInventory();
            if (view != null
               && (view.getTopInventory().getHolder() instanceof SkillPathMenuHolder || isMenu(view.getTopInventory()))) {
               player.closeInventory();
            }
         } catch (Throwable throwable) {
            plugin.debug("Skill: gagal menutup menu jalur untuk " + player.getName() + " (" + throwable + ").");
         }
      }

      OPEN.clear();
   }

   // ------------------------------------------------------------------ //
   //  Tata letak
   // ------------------------------------------------------------------ //

   public static GuiConfig gui(W2NSMP plugin) {
      return SkillMenu.gui(plugin);
   }

   public static int size() {
      return 54;
   }

   public static int backSlot(W2NSMP plugin) {
      return clamp(gui(plugin).slot("path.slots.back", 45));
   }

   public static int prevSlot(W2NSMP plugin) {
      return clamp(gui(plugin).slot("path.slots.prev", 48));
   }

   public static int infoSlot(W2NSMP plugin) {
      return clamp(gui(plugin).slot("path.slots.info", 49));
   }

   public static int nextSlot(W2NSMP plugin) {
      return clamp(gui(plugin).slot("path.slots.next", 50));
   }

   public static int closeSlot(W2NSMP plugin) {
      return clamp(gui(plugin).slot("path.slots.close", 53));
   }

   private static int clamp(int slot) {
      return slot >= 0 && slot < 54 ? slot : -1;
   }

   // ------------------------------------------------------------------ //
   //  Buka & gambar
   // ------------------------------------------------------------------ //

   public static void open(W2NSMP plugin, Player player, SkillType type, int requestedPage) {
      if (player == null || type == null) {
         return;
      }

      SkillService service = plugin.skills();
      if (service == null || !service.enabled()) {
         SkillInfo.sendDetail(plugin, player, player, type);
         return;
      }

      int maxLevel = service.maxLevel();
      int page = requestedPage < 0
         ? SkillPath.pageOf(service.level(player, type), maxLevel)
         : SkillPath.clampPage(requestedPage, maxLevel);
      SkillPathMenuHolder holder = new SkillPathMenuHolder(player.getUniqueId(), type, page);
      Inventory inventory = Bukkit.createInventory(holder, size(), title(plugin, player, type, page, maxLevel));
      holder.setInventory(inventory);
      render(plugin, inventory, player, holder);
      player.openInventory(inventory);
      OPEN.put(player.getUniqueId(), inventory);
      plugin.guiSounds().play(player, gui(plugin), "open");
   }

   private static Component title(W2NSMP plugin, Player player, SkillType type, int page, int maxLevel) {
      SkillService service = plugin.skills();
      String[] placeholders = new String[]{
         "player", player.getName(),
         "skill", service == null ? type.key() : service.label(type),
         "page", Integer.toString(page + 1),
         "pages", Integer.toString(SkillPath.pages(maxLevel))
      };
      String override = gui(plugin).name("path.title");
      return override == null
         ? plugin.messages().component("skill.path-title", placeholders)
         : plugin.messages().colored(plugin.messages().apply(override, placeholders));
   }

   public static void render(W2NSMP plugin, Inventory inventory, Player player, SkillPathMenuHolder holder) {
      if (inventory == null || player == null || holder == null || holder.type() == null) {
         return;
      }

      SkillService service = plugin.skills();
      if (service == null) {
         return;
      }

      SkillType type = holder.type();
      SkillSettings settings = service.settings(type);
      SkillCurve curve = service.curve();
      int maxLevel = curve.maxLevel();
      int page = SkillPath.clampPage(holder.page(), maxLevel);
      holder.page(page);
      int playerLevel = service.level(player, type);
      double xp = service.xp(player, type);

      Set<Integer> content = new LinkedHashSet<>();

      for (int index = 0; index < SkillPath.perPage(); index++) {
         if (SkillPath.levelAt(page, index, maxLevel) > 0) {
            content.add(Integer.valueOf(SkillPath.slotOf(index)));
         }
      }

      int back = backSlot(plugin);
      int prev = prevSlot(plugin);
      int info = infoSlot(plugin);
      int next = nextSlot(plugin);
      int close = closeSlot(plugin);
      int iconSlot = 4;
      content.add(Integer.valueOf(iconSlot));
      addIf(content, back);
      addIf(content, info);
      addIf(content, close);
      boolean hasPrev = page > 0;
      boolean hasNext = page + 1 < SkillPath.pages(maxLevel);
      if (hasPrev) {
         addIf(content, prev);
      }

      if (hasNext) {
         addIf(content, next);
      }

      GuiKit.shell(
         plugin,
         gui(plugin),
         inventory,
         Material.GRAY_STAINED_GLASS_PANE,
         "skill.filler-material",
         Material.BLACK_STAINED_GLASS_PANE,
         content::contains
      );

      // Ikon skill di atas (level & XP ringkas).
      String[] base = SkillInfo.placeholders(plugin, player, type);
      Material iconMaterial = gui(plugin).material("skills." + type.key() + ".material", settings.icon());
      inventory.setItem(iconSlot, Items.create(
         iconMaterial == null ? Material.BEDROCK : iconMaterial,
         plugin.messages().raw("skill.path-icon-name", base),
         plugin.messages().rawList("skill.path-icon-lore", base)
      ));

      // Node level (jalur ular).
      for (int index = 0; index < SkillPath.perPage(); index++) {
         int level = SkillPath.levelAt(page, index, maxLevel);
         if (level <= 0) {
            continue;
         }

         int slot = SkillPath.slotOf(index);
         if (slot >= 0 && slot < 54) {
            inventory.setItem(slot, nodeItem(plugin, service, settings, curve, type, level, playerLevel, xp));
         }
      }

      // Navigasi.
      String[] pagePlaceholders = new String[]{
         "page", Integer.toString(page + 1),
         "pages", Integer.toString(SkillPath.pages(maxLevel))
      };
      if (back >= 0) {
         inventory.setItem(back, GuiKit.nav(plugin, gui(plugin), GuiKit.NAV_BACK, Material.ARROW,
            "skill.path-back-name", "skill.path-back-lore"));
      }

      if (hasPrev && prev >= 0) {
         inventory.setItem(prev, GuiKit.nav(plugin, gui(plugin), GuiKit.NAV_PREV, Material.ARROW,
            "skill.path-prev-name", "skill.path-prev-lore", pagePlaceholders));
      }

      if (info >= 0) {
         inventory.setItem(info, Items.create(
            gui(plugin).material("path.info-material", Material.BOOK),
            plugin.messages().raw("skill.path-info-name", with(base, pagePlaceholders)),
            plugin.messages().rawList("skill.path-info-lore", with(base, pagePlaceholders))
         ));
      }

      if (hasNext && next >= 0) {
         inventory.setItem(next, GuiKit.nav(plugin, gui(plugin), GuiKit.NAV_NEXT, Material.ARROW,
            "skill.path-next-name", "skill.path-next-lore", pagePlaceholders));
      }

      if (close >= 0) {
         inventory.setItem(close, GuiKit.nav(plugin, gui(plugin), GuiKit.NAV_CLOSE, Material.BARRIER,
            "skill.close-name", "skill.close-lore"));
      }
   }

   private static void addIf(Set<Integer> content, int slot) {
      if (slot >= 0 && slot < 54) {
         content.add(Integer.valueOf(slot));
      }
   }

   /** Item satu node level: material menurut status, lore berisi XP & buff yang terbuka di sana. */
   private static ItemStack nodeItem(W2NSMP plugin, SkillService service, SkillSettings settings, SkillCurve curve,
      SkillType type, int level, int playerLevel, double xp) {
      List<SkillBuff> unlocks = settings.buffsUnlockedAt(level);
      boolean milestone = !unlocks.isEmpty();
      SkillPath.Node status = SkillPath.status(level, playerLevel, milestone);
      GuiConfig gui = gui(plugin);
      Material material = switch (status) {
         case CURRENT -> gui.material("path.node-current", Material.YELLOW_STAINED_GLASS_PANE);
         case UNLOCKED -> gui.material("path.node-unlocked", Material.LIME_STAINED_GLASS_PANE);
         case LOCKED -> gui.material("path.node-locked", Material.GRAY_STAINED_GLASS_PANE);
         case MILESTONE_UNLOCKED -> gui.material("path.node-milestone-unlocked", Material.EXPERIENCE_BOTTLE);
         case MILESTONE_LOCKED -> gui.material("path.node-milestone-locked", Material.PURPLE_STAINED_GLASS_PANE);
      };
      String nameKey = switch (status) {
         case CURRENT -> "skill.path-node-current";
         case UNLOCKED, MILESTONE_UNLOCKED -> "skill.path-node-unlocked";
         case LOCKED, MILESTONE_LOCKED -> "skill.path-node-locked";
      };
      String[] placeholders = new String[]{
         "level", Integer.toString(level),
         "max", Integer.toString(curve.maxLevel()),
         "skill", service.label(type),
         "xp-total", SkillService.format(curve.xpToReach(level)),
         "xp-have", SkillService.format(xp)
      };
      String name = plugin.messages().raw(nameKey, placeholders);
      List<String> lore = new java.util.ArrayList<>(plugin.messages().rawList("skill.path-node-lore", placeholders));

      for (SkillBuff buff : unlocks) {
         lore.add(plugin.messages().raw("skill.path-node-buff",
            "buff", service.buffName(buff),
            "desc", service.buffDescription(buff)));
      }

      if (milestone && level > playerLevel) {
         lore.add(plugin.messages().raw("skill.path-node-milestone-hint", placeholders));
      }

      return Items.create(material == null ? Material.PAPER : material, name, lore);
   }

   private static String[] with(String[] base, String... extra) {
      String[] result = new String[base.length + extra.length];
      System.arraycopy(base, 0, result, 0, base.length);
      System.arraycopy(extra, 0, result, base.length, extra.length);
      return result;
   }

   // ------------------------------------------------------------------ //
   //  Pembacaan slot untuk klik
   // ------------------------------------------------------------------ //

   /**
    * Isi slot: {@code back}, {@code prev}, {@code next}, {@code close}, {@code node:<level>},
    * atau null bila slot bukan bagian menu.
    */
   public static String keyAt(W2NSMP plugin, SkillPathMenuHolder holder, int rawSlot) {
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

      SkillService service = plugin.skills();
      int maxLevel = service == null ? 50 : service.maxLevel();
      int level = SkillPath.levelAtSlot(holder == null ? 0 : holder.page(), rawSlot, maxLevel);
      return level > 0 ? "node:" + level : null;
   }

   /** Level dari kunci {@code node:<level>}; -1 bila bukan kunci node. */
   public static int nodeLevel(String key) {
      if (key == null || !key.startsWith("node:")) {
         return -1;
      }

      try {
         int level = Integer.parseInt(key.substring(5));
         return level > 0 ? level : -1;
      } catch (RuntimeException exception) {
         return -1;
      }
   }
}
