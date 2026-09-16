package me.w2n.w2nsmp.fishing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.utility.ItemTags;
import me.w2n.w2nsmp.utility.Items;
import me.w2n.w2nsmp.utility.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Registry custom fishing + sistem rod (v1.4.0).
 *
 * <p>Satu-satunya sumber kebenaran untuk ikan custom, item pancing custom, level rod, dan
 * attachment - listener ({@link me.w2n.w2nsmp.listener.FishingListener}) hanya memanggil
 * layanan ini, tidak ada logika yang tersebar. Identitas semua item lewat PDC
 * ({@link FishingKeys}); definisi seluruhnya dari config ({@code fishing.*}).
 */
public final class FishingService {
   private final W2NSMP plugin;
   private final FishingKeys keys;
   /** Catatan ikan yang pernah ditangkap per pemain (Fish Gallery /fish, v1.4.1). */
   private final FishDiscovery discovery;
   private final Random random = new Random();
   private final Map<String, CustomFish> fish = new LinkedHashMap<>();
   private final Map<String, FishingItem> items = new LinkedHashMap<>();
   /** Gerbang level skill Fishing per rarity (rarity -> level minimum). */
   private final Map<FishRarity, Integer> rarityGates = new LinkedHashMap<>();
   /** Efek per level rod: level -> (efek -> nilai). */
   private final Map<Integer, Map<String, Double>> rodEffects = new LinkedHashMap<>();
   /** Biaya upgrade ke level N: level -> (id item -> jumlah). */
   private final Map<Integer, Map<String, Integer>> rodCosts = new LinkedHashMap<>();
   /** XP rod minimum untuk upgrade ke level N. */
   private final Map<Integer, Double> rodXpNeeded = new LinkedHashMap<>();

   private boolean enabled = true;
   private double customChance = 25.0D;
   private int rodMaxLevel = 10;
   private int attachmentSlots = 3;
   private double rodXpPerCatch = 1.0D;
   private double rodXpPerCustom = 3.0D;

   public FishingService(W2NSMP plugin) {
      this.plugin = plugin;
      this.keys = new FishingKeys(plugin);
      this.discovery = new FishDiscovery(plugin);
   }

   /** Catatan penemuan ikan (galeri /fish). Tidak pernah {@code null}. */
   public FishDiscovery discovery() {
      return this.discovery;
   }

   // ------------------------------------------------------------------ //
   // Muat config
   // ------------------------------------------------------------------ //

   public void reload() {
      this.discovery.load();
      FileConfiguration config = this.plugin.config().raw();
      this.enabled = config.getBoolean("fishing.enabled", true);
      this.customChance = clamp(config.getDouble("fishing.custom-chance-percent", 25.0D), 0.0D, 100.0D);
      this.rodMaxLevel = Math.max(1, config.getInt("fishing.rod.max-level", 10));
      this.attachmentSlots = Math.max(0, Math.min(5, config.getInt("fishing.rod.attachment-slots", 3)));
      this.rodXpPerCatch = Math.max(0.0D, config.getDouble("fishing.rod.xp-per-catch", 1.0D));
      this.rodXpPerCustom = Math.max(0.0D, config.getDouble("fishing.rod.xp-per-custom-catch", 3.0D));

      this.rarityGates.clear();
      for (FishRarity rarity : FishRarity.values()) {
         this.rarityGates.put(rarity, Math.max(0,
            config.getInt("fishing.rarity-level." + rarity.key(), 0)));
      }

      this.loadFish(config);
      this.loadItems(config);
      this.loadRod(config);
   }

   private void loadFish(FileConfiguration config) {
      this.fish.clear();
      ConfigurationSection root = config.getConfigurationSection("fishing.fish");
      Set<String> ids = root == null ? null : root.getKeys(false);
      if (ids == null) {
         return;
      }

      for (String id : ids) {
         ConfigurationSection section = root.getConfigurationSection(id);
         if (section == null) {
            continue;
         }

         String key = id.toLowerCase(Locale.ROOT);
         this.fish.put(key, new CustomFish(
            key,
            section.getString("name", id),
            FishRarity.fromKey(section.getString("rarity", "common")),
            parseMaterial(section.getString("material", "COD"), Material.COD),
            section.getStringList("lore"),
            section.getDouble("weight", 10.0D),
            section.getDouble("min-size", 10.0D),
            section.getDouble("max-size", 50.0D),
            section.getLong("value", 0L),
            section.getDouble("xp", 5.0D),
            section.getStringList("biomes"),
            section.getString("weather", "any"),
            section.getString("time", "any"),
            section.getInt("min-fishing-level", 0),
            section.getInt("min-rod-level", 0)));
      }
   }

