package me.w2n.w2nsmp.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.config.GuiConfig;
import me.w2n.w2nsmp.skill.SkillInfo;
import me.w2n.w2nsmp.skill.SkillMenuSlots;
import me.w2n.w2nsmp.skill.SkillService;
import me.w2n.w2nsmp.skill.SkillSettings;
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
 * GUI {@code /skill}: satu ikon per skill, berisi level, bar kemajuan XP, dan buff aktif.
 *
 * <p>Mengikuti pola menu W2NSMP yang sudah ada (holder sendiri, {@code gui/skill.yml} untuk
 * tata letak, {@code messages.yml} untuk kalimat bawaan). Mengklik ikon menampilkan detail
 * skill di chat dan menyegarkan angka di menu - tidak ada submenu, jadi tidak ada risiko
 * inventory bertumpuk.
 */
public final class SkillMenu {
   /**
    * Menu yang sedang terbuka (kunci: UUID pemain). Ini jalur cadangan penjagaan GUI: pembatalan
    * klik tidak lagi bergantung pada satu pemanggilan API pun, jadi item menu tidak bisa diambil
    * walaupun {@code getHolder()} bermasalah di sebuah versi server.
    */
   private static final Map<UUID, Inventory> OPEN = new HashMap<>();

   private SkillMenu() {
   }

   /** Apakah inventory ini salah satu menu skill yang sedang terbuka (dibandingkan identitasnya). */
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

   /** Apakah entity ini sedang punya menu skill terbuka (dipakai bila holder tidak terbaca). */
   public static boolean isOwnerMenu(HumanEntity entity) {
      return entity != null && OPEN.containsKey(entity.getUniqueId());
   }

   /** Lupakan menu entity ini. {@code inventory} null berarti hapus tanpa membandingkan. */
   public static void markClosed(HumanEntity entity, Inventory inventory) {
      if (entity == null) {
         return;
      }

      Inventory open = OPEN.get(entity.getUniqueId());
      if (open == null || inventory == null || open == inventory) {
         OPEN.remove(entity.getUniqueId());
      }
   }

   public static GuiConfig gui(W2NSMP plugin) {
      return plugin.guiConfigs().skill();
   }

   public static int size(W2NSMP plugin) {
      return gui(plugin).size(SkillMenuSlots.SIZE);
   }

   public static int slot(W2NSMP plugin, String path, int fallback) {
      int slot = gui(plugin).slot("slots." + path, fallback);
      return slot < size(plugin) ? slot : fallback;
   }

   public static int infoSlot(W2NSMP plugin) {
      return slot(plugin, "info", SkillMenuSlots.INFO);
   }

   public static int closeSlot(W2NSMP plugin) {
      return slot(plugin, "close", SkillMenuSlots.CLOSE);
   }

   /** Slot ikon skill: gui/skill.yml menang, lalu skills.list.&lt;key&gt;.slot di config.yml. */
   public static int skillSlot(W2NSMP plugin, SkillType type, int fallback) {
      return slot(plugin, type.key(), fallback);
   }

   public static void open(W2NSMP plugin, Player player) {
      if (player == null) {
         return;
      }

      SkillMenuHolder holder = new SkillMenuHolder(player.getUniqueId());
      Inventory inventory = Bukkit.createInventory(holder, size(plugin), gui(plugin).title("skill.gui-title", "player", player.getName()));
      holder.setInventory(inventory);
      render(plugin, inventory, player);
      player.openInventory(inventory);
      OPEN.put(player.getUniqueId(), inventory);
      plugin.guiSounds().play(player, gui(plugin), "open");
   }

   public static void render(W2NSMP plugin, Inventory inventory, Player player) {
      if (inventory == null || player == null) {
         return;
      }

      SkillService service = plugin.skills();
      int size = size(plugin);
      Set<Integer> content = contentSlots(plugin, size);
      GuiKit.shell(
         plugin,
         gui(plugin),
         inventory,
         Material.GRAY_STAINED_GLASS_PANE,
         "skill.filler-material",
         Material.BLACK_STAINED_GLASS_PANE,
         content::contains
      );
      if (service == null) {
         return;
      }

      for (SkillType type : SkillType.values()) {
         SkillSettings settings = service.settings(type);
         if (settings == null || !settings.enabled()) {
            continue;
         }

         int slot = skillSlot(plugin, type, settings.slot());
         if (slot >= 0 && slot < size) {
            inventory.setItem(slot, skillItem(plugin, player, type, settings));
         }
      }

      int info = infoSlot(plugin);
      if (info >= 0 && info < size) {
         inventory.setItem(info, infoItem(plugin, player));
      }

      int close = closeSlot(plugin);
      if (close >= 0 && close < size) {
         inventory.setItem(close, GuiKit.nav(plugin, gui(plugin), GuiKit.NAV_CLOSE, Material.BARRIER, "skill.close-name", "skill.close-lore"));
      }
   }

