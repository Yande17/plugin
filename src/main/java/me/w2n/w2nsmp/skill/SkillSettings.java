package me.w2n.w2nsmp.skill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import me.w2n.w2nsmp.W2NSMP;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Snapshot config satu skill.
 *
 * <p>Dibaca sekali saat {@code load}/{@code reload} supaya jalur permainan yang panas
 * (block break, damage, move) tidak pernah menyentuh {@code config.yml}: semua angka sudah
 * berupa field, daftar material sudah jadi {@link EnumSet}, dan daftar penyebab damage sudah
 * jadi {@link Set} string.
 *
 * <p>Setiap skill hanya memakai sebagian field; sisanya bernilai 0 dan berarti "tidak dipakai".
 * Nilai bawaan di kelas ini identik dengan nilai bawaan di {@code config.yml}, jadi plugin tetap
 * berjalan wajar walau bagian {@code skills.list.<key>} dihapus dari config pemain.
 */
public final class SkillSettings {
   private final SkillType type;
   private boolean enabled = true;
   private String iconName;
   private Material icon = Material.BEDROCK;
   private int slot;
   private double xpMultiplier = 1.0D;
   private double xpDamageDealt;
   private double xpPvpMultiplier = 1.0D;
   private double xpKillMob;
   private double xpKillPlayer;
   private double xpDamageTaken;
   private double xpDamageSurvived;
   private double xpLowHealthThreshold;
   private double xpLowHealthMultiplier = 1.0D;
   private double xpPerBlock;
   private double xpPerCatch;
   private double xpPerBlockTravelled;
   private double xpMinDistance = 0.5D;
   private double xpPerMinuteOnline;
   private double xpPerHeartRegen;
   /**
    * Buff milik skill ini, urut sesuai config. Elemen pertama adalah <b>buff utama</b> (jenisnya
    * sama dengan {@link SkillType#buff()}); elemen berikutnya terbuka pada level milestone.
    * Sejak v1.3.0 satu skill boleh punya lebih dari satu buff.
    */
   private List<SkillBuff> buffs = List.of();
   private Set<Material> blocks = EnumSet.noneOf(Material.class);
   /** Nama material bawaan (dipakai bila config server lama belum memuat skills.list.<key>.blocks). */
   private Set<String> defaultBlocks = Set.of();
   private final List<String> unknownMaterials = new ArrayList<>();
   private final List<String> unknownCauses = new ArrayList<>();

   private SkillSettings(SkillType type) {
      this.type = type;
   }