   private void loadItems(FileConfiguration config) {
      this.items.clear();
      ConfigurationSection root = config.getConfigurationSection("fishing.items");
      Set<String> ids = root == null ? null : root.getKeys(false);
      if (ids == null) {
         return;
      }

      for (String id : ids) {
         ConfigurationSection section = root.getConfigurationSection(id);
         if (section == null) {
            continue;
         }

         String key = id.toLowerCase(Locale.ROOT);
         this.items.put(key, new FishingItem(
            key,
            section.getString("name", id),
            parseMaterial(section.getString("material", "PRISMARINE_SHARD"), Material.PRISMARINE_SHARD),
            section.getStringList("lore"),
            section.getString("kind", "upgrade"),
            section.getString("effect", ""),
            section.getDouble("value", 0.0D),
            section.getDouble("drop-chance-percent", 0.0D)));
      }
   }

   private void loadRod(FileConfiguration config) {
      this.rodEffects.clear();
      this.rodCosts.clear();
      this.rodXpNeeded.clear();
      ConfigurationSection root = config.getConfigurationSection("fishing.rod.levels");
      Set<String> keys = root == null ? null : root.getKeys(false);
      if (keys == null) {
         return;
      }

      for (String raw : keys) {
         int level = parseInt(raw);
         ConfigurationSection section = root.getConfigurationSection(raw);
         if (level < 2 || level > this.rodMaxLevel || section == null) {
            continue;
         }

         this.rodXpNeeded.put(level, Math.max(0.0D, section.getDouble("xp", 0.0D)));

         Map<String, Integer> cost = new LinkedHashMap<>();
         ConfigurationSection costSection = section.getConfigurationSection("cost");
         if (costSection != null) {
            Set<String> costKeys = costSection.getKeys(false);
            if (costKeys != null) {
               for (String itemId : costKeys) {
                  int amount = costSection.getInt(itemId, 0);
                  if (amount > 0) {
                     cost.put(itemId.toLowerCase(Locale.ROOT), amount);
                  }
               }
            }
         }
         this.rodCosts.put(level, cost);

         Map<String, Double> effects = new LinkedHashMap<>();
         ConfigurationSection effectSection = section.getConfigurationSection("effects");
         if (effectSection != null) {
            Set<String> effectKeys = effectSection.getKeys(false);
            if (effectKeys != null) {
               for (String effect : effectKeys) {
                  effects.put(effect.toLowerCase(Locale.ROOT), effectSection.getDouble(effect, 0.0D));
               }
            }
         }
         this.rodEffects.put(level, effects);
      }
   }

   // ------------------------------------------------------------------ //
   // Akses registry
   // ------------------------------------------------------------------ //

   public boolean enabled() {
      return this.enabled;
   }

   public FishingKeys keys() {
      return this.keys;
   }

   public Map<String, CustomFish> fishRegistry() {
      return this.fish;
   }

   public Map<String, FishingItem> itemRegistry() {
      return this.items;
   }

   public FishingItem item(String id) {
      return id == null ? null : this.items.get(id.toLowerCase(Locale.ROOT));
   }

   public int rodMaxLevel() {
      return this.rodMaxLevel;
   }

   public int attachmentSlots() {
      return this.attachmentSlots;
   }

   public int rarityGate(FishRarity rarity) {
      Integer gate = this.rarityGates.get(rarity);
      return gate == null ? 0 : gate;
   }

   /** Nama rarity berwarna dari messages (cadangan: warna bawaan + nama teknis). */
   public String rarityLabel(FishRarity rarity) {
      if (this.plugin.messages().has(rarity.messageKey())) {
         return this.plugin.messages().raw(rarity.messageKey());
      }

      return rarity.color() + rarity.key();
   }

