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
import me.w2n.w2nsmp.skill.SkillBuff;
import me.w2n.w2nsmp.skill.SkillCurve;
import me.w2n.w2nsmp.skill.SkillInfo;
import me.w2n.w2nsmp.skill.SkillService;
import me.w2n.w2nsmp.skill.SkillSettings;
import me.w2n.w2nsmp.skill.SkillTop;
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
 * Menu <b>progres skill</b>: dibuka saat sebuah ikon skill di GUI {@code /skill} diklik.
 *
 * <p>Isinya menjawab dua pertanyaan pemain sekaligus - "sudah sejauh apa saya?" dan "apa yang
 * saya dapat berikutnya?":
 * <ul>
 *   <li>ikon skill: level, bar kemajuan, XP menuju level berikutnya, cara menaikkan skill;</li>
 *   <li>bar kaca 9 petak: gambaran visual kemajuan level;</li>
 *   <li>kartu buff (satu per buff): nilai sekarang, atau <b>level berapa buff itu terbuka</b>;</li>
 *   <li>empat kartu milestone: level berikutnya, buff berikutnya, level maksimum, dan peringkat
 *       pemain (skill ini &amp; total) di papan peringkat {@code /skill top};</li>
 *   <li>navigasi: kembali ke {@code /skill}, kirim detail ke chat, dan close.</li>
 * </ul>
 *
 * <p>Tata letak &amp; material dibaca dari {@code gui/skill.yml} bagian {@code progress:}, kalimat
 * dari {@code messages.yml} bagian {@code skill.progress-*} - sama seperti menu W2NSMP lainnya.
 * Semua slot dijaga tetap di dalam ukuran inventory, jadi config yang salah tidak pernah membuat
 * menu gagal dibuka.
 */
public final class SkillProgressMenu {
   /** Menu progres yang sedang terbuka (kunci: UUID pemain) - jalur cadangan penjagaan klik. */
   private static final Map<UUID, Inventory> OPEN = new HashMap<>();

   private SkillProgressMenu() {
   }

   // ------------------------------------------------------------------ //
   //  Penjagaan menu (pola yang sama dengan SkillMenu)
   // ------------------------------------------------------------------ //

   /** Apakah inventory ini salah satu menu progres yang sedang terbuka (dibandingkan identitasnya). */
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

   /** Apakah entity ini sedang punya menu progres terbuka (dipakai bila holder tidak terbaca). */
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

   /** Skill yang sedang dilihat entity ini (null bila tidak sedang membuka menu progres). */
   public static SkillType openType(HumanEntity entity) {
      if (entity == null) {
         return null;
      }

      Inventory inventory = OPEN.get(entity.getUniqueId());
      return inventory == null || !(inventory.getHolder() instanceof SkillProgressMenuHolder holder)
         ? null
         : holder.type();
   }