   /** Baca {@code skills.list.<key>} dari config.yml, lengkap dengan nilai bawaan per skill. */
   public static SkillSettings load(W2NSMP plugin, SkillType type, int defaultSlot) {
      SkillSettings settings = new SkillSettings(type);
      settings.slot = defaultSlot;
      settings.iconName = type.defaultIcon();
      settings.applyDefaults(type);
      FileConfiguration config = plugin.config().raw();
      String path = type.configPath();
      settings.enabled = config.getBoolean(path + ".enabled", settings.enabled);
      settings.slot = Math.max(0, Math.min(53, config.getInt(path + ".slot", settings.slot)));
      settings.iconName = config.getString(path + ".icon", settings.iconName);
      Material material = resolveMaterial(settings.iconName);
      if (material == null) {
         settings.unknownMaterials.add(String.valueOf(settings.iconName));
         material = resolveMaterial(type.defaultIcon());
      }

      settings.icon = material == null ? Material.BEDROCK : material;
      settings.xpMultiplier = Math.max(0.0D, config.getDouble(path + ".xp.multiplier", settings.xpMultiplier));
      settings.xpDamageDealt = Math.max(0.0D, config.getDouble(path + ".xp.damage-dealt", settings.xpDamageDealt));
      settings.xpPvpMultiplier = Math.max(0.0D, config.getDouble(path + ".xp.pvp-multiplier", settings.xpPvpMultiplier));
      settings.xpKillMob = Math.max(0.0D, config.getDouble(path + ".xp.kill-mob", settings.xpKillMob));
      settings.xpKillPlayer = Math.max(0.0D, config.getDouble(path + ".xp.kill-player", settings.xpKillPlayer));
      settings.xpDamageTaken = Math.max(0.0D, config.getDouble(path + ".xp.damage-taken", settings.xpDamageTaken));
      settings.xpDamageSurvived = Math.max(0.0D, config.getDouble(path + ".xp.damage-survived", settings.xpDamageSurvived));
      settings.xpLowHealthThreshold = Math.max(0.0D, config.getDouble(path + ".xp.low-health-threshold", settings.xpLowHealthThreshold));
      settings.xpLowHealthMultiplier = Math.max(0.0D, config.getDouble(path + ".xp.low-health-multiplier", settings.xpLowHealthMultiplier));
      settings.xpPerBlock = Math.max(0.0D, config.getDouble(path + ".xp.per-block", settings.xpPerBlock));
      settings.xpPerCatch = Math.max(0.0D, config.getDouble(path + ".xp.per-catch", settings.xpPerCatch));
      settings.xpPerBlockTravelled = Math.max(0.0D, config.getDouble(path + ".xp.per-block-travelled", settings.xpPerBlockTravelled));
      settings.xpMinDistance = Math.max(0.0D, config.getDouble(path + ".xp.min-distance", settings.xpMinDistance));
      settings.xpPerMinuteOnline = Math.max(0.0D, config.getDouble(path + ".xp.per-minute-online", settings.xpPerMinuteOnline));
      settings.xpPerHeartRegen = Math.max(0.0D, config.getDouble(path + ".xp.per-heart-regen", settings.xpPerHeartRegen));
      List<SkillBuff> configured = readBuffs(plugin, config.getConfigurationSection(path + ".buffs"), path + ".buffs");
      if (!configured.isEmpty()) {
         settings.buffs = List.copyOf(configured);
         if (config.isSet(path + ".buff") && plugin != null) {
            // Config hasil upgrade bisa memuat keduanya; skema baru (buffs:) yang dipakai.
            plugin.getLogger().info("[Skill] " + path + ": bagian 'buffs:' dipakai, bagian lama 'buff:' diabaikan.");
         }
      } else {
         // Config lama (v1.2.x) hanya punya satu bagian "buff:". Angka-angkanya tetap dipakai untuk
         // buff utama supaya server yang upgrade tidak kehilangan setelan, sementara buff milestone
         // bawaan v1.3.0 tetap ikut terbawa.
         settings.buffs = applyLegacyBuff(settings.buffs, config, path + ".buff", settings);
      }

      String blocksPath = path + ".blocks";
      settings.blocks = config.isList(blocksPath)
         ? resolveMaterials(plugin, config.getStringList(blocksPath), blocksPath, settings.unknownMaterials)
         : resolveMaterials(plugin, settings.defaultBlocks, blocksPath, settings.unknownMaterials);
      return settings;
   }