   // ------------------------------------------------------------------ //
   // Undian ikan custom
   // ------------------------------------------------------------------ //

   /**
    * Undi ikan custom untuk satu tangkapan (null = pakai hasil vanilla).
    *
    * @param bonusChancePercent tambahan peluang dari rod/attachment (persen absolut)
    * @param rarityBoost pengali bobot untuk rarity RARE+ (1.0 = tanpa bonus)
    */
   public CustomFish roll(String biomeName, boolean storming, long worldTime,
                          int fishingLevel, int rodLevel, double bonusChancePercent, double rarityBoost) {
      if (!this.enabled || this.fish.isEmpty()) {
         return null;
      }

      double chance = clamp(this.customChance + bonusChancePercent, 0.0D, 100.0D);
      if (this.random.nextDouble() * 100.0D >= chance) {
         return null;
      }

      List<CustomFish> pool = new ArrayList<>();
      List<Double> weights = new ArrayList<>();
      double total = 0.0D;

      for (CustomFish candidate : this.fish.values()) {
         if (candidate.weight() <= 0.0D
            || fishingLevel < Math.max(candidate.minFishingLevel(), this.rarityGate(candidate.rarity()))
            || rodLevel < candidate.minRodLevel()
            || !candidate.matchesEnvironment(biomeName, storming, worldTime)) {
            continue;
         }

         double weight = candidate.weight();
         if (rarityBoost > 1.0D && candidate.rarity().ordinal() >= FishRarity.RARE.ordinal()) {
            weight *= rarityBoost;
         }

         pool.add(candidate);
         weights.add(weight);
         total += weight;
      }

      if (pool.isEmpty() || total <= 0.0D) {
         return null;
      }

      double pick = this.random.nextDouble() * total;
      for (int i = 0; i < pool.size(); i++) {
         pick -= weights.get(i);
         if (pick <= 0.0D) {
            return pool.get(i);
         }
      }

      return pool.get(pool.size() - 1);
   }

   /** Buat ItemStack ikan custom: PDC id/rarity/ukuran/nilai + nama & lore berwarna. */
   public ItemStack createFish(CustomFish definition) {
      return this.createFish(definition, 0.0D);
   }

   /**
    * Buat ikan custom dengan bonus nilai jual (persen) dari rod/attachment - bonus dihitung
    * DI SINI supaya angka di lore selalu sama dengan nilai yang tersimpan di PDC.
    */
   public ItemStack createFish(CustomFish definition, double valueBonusPercent) {
      double size = definition.minSize()
         + this.random.nextDouble() * (definition.maxSize() - definition.minSize());
      size = Math.round(size * 10.0D) / 10.0D;
      // Ikan lebih besar sedikit lebih berharga (linier sederhana, plafon 2x nilai dasar).
      double sizeFactor = definition.maxSize() <= definition.minSize() ? 0.0D
         : (size - definition.minSize()) / (definition.maxSize() - definition.minSize());
      long value = Math.round(definition.baseValue() * (1.0D + sizeFactor)
         * (1.0D + Math.max(0.0D, valueBonusPercent) / 100.0D));

      ItemStack stack = new ItemStack(definition.material());
      ItemMeta meta = stack.getItemMeta();
      if (meta == null) {
         return stack;
      }

      meta.displayName(Text.itemName(definition.rarity().color() + definition.fishName()));

      List<Component> lore = new ArrayList<>();
      lore.add(Text.itemLore(this.plugin.messages().raw("fishing.fish-lore-rarity",
         "rarity", this.rarityLabel(definition.rarity()))));
      lore.add(Text.itemLore(this.plugin.messages().raw("fishing.fish-lore-size",
         "size", String.format(Locale.ROOT, "%.1f", size))));
      if (value > 0L) {
         lore.add(Text.itemLore(this.plugin.messages().raw("fishing.fish-lore-value",
            "value", Long.toString(value))));
      }
      for (String line : definition.lore()) {
         lore.add(Text.itemLore(line));
      }
      meta.lore(lore);

      ItemTags.setString(meta, this.keys.fishId, definition.id());
      ItemTags.setString(meta, this.keys.fishRarity, definition.rarity().key());
      ItemTags.setDouble(meta, this.keys.fishSize, size);
      ItemTags.setLong(meta, this.keys.fishValue, value);
      stack.setItemMeta(meta);
      return stack;
   }

