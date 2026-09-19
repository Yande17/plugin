package me.w2n.w2nsmp.gear;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.skill.SkillType;
import me.w2n.w2nsmp.utility.ItemTags;
import me.w2n.w2nsmp.utility.Items;
import me.w2n.w2nsmp.utility.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Riwayat & progresi gear (v1.4.0).
 *
 * <p>Sumber kebenaran riwayat adalah PDC di item ({@link GearKeys}); kelas gear dan milestone
 * perk seluruhnya dari config ({@code gear.*}) - tidak ada progresi yang di-hardcode. Layanan
 * ini hanya membaca/menulis data; penerapan perk saat combat ada di
 * {@link me.w2n.w2nsmp.listener.GearListener}.
 */
public final class GearService {
   private final W2NSMP plugin;
   private final GearKeys keys;
   private final Map<String, GearClass> classes = new LinkedHashMap<>();
   /** Cooldown perk per "uuid|kelas|kind" -> epoch millis kedaluwarsa (dibersihkan malas). */
   private final Map<String, Long> perkCooldowns = new HashMap<>();

   private boolean enabled = true;
   private boolean writeLore = true;
   private int loreUpdateEvery = 5;
   private boolean firstOwnerOnly = false;
   private double unmetDamagePercent = 100.0D;
   private double healCap = 20.0D;
   private boolean notifyMilestone = true;
   /** v1.5.4: true = syarat skill MENOLAK pemakaian/pemasangan (bukan hanya mengunci perk). */
   private boolean enforceRequirements = true;
   /** v1.5.4: anti-spam pesan penolakan per pemain (ms epoch pesan terakhir). */
   private final Map<UUID, Long> denyMessageAt = new HashMap<>();

   public GearService(W2NSMP plugin) {
      this.plugin = plugin;
      this.keys = new GearKeys(plugin);
   }

   public void reload() {
      FileConfiguration config = this.plugin.config().raw();
      this.enabled = config.getBoolean("gear.enabled", true);
      this.writeLore = config.getBoolean("gear.write-lore", true);
      this.loreUpdateEvery = Math.max(1, config.getInt("gear.lore-update-every", 5));
      this.firstOwnerOnly = config.getBoolean("gear.first-owner-only-perks", false);
      this.unmetDamagePercent = clamp(config.getDouble("gear.unmet-damage-percent", 100.0D), 0.0D, 100.0D);
      this.healCap = Math.max(1.0D, config.getDouble("gear.heal-cap", 20.0D));
      this.notifyMilestone = config.getBoolean("gear.notify-milestone", true);
      // v1.5.4 (PHASE 4): syarat skill kini benar-benar menggerbang pemakaian.
      this.enforceRequirements = config.getBoolean("gear.enforce-requirements", true);
      this.classes.clear();
      this.perkCooldowns.clear();
      this.denyMessageAt.clear();

      ConfigurationSection root = config.getConfigurationSection("gear.classes");
      if (root == null) {
         return;
      }

      Set<String> names = root.getKeys(false);
      if (names == null) {
         return;
      }

      for (String name : names) {
         ConfigurationSection section = root.getConfigurationSection(name);
         if (section == null) {
            continue;
         }

         Set<Material> materials = new LinkedHashSet<>();
         List<String> raw = section.getStringList("materials");
         if (raw != null) {
            for (String token : raw) {
               Material material = parseMaterial(token);
               if (material != null) {
                  materials.add(material);
               } else {
                  this.plugin.debug("Gear: material tidak dikenal di gear.classes." + name + " -> " + token);
               }
            }
         }

         if (materials.isEmpty()) {
            continue;
         }

         List<GearPerk> perks = new ArrayList<>();
         ConfigurationSection milestones = section.getConfigurationSection("milestones");
         if (milestones != null) {
            Set<String> thresholds = milestones.getKeys(false);
            if (thresholds != null) {
               for (String key : thresholds) {
                  int threshold = parseInt(key);
                  ConfigurationSection perk = milestones.getConfigurationSection(key);
                  if (threshold <= 0 || perk == null) {
                     continue;
                  }

                  perks.add(new GearPerk(
                     perk.getString("perk", ""),
                     threshold,
                     perk.getDouble("value", 0.0D),
                     perk.getInt("ticks", 3),
                     perk.getInt("interval-seconds", 2),
                     perk.getInt("cooldown-seconds", 8)));
               }
            }
         }

         this.classes.put(name.toLowerCase(Locale.ROOT), new GearClass(
            name,
            materials,
            section.getString("counter", "kills"),
            section.getString("slot", "weapon"),
            section.getString("require-skill", ""),
            section.getInt("require-level", 0),
            perks));
      }
   }