   /** Nilai bawaan bila config pemain tidak memuat bagian skill ini. */
   private void applyDefaults(SkillType type) {
      switch (type) {
         case FIGHTING -> {
            this.xpDamageDealt = 1.0D;
            this.xpPvpMultiplier = 1.5D;
            this.xpKillMob = 15.0D;
            this.xpKillPlayer = 40.0D;
         }
         case DEFENSE -> {
            this.xpDamageTaken = 0.8D;
         }
         case ARCHERY -> {
            this.xpDamageDealt = 1.2D;
            this.xpPvpMultiplier = 1.5D;
            this.xpKillMob = 10.0D;
            this.xpKillPlayer = 30.0D;
         }
         case AGILITY -> {
            this.xpPerBlockTravelled = 0.35D;
            this.xpMinDistance = 0.5D;
         }
         case MINING -> {
            this.xpPerBlock = 3.0D;
            this.defaultBlocks = Set.of(
               "STONE", "COBBLESTONE", "DEEPSLATE", "COBBLED_DEEPSLATE", "GRANITE", "DIORITE", "ANDESITE", "TUFF", "CALCITE", "DRIPSTONE_BLOCK",
               "TERRACOTTA", "NETHERRACK", "BLACKSTONE", "BASALT", "SMOOTH_BASALT", "END_STONE", "OBSIDIAN", "CRYING_OBSIDIAN", "ANCIENT_DEBRIS",
               "AMETHYST_BLOCK", "BUDDING_AMETHYST", "COAL_ORE", "DEEPSLATE_COAL_ORE", "IRON_ORE", "DEEPSLATE_IRON_ORE", "COPPER_ORE",
               "DEEPSLATE_COPPER_ORE", "GOLD_ORE", "DEEPSLATE_GOLD_ORE", "REDSTONE_ORE", "DEEPSLATE_REDSTONE_ORE", "LAPIS_ORE",
               "DEEPSLATE_LAPIS_ORE", "EMERALD_ORE", "DEEPSLATE_EMERALD_ORE", "DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE", "NETHER_GOLD_ORE",
               "NETHER_QUARTZ_ORE"
            );
         }
         case WOODCUTTING -> {
            this.xpPerBlock = 4.0D;
            this.defaultBlocks = Set.of(
               "OAK_LOG", "SPRUCE_LOG", "BIRCH_LOG", "JUNGLE_LOG", "ACACIA_LOG", "DARK_OAK_LOG", "MANGROVE_LOG", "CHERRY_LOG", "PALE_OAK_LOG",
               "CRIMSON_STEM", "WARPED_STEM", "OAK_WOOD", "SPRUCE_WOOD", "BIRCH_WOOD", "JUNGLE_WOOD", "ACACIA_WOOD", "DARK_OAK_WOOD",
               "MANGROVE_WOOD", "CHERRY_WOOD", "PALE_OAK_WOOD", "CRIMSON_HYPHAE", "WARPED_HYPHAE", "BAMBOO_BLOCK"
            );
         }
         case FARMING -> {
            this.xpPerBlock = 5.0D;
            this.defaultBlocks = Set.of(
               "WHEAT", "CARROTS", "POTATOES", "BEETROOTS", "NETHER_WART", "MELON", "PUMPKIN", "SUGAR_CANE", "BAMBOO", "COCOA", "SWEET_BERRY_BUSH",
               "CACTUS", "KELP", "KELP_PLANT", "SEA_PICKLE", "CHORUS_FLOWER", "CAVE_VINES", "CAVE_VINES_PLANT", "TORCHFLOWER_CROP", "PITCHER_CROP"
            );
         }
         case FISHING -> {
            this.xpPerCatch = 18.0D;
         }
         case ENDURANCE -> {
            this.xpPerMinuteOnline = 20.0D;
         }
         case VITALITY -> {
            this.xpDamageSurvived = 1.0D;
            this.xpLowHealthThreshold = 6.0D;
            this.xpLowHealthMultiplier = 2.0D;
         }
         case RECOVERY -> {
            this.xpPerHeartRegen = 8.0D;
         }
      }

      this.buffs = defaultBuffs(type);
   }