   public boolean isCustomFish(ItemStack stack) {
      ItemMeta meta = meta(stack);
      return meta != null && ItemTags.has(meta, this.keys.fishId);
   }

   public CustomFish fishOf(ItemStack stack) {
      ItemMeta meta = meta(stack);
      String id = meta == null ? null : ItemTags.getString(meta, this.keys.fishId);
      return id == null ? null : this.fish.get(id);
   }

   /** Nilai jual ikan custom yang tersimpan di item (0 = bukan ikan custom / tanpa nilai). */
   public long fishValue(ItemStack stack) {
      ItemMeta meta = meta(stack);
      return meta == null ? 0L : ItemTags.getLong(meta, this.keys.fishValue, 0L);
   }

   // ------------------------------------------------------------------ //
   // Item pancing custom (bahan upgrade & attachment)
   // ------------------------------------------------------------------ //

   /** Buat item pancing custom ber-PDC (untuk /w2nsmp fishing give & loot). */
   public ItemStack createItem(FishingItem definition, int amount) {
      ItemStack stack = new ItemStack(definition.material());
      stack.setAmount(Math.max(1, amount));
      ItemMeta meta = stack.getItemMeta();
      if (meta == null) {
         return stack;
      }

      meta.displayName(Text.itemName(definition.itemName()));
      List<Component> lore = new ArrayList<>();
      for (String line : definition.lore()) {
         lore.add(Text.itemLore(line));
      }
      if (!lore.isEmpty()) {
         meta.lore(lore);
      }

      ItemTags.setString(meta, this.keys.itemId, definition.id());
      stack.setItemMeta(meta);
      return stack;
   }

   /** Id item pancing custom di stack (null = bukan item pancing custom). */
   public String itemId(ItemStack stack) {
      ItemMeta meta = meta(stack);
      return meta == null ? null : ItemTags.getString(meta, this.keys.itemId);
   }

   /** Undi bonus bahan upgrade saat menangkap ikan custom (drop-chance-percent per item). */
   public FishingItem rollBonusItem() {
      for (FishingItem candidate : this.items.values()) {
         if (candidate.dropChance() > 0.0D && this.random.nextDouble() * 100.0D < candidate.dropChance()) {
            return candidate;
         }
      }

      return null;
   }

   // ------------------------------------------------------------------ //
   // Rod: level, XP, attachment
   // ------------------------------------------------------------------ //

   public boolean isRod(ItemStack stack) {
      return !Items.isEmpty(stack) && stack.getType() == Material.FISHING_ROD;
   }

   public int rodLevel(ItemStack stack) {
      ItemMeta meta = meta(stack);
      int level = meta == null ? 1 : ItemTags.getInt(meta, this.keys.rodLevel, 1);
      return Math.max(1, Math.min(this.rodMaxLevel, level));
   }

   public double rodXp(ItemStack stack) {
      ItemMeta meta = meta(stack);
      return meta == null ? 0.0D : Math.max(0.0D, ItemTags.getDouble(meta, this.keys.rodXp, 0.0D));
   }

   public void addRodXp(ItemStack stack, double amount) {
      if (Items.isEmpty(stack) || amount <= 0.0D) {
         return;
      }

      ItemMeta meta = stack.getItemMeta();
      if (meta == null) {
         return;
      }

      ItemTags.setDouble(meta, this.keys.rodXp,
         Math.max(0.0D, ItemTags.getDouble(meta, this.keys.rodXp, 0.0D)) + amount);
      stack.setItemMeta(meta);
   }

   public double rodXpPerCatch() {
      return this.rodXpPerCatch;
   }

   public double rodXpPerCustomCatch() {
      return this.rodXpPerCustom;
   }

   /** XP rod yang dibutuhkan untuk upgrade ke level berikutnya (0 = hanya bahan). */
   public double xpNeeded(int targetLevel) {
      Double xp = this.rodXpNeeded.get(targetLevel);
      return xp == null ? 0.0D : xp;
   }