   public boolean enabled() {
      return this.enabled;
   }

   public GearKeys keys() {
      return this.keys;
   }

   public Map<String, GearClass> classes() {
      return this.classes;
   }

   /** Kelas gear untuk material tertentu (null = bukan gear yang dilacak). */
   public GearClass classFor(Material material) {
      if (material == null) {
         return null;
      }

      for (GearClass gearClass : this.classes.values()) {
         if (gearClass.matches(material)) {
            return gearClass;
         }
      }

      return null;
   }

   // ------------------------------------------------------------------ //
   // Riwayat item (baca)
   // ------------------------------------------------------------------ //

   public UUID firstOwner(ItemStack stack) {
      ItemMeta meta = meta(stack);
      String raw = meta == null ? null : ItemTags.getString(meta, this.keys.firstOwner);
      if (raw == null || raw.isEmpty()) {
         return null;
      }

      try {
         return UUID.fromString(raw);
      } catch (IllegalArgumentException exception) {
         return null;
      }
   }

   public String firstOwnerName(ItemStack stack) {
      ItemMeta meta = meta(stack);
      String name = meta == null ? null : ItemTags.getString(meta, this.keys.firstOwnerName);
      return name == null ? "?" : name;
   }

   public int kills(ItemStack stack) {
      ItemMeta meta = meta(stack);
      return meta == null ? 0 : ItemTags.getInt(meta, this.keys.kills, 0);
   }

   public int uses(ItemStack stack) {
      ItemMeta meta = meta(stack);
      return meta == null ? 0 : ItemTags.getInt(meta, this.keys.uses, 0);
   }

   /** Nilai counter yang dipakai kelas ini untuk milestone. */
   public int counter(ItemStack stack, GearClass gearClass) {
      return "uses".equals(gearClass.counter()) ? this.uses(stack) : this.kills(stack);
   }

   // ------------------------------------------------------------------ //
   // Riwayat item (tulis)
   // ------------------------------------------------------------------ //

   /** Cap pemilik pertama saat item pertama kali dipakai. Aman dipanggil berulang. */
   public boolean stampFirstOwner(Player player, ItemStack stack) {
      if (!this.enabled || player == null || Items.isEmpty(stack)) {
         return false;
      }

      ItemMeta meta = stack.getItemMeta();
      if (meta == null || ItemTags.has(meta, this.keys.firstOwner)) {
         return false;
      }

      ItemTags.setString(meta, this.keys.firstOwner, player.getUniqueId().toString());
      ItemTags.setString(meta, this.keys.firstOwnerName, player.getName());
      stack.setItemMeta(meta);
      this.refreshLore(stack, this.classFor(stack.getType()));
      return true;
   }

   /**
    * Tambah counter kill/pemakaian. Mengembalikan milestone yang BARU terlewati (null = tidak
    * ada). Lore di-refresh saat milestone terlewati atau tiap {@code lore-update-every} hitungan
    * supaya tidak menulis meta di setiap pukulan.
    */
   public GearPerk increment(ItemStack stack, GearClass gearClass, boolean kill) {
      if (!this.enabled || Items.isEmpty(stack) || gearClass == null) {
         return null;
      }

      ItemMeta meta = stack.getItemMeta();
      if (meta == null) {
         return null;
      }

      boolean usesCounter = "uses".equals(gearClass.counter());
      GearPerk crossed = null;
      int updated;

      if (kill) {
         int old = ItemTags.getInt(meta, this.keys.kills, 0);
         updated = old + 1;
         ItemTags.setInt(meta, this.keys.kills, updated);
         if (!usesCounter) {
            crossed = this.crossed(gearClass, old, updated);
         }
      } else {
         int old = ItemTags.getInt(meta, this.keys.uses, 0);
         updated = old + 1;
         ItemTags.setInt(meta, this.keys.uses, updated);
         if (usesCounter) {
            crossed = this.crossed(gearClass, old, updated);
         }
      }

      stack.setItemMeta(meta);

      if (crossed != null || updated % this.loreUpdateEvery == 0) {
         this.refreshLore(stack, gearClass);
      }

      return crossed;
   }

   private GearPerk crossed(GearClass gearClass, int oldCount, int newCount) {
      for (GearPerk perk : gearClass.perks()) {
         if (perk.threshold() > oldCount && perk.threshold() <= newCount) {
            return perk;
         }
      }

      return null;
   }

   // ------------------------------------------------------------------ //
   // Syarat & status perk
   // ------------------------------------------------------------------ //