   /** Semua slot yang dipakai isi (ikon skill, info, close) - sisanya diisi filler/bingkai. */
   private static Set<Integer> contentSlots(W2NSMP plugin, int size) {
      Set<Integer> slots = new LinkedHashSet<>();
      SkillService service = plugin.skills();
      if (service != null) {
         for (SkillType type : SkillType.values()) {
            SkillSettings settings = service.settings(type);
            if (settings != null && settings.enabled()) {
               int slot = skillSlot(plugin, type, settings.slot());
               if (slot >= 0 && slot < size) {
                  slots.add(Integer.valueOf(slot));
               }
            }
         }
      }

      int info = infoSlot(plugin);
      if (info >= 0 && info < size) {
         slots.add(Integer.valueOf(info));
      }

      int close = closeSlot(plugin);
      if (close >= 0 && close < size) {
         slots.add(Integer.valueOf(close));
      }

      return slots;
   }

   private static ItemStack skillItem(W2NSMP plugin, Player player, SkillType type, SkillSettings settings) {
      String[] placeholders = SkillInfo.placeholders(plugin, player, type);
      GuiConfig gui = gui(plugin);
      Material material = gui.material("skills." + type.key() + ".material", settings.icon());
      String name = gui.name("skills." + type.key() + ".name");
      List<String> lore = gui.lore("skills." + type.key() + ".lore");
      if (name == null) {
         name = plugin.messages().raw("skill.item-name", placeholders);
      } else {
         name = plugin.messages().apply(name, placeholders);
      }

      if (lore.isEmpty()) {
         lore = plugin.messages().rawList("skill.item-lore", placeholders);
      } else {
         lore = plugin.messages().applyList(lore, placeholders);
      }

      return Items.create(material == null ? Material.BEDROCK : material, name, lore);
   }

   private static ItemStack infoItem(W2NSMP plugin, Player player) {
      SkillService service = plugin.skills();
      SkillType best = service == null ? null : service.bestSkill(player);
      String[] placeholders = new String[]{
         "player",
         player.getName(),
         "total-level",
         Integer.toString(service == null ? 0 : service.totalLevel(player)),
         "total-xp",
         SkillService.format(service == null ? 0.0D : service.totalXp(player)),
         "max",
         Integer.toString(service == null ? 0 : service.maxLevel()),
         "skills",
         Integer.toString(service == null ? 0 : service.enabledSkillCount()),
         "best",
         best == null ? "-" : service.label(best),
         "best-level",
         Integer.toString(best == null ? 1 : service.level(player, best)),
         "file",
         service == null ? "-" : service.fileName()
      };
      Material material = gui(plugin).material("info.material", GuiKit.material(plugin, "skill.info-material", Material.BOOK));
      String name = gui(plugin).name("info.name");
      List<String> lore = gui(plugin).lore("info.lore");
      if (name == null) {
         name = plugin.messages().raw("skill.info-name", placeholders);
      } else {
         name = plugin.messages().apply(name, placeholders);
      }

      if (lore.isEmpty()) {
         lore = plugin.messages().rawList("skill.info-lore", placeholders);
      } else {
         lore = plugin.messages().applyList(lore, placeholders);
      }

      return Items.create(material == null ? Material.BOOK : material, name, lore);
   }

   /** Isi slot: {@code skill:<key>}, {@code info}, {@code close}, atau null bila bukan bagian menu. */
   public static String keyAt(W2NSMP plugin, int rawSlot) {
      if (rawSlot == infoSlot(plugin)) {
         return "info";
      }

      if (rawSlot == closeSlot(plugin)) {
         return "close";
      }

      SkillType type = typeAt(plugin, rawSlot);
      return type == null ? null : "skill:" + type.key();
   }

   public static SkillType typeAt(W2NSMP plugin, int rawSlot) {
      SkillService service = plugin.skills();
      if (service == null) {
         return null;
      }

      for (SkillType type : SkillType.values()) {
         SkillSettings settings = service.settings(type);
         if (settings != null && settings.enabled() && skillSlot(plugin, type, settings.slot()) == rawSlot) {
            return type;
         }
      }

      return null;
   }

   /** Daftar skill yang benar-benar tampil (untuk pesan kosong). */
   public static List<SkillType> visible(W2NSMP plugin) {
      List<SkillType> result = new ArrayList<>();
      SkillService service = plugin.skills();
      if (service != null) {
         for (SkillType type : SkillType.values()) {
            SkillSettings settings = service.settings(type);
            if (settings != null && settings.enabled()) {
               result.add(type);
            }
         }
      }

      return result;
   }

   public static void closeAll(W2NSMP plugin) {
      for (Player player : Bukkit.getOnlinePlayers()) {
         try {
            InventoryView view = player.getOpenInventory();
            if (view != null && (view.getTopInventory().getHolder() instanceof SkillMenuHolder || isMenu(view.getTopInventory()))) {
               player.closeInventory();
            }
         } catch (Throwable throwable) {
            plugin.debug("Skill: gagal menutup menu untuk " + player.getName() + " (" + throwable + ").");
         }
      }

      OPEN.clear();
   }
}