   /**
    * Buff bawaan tiap skill (v1.3.0: tiga buff per skill, terbuka bertahap).
    *
    * <p>Angkanya sengaja <b>kecil</b>: level maksimum 50 diniatkan sebagai progresi awal game, jadi
    * buff di level 50 harus terasa membantu tanpa membuat pemain kebal atau merusak ekonomi server
    * (damage melee maksimal +15%, pengurangan damage maksimal -17%, peluang loot/drop ganda di
    * bawah 10%, XP vanilla tambahan di bawah 20%). Nilai di sini identik dengan {@code config.yml};
    * {@code tools/verify_feature.py} membandingkan keduanya supaya tidak pernah berbeda.
    */
   private static List<SkillBuff> defaultBuffs(SkillType type) {
      switch (type) {
         case FIGHTING -> {
            return List.of(
               SkillBuff.of(BuffKind.MELEE_DAMAGE, 1, 0.30D, 15.0D),
               SkillBuff.of(BuffKind.CRIT_CHANCE, 10, 0.25D, 12.5D).power(50.0D),
               SkillBuff.of(BuffKind.MOB_LOOT, 25, 0.30D, 9.0D)
            );
         }
         case DEFENSE -> {
            return List.of(
               SkillBuff.of(BuffKind.DAMAGE_REDUCTION, 1, 0.28D, 14.0D),
               SkillBuff.of(BuffKind.BLOCK_CHANCE, 15, 0.20D, 10.0D).power(50.0D),
               SkillBuff.of(BuffKind.DAMAGE_REDUCTION, 30, 0.15D, 6.0D)
            );
         }
         case ARCHERY -> {
            return List.of(
               SkillBuff.of(BuffKind.PROJECTILE_DAMAGE, 1, 0.35D, 18.0D),
               SkillBuff.of(BuffKind.CRIT_CHANCE, 12, 0.30D, 15.0D).power(50.0D),
               SkillBuff.of(BuffKind.MOB_LOOT, 28, 0.25D, 8.0D)
            );
         }
         case AGILITY -> {
            return List.of(
               SkillBuff.of(BuffKind.WALK_SPEED, 1, 0.25D, 12.5D),
               SkillBuff.of(BuffKind.ENVIRONMENT_REDUCTION, 10, 0.50D, 20.0D).causes(Set.of("FALL", "FLY_INTO_WALL")),
               SkillBuff.potion(BuffKind.POTION, "slow_falling", 25, 25, 0)
            );
         }
         case MINING -> {
            return List.of(
               SkillBuff.potion(BuffKind.HASTE, "haste", 5, 12, 2),
               SkillBuff.of(BuffKind.VANILLA_XP, 15, 0.40D, 20.0D),
               SkillBuff.of(BuffKind.DOUBLE_XP, 30, 0.25D, 8.0D)
            );
         }
         case WOODCUTTING -> {
            return List.of(
               SkillBuff.of(BuffKind.EXTRA_DROP, 1, 0.30D, 15.0D),
               SkillBuff.of(BuffKind.EXTRA_DROP, 22, 0.20D, 8.0D),
               SkillBuff.of(BuffKind.DOUBLE_XP, 32, 0.30D, 7.0D)
            );
         }
         case FARMING -> {
            return List.of(
               SkillBuff.of(BuffKind.EXTRA_DROP, 1, 0.30D, 15.0D),
               SkillBuff.of(BuffKind.EXTRA_DROP, 20, 0.20D, 10.0D),
               SkillBuff.of(BuffKind.DOUBLE_XP, 35, 0.30D, 6.0D)
            );
         }
         case FISHING -> {
            return List.of(
               SkillBuff.of(BuffKind.EXTRA_CATCH, 1, 0.35D, 18.0D),
               SkillBuff.of(BuffKind.EXTRA_CATCH, 25, 0.20D, 10.0D),
               SkillBuff.of(BuffKind.DOUBLE_XP, 30, 0.30D, 8.0D)
            );
         }
         case ENDURANCE -> {
            return List.of(
               SkillBuff.of(BuffKind.ENVIRONMENT_REDUCTION, 1, 0.40D, 20.0D).causes(Set.of(
                  "FALL", "FIRE", "FIRE_TICK", "LAVA", "HOT_FLOOR", "DROWNING", "SUFFOCATION", "STARVATION",
                  "FREEZE", "FLY_INTO_WALL", "CRAMMING", "DRYOUT", "MELTING"
               )),
               SkillBuff.of(BuffKind.BLOCK_CHANCE, 12, 0.15D, 7.5D).power(40.0D),
               SkillBuff.potion(BuffKind.POTION, "fire_resistance", 40, 25, 0)
            );
         }
         case VITALITY -> {
            return List.of(
               SkillBuff.of(BuffKind.PASSIVE_HEAL, 3, 0.03D, 1.5D).delay(10),
               SkillBuff.potion(BuffKind.POTION, "absorption", 15, 20, 1),
               SkillBuff.of(BuffKind.REGEN_BOOST, 28, 0.25D, 10.0D)
            );
         }
         case RECOVERY -> {
            return List.of(
               SkillBuff.of(BuffKind.REGEN_BOOST, 3, 0.40D, 20.0D),
               SkillBuff.of(BuffKind.PASSIVE_HEAL, 20, 0.02D, 1.0D).delay(12),
               SkillBuff.potion(BuffKind.POTION, "health_boost", 40, 12, 0)
            );
         }
         default -> {
            return List.of();
         }
      }
   }