   /** Syarat skill item ini: PDC menang atas config kelas (memungkinkan item spesial). */
   public SkillType requiredSkill(ItemStack stack, GearClass gearClass) {
      ItemMeta meta = meta(stack);
      String raw = meta == null ? null : ItemTags.getString(meta, this.keys.requireSkill);
      if (raw == null || raw.isEmpty()) {
         raw = gearClass == null ? "" : gearClass.requireSkill();
      }

      return SkillType.fromKey(raw);
   }

   public int requiredLevel(ItemStack stack, GearClass gearClass) {
      ItemMeta meta = meta(stack);
      int level = meta == null ? 0 : ItemTags.getInt(meta, this.keys.requireLevel, 0);
      if (level <= 0) {
         level = gearClass == null ? 0 : gearClass.requireLevel();
      }

      return Math.max(0, level);
   }

   /** Pemakaian dasar selalu boleh; hanya perk lanjutan yang terkunci bila syarat tidak terpenuhi. */
   /**
    * v1.5.4 (PHASE 4): apakah PEMAIN INI boleh memakai item ini. Selalu membandingkan stat
    * pemain yang sedang memegang/memasang dengan syarat item (PDC menang, lalu config class)
    * - TIDAK PERNAH stat pemilik pertama. Pemilik pertama murni riwayat.
    *
    * @return null bila boleh; bila ditolak, mengembalikan alasan siap-tampil
    *         ("Fighting Lv. 15 (you have 3)").
    */
   public String denyReason(Player player, ItemStack stack) {
      if (!this.enabled || !this.enforceRequirements || player == null || Items.isEmpty(stack)) {
         return null;
      }

      GearClass gearClass = this.classFor(stack.getType());
      if (gearClass == null) {
         return null;
      }

      SkillType skill = this.requiredSkill(stack, gearClass);
      int level = this.requiredLevel(stack, gearClass);
      if (skill == null || level <= 0) {
         return null;
      }

      int have = this.plugin.skills() == null ? 0 : this.plugin.skills().level(player, skill);
      if (have >= level) {
         return null;
      }

      String label = this.plugin.skills() == null ? skill.key() : this.plugin.skills().label(skill);
      return this.plugin.messages().raw("gear.deny-requirement",
         "skill", label, "level", Integer.toString(level), "have", Integer.toString(have));
   }

   /** true bila penolakan pemakaian aktif (gear.enforce-requirements). */
   public boolean enforceRequirements() {
      return this.enforceRequirements;
   }

   /** Kirim pesan penolakan (dengan cooldown anti-spam per pemain). */
   public void sendDeny(Player player, String reason) {
      if (player == null || reason == null) {
         return;
      }

      long now = System.currentTimeMillis();
      Long last = this.denyMessageAt.get(player.getUniqueId());
      if (last != null && now - last < 1500L) {
         return;
      }

      this.denyMessageAt.put(player.getUniqueId(), now);
      this.plugin.messages().send(player, "gear.denied", "reason", reason);
   }

   public boolean meetsRequirement(Player player, ItemStack stack, GearClass gearClass) {
      if (player == null) {
         return false;
      }

      if (this.firstOwnerOnly) {
         UUID owner = this.firstOwner(stack);
         if (owner != null && !owner.equals(player.getUniqueId())) {
            return false;
         }
      }

      SkillType skill = this.requiredSkill(stack, gearClass);
      int level = this.requiredLevel(stack, gearClass);
      if (skill == null || level <= 0) {
         return true;
      }

      return this.plugin.skills() != null && this.plugin.skills().level(player, skill) >= level;
   }

   /**
    * Persen damage saat syarat TIDAK terpenuhi (100 = pemakaian dasar penuh, bawaan).
    * Admin bisa menurunkannya lewat {@code gear.unmet-damage-percent} bila ingin item
    * bersyarat benar-benar lemah di tangan yang salah.
    */
   public double unmetDamagePercent() {
      return this.unmetDamagePercent;
   }

   public double healCap() {
      return this.healCap;
   }

   public boolean notifyMilestone() {
      return this.notifyMilestone;
   }

   /** Cek + pasang cooldown perk (true = boleh memicu sekarang). */
   public boolean tryCooldown(UUID player, GearClass gearClass, GearPerk perk, long now) {
      if (perk.cooldownSeconds() <= 0) {
         return true;
      }

      String key = player + "|" + gearClass.name() + "|" + perk.kind();
      Long until = this.perkCooldowns.get(key);
      if (until != null && until > now) {
         return false;
      }

      if (this.perkCooldowns.size() > 5000) {
         Iterator<Map.Entry<String, Long>> iterator = this.perkCooldowns.entrySet().iterator();
         while (iterator.hasNext()) {
            if (iterator.next().getValue() <= now) {
               iterator.remove();
            }
         }
      }

      this.perkCooldowns.put(key, now + perk.cooldownSeconds() * 1000L);
      return true;
   }