   public static void closeAll(W2NSMP plugin) {
      for (Player player : Bukkit.getOnlinePlayers()) {
         try {
            InventoryView view = player.getOpenInventory();
            if (view != null
               && (view.getTopInventory().getHolder() instanceof SkillProgressMenuHolder || isMenu(view.getTopInventory()))) {
               player.closeInventory();
            }
         } catch (Throwable throwable) {
            plugin.debug("Skill: gagal menutup menu progres untuk " + player.getName() + " (" + throwable + ").");
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

   public static int size(W2NSMP plugin) {
      return gui(plugin).size("progress.size", SkillMenu.size(plugin));
   }

   private static int slot(W2NSMP plugin, String path, int fallback) {
      int size = size(plugin);
      int value = gui(plugin).slot("progress." + path, fallback);
      return value >= 0 && value < size ? value : (fallback < size ? fallback : -1);
   }

   public static int iconSlot(W2NSMP plugin) {
      return slot(plugin, "slots.icon", 4);
   }

   public static int barStart(W2NSMP plugin) {
      return slot(plugin, "bar-start", 18);
   }

   public static int barLength(W2NSMP plugin) {
      int length = gui(plugin).slot("progress.bar-length", 9);
      return Math.max(1, Math.min(27, length <= 0 ? 9 : length));
   }

   public static int buffStart(W2NSMP plugin) {
      return slot(plugin, "slots.buffs", 10);
   }

   public static int backSlot(W2NSMP plugin) {
      return slot(plugin, "slots.back", 45);
   }

   /** Tombol jalur progres (snake path, v1.4.0). */
   public static int pathSlot(W2NSMP plugin) {
      return slot(plugin, "slots.path", 47);
   }

   public static int detailSlot(W2NSMP plugin) {
      return slot(plugin, "slots.detail", 49);
   }

   public static int closeSlot(W2NSMP plugin) {
      return slot(plugin, "slots.close", 53);
   }

   /** Slot kartu buff (maks {@code maxBuffs}); buff sedikit dipasang di tengah, banyak berjejer. */
   public static List<Integer> buffSlots(W2NSMP plugin, int count) {
      List<Integer> slots = new ArrayList<>();
      int size = size(plugin);
      int start = buffStart(plugin);
      int max = Math.max(1, Math.min(7, count));
      if (start < 0 || start >= size) {
         return slots;
      }

      if (max <= 3) {
         int center = start + 3;

         for (int index = 0; index < max; index++) {
            int value = center - (max - 1 - index) * 2;
            if (value >= 0 && value < size) {
               slots.add(Integer.valueOf(value));
            }
         }

         return slots;
      }

      for (int index = 0; index < max && start + index < size; index++) {
         slots.add(Integer.valueOf(start + index));
      }

      return slots;
   }

   // ------------------------------------------------------------------ //
   //  Buka & gambar
   // ------------------------------------------------------------------ //

   public static void open(W2NSMP plugin, Player player, SkillType type) {
      if (player == null || type == null) {
         return;
      }

      SkillService service = plugin.skills();
      if (service == null || !service.enabled()) {
         SkillInfo.sendDetail(plugin, player, player, type);
         return;
      }

      int size = size(plugin);
      SkillProgressMenuHolder holder = new SkillProgressMenuHolder(player.getUniqueId(), type);
      Inventory inventory = Bukkit.createInventory(holder, size, title(plugin, player, type));
      holder.setInventory(inventory);
      render(plugin, inventory, player, type);
      player.openInventory(inventory);
      OPEN.put(player.getUniqueId(), inventory);
      plugin.guiSounds().play(player, gui(plugin), "open");
   }

   /** Segarkan menu yang sedang terbuka (dipanggil setelah angka berubah). */
   public static void refresh(W2NSMP plugin, Player player) {
      if (player == null) {
         return;
      }

      Inventory inventory = OPEN.get(player.getUniqueId());
      SkillType type = openType(player);
      if (inventory == null || type == null) {
         return;
      }

      render(plugin, inventory, player, type);
   }

   private static Component title(W2NSMP plugin, Player player, SkillType type) {
      String[] placeholders = SkillInfo.placeholders(plugin, player, type);
      String override = gui(plugin).name("progress.title");
      return override == null
         ? plugin.messages().component("skill.progress-title", placeholders)
         : plugin.messages().colored(plugin.messages().apply(override, placeholders));
   }

   public static void render(W2NSMP plugin, Inventory inventory, Player player, SkillType type) {
      if (inventory == null || player == null || type == null) {
         return;
      }

      SkillService service = plugin.skills();
      int size = inventory.getSize();
      SkillSettings settings = service == null ? null : service.settings(type);
      List<SkillBuff> buffs = settings == null ? List.of() : settings.buffs();
      List<Integer> buffSlots = buffSlots(plugin, buffs.size());
      Set<Integer> content = contentSlots(plugin, size, buffs.size());
      GuiKit.shell(
         plugin,
         gui(plugin),
         inventory,
         Material.GRAY_STAINED_GLASS_PANE,
         "skill.filler-material",
         Material.BLACK_STAINED_GLASS_PANE,
         content::contains
      );
      if (service == null || settings == null) {
         return;
      }

      String[] base = SkillInfo.placeholders(plugin, player, type);
      int icon = iconSlot(plugin);
      if (icon >= 0 && icon < size) {
         inventory.setItem(icon, iconItem(plugin, player, type, settings, base));
      }

      renderBar(plugin, inventory, player, type, size);

      for (int index = 0; index < buffs.size() && index < buffSlots.size(); index++) {
         int slot = buffSlots.get(index).intValue();
         if (slot >= 0 && slot < size) {
            inventory.setItem(slot, buffItem(plugin, player, type, settings, buffs.get(index), base));
         }
      }

      renderMilestones(plugin, inventory, player, type, size, base);

      int back = backSlot(plugin);
      if (back >= 0 && back < size) {
         inventory.setItem(back, GuiKit.nav(plugin, gui(plugin), GuiKit.NAV_BACK, Material.ARROW,
            "skill.progress-back-name", "skill.progress-back-lore"));
      }

      int path = pathSlot(plugin);
      if (path >= 0 && path < size) {
         inventory.setItem(path, textItem(plugin, gui(plugin).material("progress.path-material", Material.FILLED_MAP),
            "skill.progress-path-name", "skill.progress-path-lore", base));
      }

      int detail = detailSlot(plugin);
      if (detail >= 0 && detail < size) {
         inventory.setItem(detail, textItem(plugin, gui(plugin).material("progress.detail-material", Material.BOOK),
            "skill.progress-detail-name", "skill.progress-detail-lore", base));
      }

      int close = closeSlot(plugin);
      if (close >= 0 && close < size) {
         inventory.setItem(close, GuiKit.nav(plugin, gui(plugin), GuiKit.NAV_CLOSE, Material.BARRIER,
            "skill.close-name", "skill.close-lore"));
      }
   }

   /** Bar kaca: petak terisi mengikuti persen kemajuan level (penuh saat level maksimum). */
   private static void renderBar(W2NSMP plugin, Inventory inventory, Player player, SkillType type, int size) {
      SkillService service = plugin.skills();
      int start = barStart(plugin);
      int length = barLength(plugin);
      if (start < 0 || start >= size) {
         return;
      }

      SkillCurve curve = service.curve();
      double xp = service.xp(player, type);
      double progress = Math.max(0.0D, Math.min(1.0D, curve.progress(xp)));
      int filled = (int) Math.round(progress * (double) length);
      Material filledMaterial = gui(plugin).material("progress.bar-filled", Material.LIME_DYE);
      Material emptyMaterial = gui(plugin).material("progress.bar-empty", Material.GRAY_STAINED_GLASS_PANE);
      String name = gui(plugin).name("progress.bar-name");
      if (name == null) {
         name = plugin.messages().has("skill.progress-pane-name") ? plugin.messages().raw("skill.progress-pane-name") : " ";
      }

      for (int index = 0; index < length && start + index < size; index++) {
         ItemStack pane = Items.create(index < filled ? filledMaterial : emptyMaterial, name, List.of());
         inventory.setItem(start + index, pane);
      }
   }

   /** Empat kartu milestone: level berikutnya, buff berikutnya, level maksimum, dan peringkat. */
   private static void renderMilestones(W2NSMP plugin, Inventory inventory, Player player, SkillType type, int size,
      String[] base) {
      SkillService service = plugin.skills();
      SkillSettings settings = service.settings(type);
      int level = service.level(player, type);
      SkillCurve curve = service.curve();

      int nextLevel = slot(plugin, "slots.next-level", 28);
      if (nextLevel >= 0 && nextLevel < size) {
         String key = curve.isMaxLevel(service.xp(player, type)) ? "skill.progress-maxed-name" : "skill.progress-next-level-name";
         String loreKey = curve.isMaxLevel(service.xp(player, type)) ? "skill.progress-maxed-lore" : "skill.progress-next-level-lore";
         inventory.setItem(nextLevel, textItem(plugin, gui(plugin).material("progress.next-level-material", Material.CLOCK),
            key, loreKey, base));
      }

      int nextBuff = slot(plugin, "slots.next-buff", 30);
      if (nextBuff >= 0 && nextBuff < size) {
         SkillBuff upcoming = settings.nextBuff(level);
         if (upcoming == null) {
            inventory.setItem(nextBuff, textItem(plugin, gui(plugin).material("progress.all-buffs-material", Material.NETHER_STAR),
               "skill.progress-all-buffs-name", "skill.progress-all-buffs-lore", base));
         } else {
            String[] placeholders = with(base, buffPlaceholders(plugin, service, player, type, upcoming, level));
            inventory.setItem(nextBuff, textItem(plugin, gui(plugin).material("progress.next-buff-material", Material.ENDER_EYE),
               "skill.progress-next-buff-name", "skill.progress-next-buff-lore", placeholders));
         }
      }

      int maxLevel = slot(plugin, "slots.max-level", 32);
      if (maxLevel >= 0 && maxLevel < size) {
         String[] placeholders = with(base, "xp-max-level", SkillService.format(curve.totalXpAtMaxLevel()));
         inventory.setItem(maxLevel, textItem(plugin, gui(plugin).material("progress.max-level-material", Material.DIAMOND),
            "skill.progress-max-level-name", "skill.progress-max-level-lore", placeholders));
      }

      int rank = slot(plugin, "slots.rank", 34);
      if (rank >= 0 && rank < size) {
         SkillTop top = service.top();
         int skillRank = top.rankOfSkill(player.getUniqueId(), type);
         int totalRank = top.rankOfTotal(player.getUniqueId());
         String[] placeholders = with(base,
            "rank", skillRank <= 0 ? "-" : Integer.toString(skillRank),
            "rank-total", totalRank <= 0 ? "-" : Integer.toString(totalRank));
         inventory.setItem(rank, textItem(plugin, gui(plugin).material("progress.rank-material", Material.GOLD_INGOT),
            "skill.progress-rank-name", "skill.progress-rank-lore", placeholders));
      }
   }

   private static Set<Integer> contentSlots(W2NSMP plugin, int size, int buffCount) {
      Set<Integer> slots = new LinkedHashSet<>();
      add(slots, size, iconSlot(plugin));
      add(slots, size, backSlot(plugin));
      add(slots, size, pathSlot(plugin));
      add(slots, size, detailSlot(plugin));
      add(slots, size, closeSlot(plugin));
      add(slots, size, slot(plugin, "slots.next-level", 28));
      add(slots, size, slot(plugin, "slots.next-buff", 30));
      add(slots, size, slot(plugin, "slots.max-level", 32));
      add(slots, size, slot(plugin, "slots.rank", 34));

      int start = barStart(plugin);
      int length = barLength(plugin);

      for (int index = 0; index < length && start + index < size; index++) {
         add(slots, size, start + index);
      }

      for (Integer value : buffSlots(plugin, buffCount)) {
         add(slots, size, value.intValue());
      }

      return slots;
   }

   private static void add(Set<Integer> slots, int size, int value) {
      if (value >= 0 && value < size) {
         slots.add(Integer.valueOf(value));
      }
   }

   private static ItemStack iconItem(W2NSMP plugin, Player player, SkillType type, SkillSettings settings, String[] base) {
      GuiConfig gui = gui(plugin);
      Material material = gui.material("skills." + type.key() + ".material", settings.icon());
      String name = gui.name("progress.icon-name");
      List<String> lore = gui.lore("progress.icon-lore");
      if (name == null) {
         name = plugin.messages().raw("skill.progress-item-name", base);
      } else {
         name = plugin.messages().apply(name, base);
      }

      if (lore.isEmpty()) {
         lore = plugin.messages().rawList("skill.progress-item-lore", base);
      } else {
         lore = plugin.messages().applyList(lore, base);
      }

      return Items.create(material == null ? Material.BEDROCK : material, name, lore);
   }

   /** Kartu satu buff: nilainya bila sudah terbuka, atau level pembukaannya bila belum. */
   private static ItemStack buffItem(W2NSMP plugin, Player player, SkillType type, SkillSettings settings,
      SkillBuff buff, String[] base) {
      SkillService service = plugin.skills();
      int level = service.level(player, type);
      boolean active = settings.enabled() && buff.unlocked(level);
      String[] placeholders = with(base, buffPlaceholders(plugin, service, player, type, buff, level));
      Material material = gui(plugin).material(active ? "progress.buff-active-material" : "progress.buff-locked-material",
         active ? Material.NETHER_STAR : Material.GRAY_DYE);
      String name = plugin.messages().raw(active ? "skill.progress-buff-active-name" : "skill.progress-buff-locked-name",
         placeholders);
      List<String> lore = plugin.messages().rawList(active ? "skill.progress-buff-active-lore" : "skill.progress-buff-locked-lore",
         placeholders);
      return Items.create(material, name, lore);
   }

   private static String[] buffPlaceholders(W2NSMP plugin, SkillService service, Player player, SkillType type,
      SkillBuff buff, int level) {
      int capLevel = buff.capLevel();
      int nextAmplifier = buff.nextAmplifierLevel(level);
      return new String[]{
         "buff",
         service.buffName(buff),
         "buff-value",
         service.buffDisplay(player, type, buff),
         "buff-desc",
         service.buffDescription(buff),
         "buff-kind",
         buff.kind() == null ? "-" : buff.kind().key(),
         "unlock",
         Integer.toString(buff.unlockLevel()),
         "per",
         SkillService.format(buff.perLevel()),
         "max",
         SkillService.format(buff.max()),
         "power",
         SkillService.format(buff.power()),
         "value",
         SkillService.format(buff.value(level)),
         "cap-level",
         capLevel < 0 ? "-" : Integer.toString(Math.min(capLevel, service.maxLevel())),
         "amp-next",
         nextAmplifier < 0 ? "-" : Integer.toString(nextAmplifier)
      };
   }

   /** Item teks sederhana: nama & lore dari messages.yml, material dari gui/skill.yml. */
   private static ItemStack textItem(W2NSMP plugin, Material material, String nameKey, String loreKey, String[] placeholders) {
      String name = plugin.messages().has(nameKey) ? plugin.messages().raw(nameKey, placeholders) : nameKey;
      List<String> lore = plugin.messages().has(loreKey) ? plugin.messages().rawList(loreKey, placeholders) : List.of();
      return Items.create(material == null ? Material.PAPER : material, name, lore);
   }

   /** Gabungkan placeholder dasar dengan pasangan tambahan. */
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
    * Isi slot: {@code back}, {@code detail}, {@code close}, {@code buff:<indeks>}, atau null bila
    * slot itu bukan bagian menu (filler/bar/milestone hanya hiasan). Jumlah buff ikut diberikan
    * karena posisi kartu buff menyesuaikan banyaknya buff skill itu.
    */
   public static String keyAt(W2NSMP plugin, SkillType type, int rawSlot) {
      if (rawSlot == backSlot(plugin)) {
         return "back";
      }

      if (rawSlot == pathSlot(plugin)) {
         return "path";
      }

      if (rawSlot == detailSlot(plugin)) {
         return "detail";
      }

      if (rawSlot == closeSlot(plugin)) {
         return "close";
      }

      SkillService service = plugin.skills();
      SkillSettings settings = service == null || type == null ? null : service.settings(type);
      int count = settings == null ? 0 : settings.buffs().size();
      List<Integer> buffSlots = buffSlots(plugin, count);

      for (int index = 0; index < buffSlots.size(); index++) {
         if (buffSlots.get(index).intValue() == rawSlot) {
            return "buff:" + index;
         }
      }

      return null;
   }

   /** Indeks buff dari kunci {@code buff:<indeks>}; -1 bila bukan kunci buff. */
   public static int buffIndex(String key) {
      if (key == null || !key.startsWith("buff:")) {
         return -1;
      }

      try {
         return Integer.parseInt(key.substring(5));
      } catch (RuntimeException exception) {
         return -1;
      }
   }
}