   /**
    * Baca bagian {@code skills.list.<key>.buffs} (peta bernomor: {@code 1:}, {@code 2:}, ...).
    * Buff dengan jenis tak dikenal dilewati dan dicatat di log supaya salah ketik segera terlihat.
    */
   private static List<SkillBuff> readBuffs(W2NSMP plugin, ConfigurationSection section, String path) {
      List<SkillBuff> result = new ArrayList<>();
      if (section == null) {
         return result;
      }

      Set<String> keys = section.getKeys(false);
      if (keys == null || keys.isEmpty()) {
         return result;
      }

      List<String> ordered = new ArrayList<>(keys);
      ordered.sort(SkillSettings::compareBuffKeys);

      for (String key : ordered) {
         ConfigurationSection sub = section.getConfigurationSection(key);
         if (sub == null) {
            continue;
         }

         BuffKind kind = BuffKind.fromKey(sub.getString("kind", ""));
         if (kind == null) {
            if (plugin != null) {
               plugin.getLogger().warning("[Skill] Jenis buff tidak dikenal di " + path + "." + key
                  + ".kind - buff dilewati. Pilihan: " + String.join(", ", BuffKind.keys()));
            }

            continue;
         }

         SkillBuff buff = SkillBuff.of(kind,
            Math.max(1, sub.getInt("unlock-level", 1)),
            Math.max(0.0D, sub.getDouble("per-level", 0.0D)),
            Math.max(0.0D, sub.getDouble("max", 0.0D)));
         buff.power(Math.max(0.0D, sub.getDouble("power", 0.0D)));
         buff.potionKey(sub.getString("potion", kind == BuffKind.HASTE ? "haste" : ""));
         buff.amplifier(Math.max(1, sub.getInt("amplifier-every-levels", 15)),
            Math.max(0, sub.getInt("amplifier-max", kind.isPotion() ? 0 : 0)));
         buff.delay(Math.max(1, sub.getInt("delay-seconds", 10)));
         if (sub.isList("causes")) {
            buff.causes(normalizeCauses(sub.getStringList("causes")));
         }

         result.add(buff);
      }

      return result;
   }

   /**
    * Terapkan bagian lama {@code buff:} (satu buff, config v1.2.x) ke buff utama.
    * Kunci lama {@code haste-*} dipetakan ke {@code amplifier-*}, dan {@code heal-per-level}/
    * {@code heal-max} dipetakan ke {@code per-level}/{@code max}.
    */
   private static List<SkillBuff> applyLegacyBuff(List<SkillBuff> defaults, FileConfiguration config, String path,
      SkillSettings settings) {
      List<SkillBuff> result = new ArrayList<>();

      for (SkillBuff buff : defaults) {
         result.add(buff.copy());
      }

      if (result.isEmpty()) {
         return List.of();
      }

      SkillBuff old = result.get(0);
      BuffKind kind = BuffKind.fromKey(config.getString(path + ".kind", ""));
      double perLevel = Math.max(0.0D, config.getDouble(path + ".per-level", old.perLevel()));
      double max = Math.max(0.0D, config.getDouble(path + ".max", old.max()));
      double healPerLevel = config.getDouble(path + ".heal-per-level", -1.0D);
      if (healPerLevel >= 0.0D) {
         perLevel = healPerLevel;
      }

      double healMax = config.getDouble(path + ".heal-max", -1.0D);
      if (healMax >= 0.0D) {
         max = healMax;
      }

      SkillBuff primary = SkillBuff.of(kind == null ? old.kind() : kind,
         Math.max(1, config.getInt(path + ".unlock-level", old.unlockLevel())), perLevel, max);
      primary.power(Math.max(0.0D, config.getDouble(path + ".power", old.power())));
      primary.potionKey(old.potionKey().isEmpty() ? config.getString(path + ".potion", "") : old.potionKey());
      int everyLevels = config.getInt(path + ".haste-every-levels", old.amplifierEveryLevels());
      // config lama menulis jumlah tingkat (Haste I..IV = 4); skema baru menulis amplifier tertinggi (0..3)
      int amplifierMax = config.getInt(path + ".haste-max-amplifier", old.amplifierMax() + 1) - 1;
      primary.amplifier(Math.max(1, everyLevels), Math.max(-1, amplifierMax));
      primary.delay(Math.max(1, config.getInt(path + ".heal-delay-seconds", old.delaySeconds())));
      if (config.isList(path + ".causes")) {
         List<String> causes = config.getStringList(path + ".causes");
         if (causes == null || causes.isEmpty()) {
            settings.unknownCauses.add(path + ".causes kosong");
         }

         primary.causes(normalizeCauses(causes));
      } else {
         primary.causes(old.causes());
      }

      result.set(0, primary);
      return List.copyOf(result);
   }