   /** Biaya bahan upgrade ke level target (id item -> jumlah; kosong = gratis). */
   public Map<String, Integer> upgradeCost(int targetLevel) {
      Map<String, Integer> cost = this.rodCosts.get(targetLevel);
      return cost == null ? Map.of() : cost;
   }

   /** Attachment terpasang (daftar id, urutan pemasangan; disimpan sebagai csv di PDC). */
   public List<String> attachments(ItemStack stack) {
      ItemMeta meta = meta(stack);
      String raw = meta == null ? null : ItemTags.getString(meta, this.keys.rodAttachments);
      List<String> result = new ArrayList<>();
      if (raw == null || raw.isEmpty()) {
         return result;
      }

      for (String token : raw.split(",")) {
         String id = token.trim().toLowerCase(Locale.ROOT);
         if (!id.isEmpty() && this.items.containsKey(id)) {
            result.add(id);
         }
      }

      return result;
   }

   public void writeAttachments(ItemStack stack, List<String> ids) {
      if (Items.isEmpty(stack)) {
         return;
      }

      ItemMeta meta = stack.getItemMeta();
      if (meta == null) {
         return;
      }

      ItemTags.setString(meta, this.keys.rodAttachments, String.join(",", ids));
      stack.setItemMeta(meta);
   }

   /**
    * Total satu efek dari level rod + attachment terpasang.
    * Efek yang dikenal: bite-speed, custom-chance, rarity-boost, value, xp, double-catch.
    */
   public double effectTotal(ItemStack rod, String effect) {
      double total = 0.0D;
      int level = this.rodLevel(rod);

      for (Map.Entry<Integer, Map<String, Double>> entry : this.rodEffects.entrySet()) {
         if (entry.getKey() <= level) {
            Double value = entry.getValue().get(effect);
            if (value != null) {
               total += value;
            }
         }
      }

      for (String id : this.attachments(rod)) {
         FishingItem attachment = this.items.get(id);
         if (attachment != null && attachment.isAttachment() && effect.equals(attachment.effect())) {
            total += attachment.value();
         }
      }

      return total;
   }

   /** Segarkan lore rod (level, XP, attachment) - dipanggil setelah upgrade/pasang-copot. */
   public void refreshRodLore(ItemStack rod) {
      if (!this.isRod(rod)) {
         return;
      }

      ItemMeta meta = rod.getItemMeta();
      if (meta == null) {
         return;
      }

      try {
         int level = this.rodLevel(rod);
         List<Component> lore = new ArrayList<>();
         lore.add(Text.itemLore(this.plugin.messages().raw("fishing.rod-lore-level",
            "level", Integer.toString(level), "max", Integer.toString(this.rodMaxLevel))));

         double needed = this.xpNeeded(level + 1);
         if (level < this.rodMaxLevel) {
            lore.add(Text.itemLore(this.plugin.messages().raw("fishing.rod-lore-xp",
               "xp", String.format(Locale.ROOT, "%.0f", this.rodXp(rod)),
               "needed", String.format(Locale.ROOT, "%.0f", needed))));
         }

         List<String> attached = this.attachments(rod);
         lore.add(Text.itemLore(this.plugin.messages().raw("fishing.rod-lore-attachments",
            "count", Integer.toString(attached.size()),
            "slots", Integer.toString(this.attachmentSlots))));

         for (String id : attached) {
            FishingItem attachment = this.items.get(id);
            if (attachment != null) {
               lore.add(Text.itemLore(this.plugin.messages().raw("fishing.rod-lore-attachment",
                  "name", attachment.itemName())));
            }
         }

         meta.lore(lore);
         rod.setItemMeta(meta);
      } catch (Throwable throwable) {
         this.plugin.debug("Fishing: gagal menulis lore rod (" + throwable + ").");
      }
   }

   // ------------------------------------------------------------------ //

   private static ItemMeta meta(ItemStack stack) {
      return Items.isEmpty(stack) || !stack.hasItemMeta() ? null : stack.getItemMeta();
   }

   private static Material parseMaterial(String token, Material fallback) {
      if (token == null || token.isEmpty()) {
         return fallback;
      }

      try {
         return Material.valueOf(token.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException exception) {
         return fallback;
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
}