   // ------------------------------------------------------------------ //
   // Lore dinamis
   // ------------------------------------------------------------------ //

   /**
    * Tulis ulang blok lore riwayat di ekor lore item. Jumlah baris yang ditulis disimpan di PDC
    * ({@code gear-lore-lines}) sehingga pemanggilan berikutnya bisa menimpa blok lama tanpa
    * menyentuh lore asli item (mis. lore enchant atau lore custom lain).
    */
   public void refreshLore(ItemStack stack, GearClass gearClass) {
      if (!this.writeLore || Items.isEmpty(stack)) {
         return;
      }

      ItemMeta meta = stack.getItemMeta();
      if (meta == null) {
         return;
      }

      try {
         int oldLines = ItemTags.getInt(meta, this.keys.loreLines, 0);
         List<Component> lore = meta.lore();
         List<Component> base = lore == null ? new ArrayList<>() : new ArrayList<>(lore);
         for (int i = 0; i < oldLines && !base.isEmpty(); i++) {
            base.remove(base.size() - 1);
         }

         List<String> lines = this.loreLines(stack, meta, gearClass);
         for (String line : lines) {
            base.add(Text.itemLore(line));
         }

         meta.lore(base);
         ItemTags.setInt(meta, this.keys.loreLines, lines.size());
         stack.setItemMeta(meta);
      } catch (Throwable throwable) {
         this.plugin.debug("Gear: gagal menulis lore riwayat (" + throwable + ").");
      }
   }

   private List<String> loreLines(ItemStack stack, ItemMeta meta, GearClass gearClass) {
      List<String> lines = new ArrayList<>();
      String ownerName = ItemTags.getString(meta, this.keys.firstOwnerName);
      int kills = ItemTags.getInt(meta, this.keys.kills, 0);
      int uses = ItemTags.getInt(meta, this.keys.uses, 0);

      lines.add(this.plugin.messages().raw("gear.lore-header"));
      if (ownerName != null && !ownerName.isEmpty()) {
         lines.add(this.plugin.messages().raw("gear.lore-owner", "owner", ownerName));
      }

      lines.add(this.plugin.messages().raw("gear.lore-kills", "kills", Integer.toString(kills)));
      lines.add(this.plugin.messages().raw("gear.lore-uses", "uses", Integer.toString(uses)));

      if (gearClass != null) {
         int count = "uses".equals(gearClass.counter()) ? uses : kills;
         for (GearPerk perk : gearClass.unlocked(count)) {
            lines.add(this.plugin.messages().raw("gear.lore-perk",
               "perk", this.perkLabel(perk), "threshold", Integer.toString(perk.threshold())));
         }

         GearPerk next = gearClass.next(count);
         if (next != null) {
            lines.add(this.plugin.messages().raw("gear.lore-next",
               "perk", this.perkLabel(next), "threshold", Integer.toString(next.threshold()),
               "count", Integer.toString(count)));
         }

         SkillType skill = this.requiredSkill(stack, gearClass);
         int level = this.requiredLevel(stack, gearClass);
         if (skill != null && level > 0) {
            lines.add(this.plugin.messages().raw("gear.lore-require",
               "skill", this.plugin.skills() == null ? skill.key() : this.plugin.skills().label(skill),
               "level", Integer.toString(level)));
         }
      }

      return lines;
   }

   /** Nama perk yang bisa dibaca (dari messages, cadangan nama teknis). */
   public String perkLabel(GearPerk perk) {
      String key = "gear.perk." + perk.kind();
      if (this.plugin.messages().has(key)) {
         return this.plugin.messages().raw(key,
            "value", trim(perk.value()), "ticks", Integer.toString(perk.ticks()));
      }

      return perk.kind();
   }

   // ------------------------------------------------------------------ //

   private static ItemMeta meta(ItemStack stack) {
      return Items.isEmpty(stack) || !stack.hasItemMeta() ? null : stack.getItemMeta();
   }

   private static Material parseMaterial(String token) {
      if (token == null || token.isEmpty()) {
         return null;
      }

      try {
         return Material.valueOf(token.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException exception) {
         return null;
      }
   }

   private static int parseInt(String raw) {
      try {
         return Integer.parseInt(raw.trim());
      } catch (Exception exception) {
         return 0;
      }
   }

   private static double clamp(double value, double min, double max) {
      return Math.max(min, Math.min(max, value));
   }

   private static String trim(double value) {
      return value == Math.floor(value) && !Double.isInfinite(value)
         ? Long.toString((long)value)
         : String.format(Locale.ROOT, "%.1f", value);
   }
}