   /** Normalisasi nama penyebab damage ke huruf besar (format {@code DamageCause.name()}). */
   private static Set<String> normalizeCauses(List<String> raw) {
      if (raw == null || raw.isEmpty()) {
         return Set.of();
      }

      Set<String> causes = new LinkedHashSet<>(raw.size());

      for (String entry : raw) {
         if (entry == null) {
            continue;
         }

         String name = entry.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
         if (!name.isEmpty()) {
            causes.add(name);
         }
      }

      return Collections.unmodifiableSet(causes);
   }

   /** Urutkan kunci buff: angka lebih dulu (1, 2, 10), sisanya alfabetis. */
   private static int compareBuffKeys(String left, String right) {
      Integer leftNumber = parseNumber(left);
      Integer rightNumber = parseNumber(right);
      if (leftNumber != null && rightNumber != null) {
         return Integer.compare(leftNumber, rightNumber);
      }

      if (leftNumber != null) {
         return -1;
      }

      if (rightNumber != null) {
         return 1;
      }

      return left.compareTo(right);
   }

   private static Integer parseNumber(String value) {
      if (value == null) {
         return null;
      }

      try {
         return Integer.valueOf(value.trim());
      } catch (RuntimeException ex) {
         return null;
      }
   }

   /**
    * Daftar nama material dari config menjadi {@link EnumSet}. Nama yang tidak dikenal versi
    * server ini dicatat lalu dilewati - bukan membuat plugin gagal dimuat.
    */
   private static Set<Material> resolveMaterials(W2NSMP plugin, Collection<String> names, String path, List<String> unknown) {
      if (names == null || names.isEmpty()) {
         return EnumSet.noneOf(Material.class);
      }

      Set<Material> result = EnumSet.noneOf(Material.class);

      for (String raw : names) {
         if (raw == null || raw.isBlank()) {
            continue;
         }

         Material material = resolveMaterial(raw);
         if (material == null) {
            unknown.add(raw.trim());
         } else {
            result.add(material);
         }
      }

      if (!unknown.isEmpty()) {
         plugin.debug("Skill: material tidak dikenal di " + path + " (dilewati): " + String.join(", ", unknown));
      }

      return result;
   }

   /** Cari material dari namanya tanpa pernah melempar exception. */
   public static Material resolveMaterial(String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
      } catch (RuntimeException exception) {
         return null;
      }
   }

   public SkillType type() {
      return this.type;
   }

   public boolean enabled() {
      return this.enabled;
   }

   public String iconName() {
      return this.iconName;
   }

   public Material icon() {
      return this.icon;
   }

   public int slot() {
      return this.slot;
   }

   public double xpMultiplier() {
      return this.xpMultiplier;
   }

   public double xpDamageDealt() {
      return this.xpDamageDealt;
   }

   public double xpPvpMultiplier() {
      return this.xpPvpMultiplier;
   }

   public double xpKillMob() {
      return this.xpKillMob;
   }

   public double xpKillPlayer() {
      return this.xpKillPlayer;
   }

   public double xpDamageTaken() {
      return this.xpDamageTaken;
   }

   public double xpDamageSurvived() {
      return this.xpDamageSurvived;
   }

   public double xpLowHealthThreshold() {
      return this.xpLowHealthThreshold;
   }

   public double xpLowHealthMultiplier() {
      return this.xpLowHealthMultiplier;
   }

   public double xpPerBlock() {
      return this.xpPerBlock;
   }

   public double xpPerCatch() {
      return this.xpPerCatch;
   }

   public double xpPerBlockTravelled() {
      return this.xpPerBlockTravelled;
   }

   public double xpMinDistance() {
      return this.xpMinDistance;
   }

   public double xpPerMinuteOnline() {
      return this.xpPerMinuteOnline;
   }

   public double xpPerHeartRegen() {
      return this.xpPerHeartRegen;
   }

   // ------------------------------------------------------------------ //
   //  Buff (v1.3.0: beberapa buff per skill)
   // ------------------------------------------------------------------ //

   /** Semua buff skill ini, urut config; kosong bila skill tidak punya buff. */
   public List<SkillBuff> buffs() {
      return this.buffs;
   }

   public int buffCount() {
      return this.buffs.size();
   }

   /** Buff utama (jenisnya sama dengan {@link SkillType#buff()}); null bila tidak ada. */
   public SkillBuff primaryBuff() {
      return this.buffs.isEmpty() ? null : this.buffs.get(0);
   }

   /** Buff pertama dengan jenis tertentu; null bila skill tidak punya buff jenis itu. */
   public SkillBuff firstBuff(BuffKind kind) {
      for (SkillBuff buff : this.buffs) {
         if (buff.kind() == kind) {
            return buff;
         }
      }

      return null;
   }

   /** Buff berikutnya yang belum terbuka pada level ini (untuk menu progres); null bila semua terbuka. */
   public SkillBuff nextBuff(int level) {
      SkillBuff found = null;

      for (SkillBuff buff : this.buffs) {
         if (buff.unlocked(level)) {
            continue;
         }

         if (found == null || buff.unlockLevel() < found.unlockLevel()) {
            found = buff;
         }
      }

      return found;
   }

   /** Jumlah buff yang sudah terbuka pada level ini. */
   public int unlockedBuffs(int level) {
      int count = 0;

      for (SkillBuff buff : this.buffs) {
         if (buff.unlocked(level)) {
            count++;
         }
      }

      return count;
   }

   public int buffUnlockLevel() {
      SkillBuff buff = this.primaryBuff();
      return buff == null ? 1 : buff.unlockLevel();
   }

   public double buffPerLevel() {
      SkillBuff buff = this.primaryBuff();
      return buff == null ? 0.0D : buff.perLevel();
   }

   public double buffMax() {
      SkillBuff buff = this.primaryBuff();
      return buff == null ? 0.0D : buff.max();
   }

   public int hasteEveryLevels() {
      SkillBuff buff = this.firstBuff(BuffKind.HASTE);
      return buff == null ? 10 : buff.amplifierEveryLevels();
   }

   /** Jumlah tingkat Haste (tampilan Haste I..N); 0 bila skill ini tidak punya Haste. */
   public int hasteMaxAmplifier() {
      SkillBuff buff = this.firstBuff(BuffKind.HASTE);
      return buff == null ? 0 : buff.amplifierMax() + 1;
   }

   public double healPerLevel() {
      SkillBuff buff = this.firstBuff(BuffKind.PASSIVE_HEAL);
      return buff == null ? 0.0D : buff.perLevel();
   }

   public double healMax() {
      SkillBuff buff = this.firstBuff(BuffKind.PASSIVE_HEAL);
      return buff == null ? 0.0D : buff.max();
   }

   public int healDelaySeconds() {
      SkillBuff buff = this.firstBuff(BuffKind.PASSIVE_HEAL);
      return buff == null ? 10 : buff.delaySeconds();
   }

   /** Gabungan penyebab damage dari semua buff lingkungan skill ini. */
   public Set<String> causes() {
      Set<String> result = new LinkedHashSet<>();

      for (SkillBuff buff : this.buffs) {
         if (buff.kind() == BuffKind.ENVIRONMENT_REDUCTION) {
            result.addAll(buff.causes());
         }
      }

      return Collections.unmodifiableSet(result);
   }

   public Set<Material> blocks() {
      return this.blocks;
   }

   public List<String> unknownMaterials() {
      return this.unknownMaterials;
   }

   public List<String> unknownCauses() {
      return this.unknownCauses;
   }

   public boolean hasBlocks() {
      return !this.blocks.isEmpty();
   }

   /**
    * Nilai buff utama pada level tertentu (persen, heart, atau peluang); 0 bila skill mati, level
    * masih di bawah {@code unlock-level}, atau buff jenis itu tidak ada.
    */
   public double buffValue(int level) {
      return this.buffValue(level, this.type.buff());
   }

   /**
    * Jumlah nilai semua buff dengan jenis tertentu pada level ini. Beberapa skill punya dua buff
    * sejenis (misalnya dua tingkat peluang drop tambahan) sehingga keduanya dijumlahkan di sini.
    */
   public double buffValue(int level, BuffKind kind) {
      if (!this.enabled || kind == null) {
         return 0.0D;
      }

      double total = 0.0D;

      for (SkillBuff buff : this.buffs) {
         if (buff.kind() == kind) {
            total += buff.value(level);
         }
      }

      return total > 0.0D ? total : 0.0D;
   }

   /**
    * Kekuatan tambahan buff jenis itu (persen damage ekstra saat critical, atau persen damage yang
    * ditahan saat block) - diambil dari buff pertama yang sudah terbuka pada level ini.
    */
   public double buffPower(int level, BuffKind kind) {
      if (!this.enabled || kind == null) {
         return 0.0D;
      }

      for (SkillBuff buff : this.buffs) {
         if (buff.kind() == kind && buff.unlocked(level)) {
            return buff.power();
         }
      }

      return 0.0D;
   }

   /** Amplifier Haste (0 = tingkat pertama) untuk level tertentu; -1 bila belum terbuka. */
   public int hasteAmplifier(int level) {
      SkillBuff buff = this.firstBuff(BuffKind.HASTE);
      return !this.enabled || buff == null ? -1 : buff.amplifier(level);
   }

   /** Jumlah heart yang dipulihkan buff heal pasif tiap interval (0 = tidak ada). */
   public double healAmount(int level) {
      return this.enabled ? this.buffValue(level, BuffKind.PASSIVE_HEAL) : 0.0D;
   }

   /** Apakah skill ini mengurangi damage dari penyebab bernama {@code causeName}. */
   public boolean tracksCause(String causeName) {
      if (causeName == null) {
         return false;
      }

      for (SkillBuff buff : this.buffs) {
         if (buff.kind() == BuffKind.ENVIRONMENT_REDUCTION && buff.tracksCause(causeName)) {
            return true;
         }
      }

      return false;
   }

   /** Pengurangan damage lingkungan (persen) untuk penyebab tertentu pada level ini. */
   public double environmentReduction(int level, String causeName) {
      if (!this.enabled || causeName == null) {
         return 0.0D;
      }

      double total = 0.0D;

      for (SkillBuff buff : this.buffs) {
         if (buff.kind() == BuffKind.ENVIRONMENT_REDUCTION && buff.tracksCause(causeName)) {
            total += buff.value(level);
         }
      }

      return total > 0.0D ? total : 0.0D;
   }

   /** Ringkasan satu baris untuk log diagnostik (/w2nsmp debug & startup). */
   public String summary() {
      return this.type.key()
         + (this.enabled ? "" : " (mati)")
         + " xp="
         + trim(this.xpDamageDealt)
         + "/"
         + trim(this.xpPerBlock)
         + "/"
         + trim(this.xpPerCatch)
         + " buff="
         + this.buffs.size()
         + "x ("
         + trim(this.buffPerLevel())
         + "%/lvl maks "
         + trim(this.buffMax())
         + "%)"
         + (this.blocks.isEmpty() ? "" : " blok=" + this.blocks.size());
   }

   private static String trim(double value) {
      return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(Math.round(value * 100.0D) / 100.0D);
   }
}
