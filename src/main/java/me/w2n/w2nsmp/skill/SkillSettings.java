package me.w2n.w2nsmp.skill;

import java.util.ArrayList;
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
   private int buffUnlockLevel = 1;
   private double buffPerLevel;
   private double buffMax;
   private int hasteEveryLevels = 10;
   private int hasteMaxAmplifier = 4;
   private double healPerLevel;
   private double healMax;
   private int healDelaySeconds = 10;
   private Set<String> causes = Set.of();
   private Set<Material> blocks = EnumSet.noneOf(Material.class);
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
      settings.buffUnlockLevel = Math.max(1, config.getInt(path + ".buff.unlock-level", settings.buffUnlockLevel));
      settings.buffPerLevel = Math.max(0.0D, config.getDouble(path + ".buff.per-level", settings.buffPerLevel));
      settings.buffMax = Math.max(0.0D, config.getDouble(path + ".buff.max", settings.buffMax));
      settings.hasteEveryLevels = Math.max(1, config.getInt(path + ".buff.haste-every-levels", settings.hasteEveryLevels));
      settings.hasteMaxAmplifier = Math.max(0, config.getInt(path + ".buff.haste-max-amplifier", settings.hasteMaxAmplifier));
      settings.healPerLevel = Math.max(0.0D, config.getDouble(path + ".buff.heal-per-level", settings.healPerLevel));
      settings.healMax = Math.max(0.0D, config.getDouble(path + ".buff.heal-max", settings.healMax));
      settings.healDelaySeconds = Math.max(0, config.getInt(path + ".buff.heal-delay-seconds", settings.healDelaySeconds));
      settings.causes = readStrings(config, path + ".buff.causes");
      settings.blocks = readMaterials(plugin, config, path + ".blocks", settings.unknownMaterials);
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
            this.buffPerLevel = 0.5D;
            this.buffMax = 25.0D;
         }
         case DEFENSE -> {
            this.xpDamageTaken = 0.8D;
            this.buffPerLevel = 0.4D;
            this.buffMax = 20.0D;
         }
         case ARCHERY -> {
            this.xpDamageDealt = 1.2D;
            this.xpPvpMultiplier = 1.5D;
            this.xpKillMob = 10.0D;
            this.xpKillPlayer = 30.0D;
            this.buffPerLevel = 0.6D;
            this.buffMax = 30.0D;
         }
         case AGILITY -> {
            this.xpPerBlockTravelled = 0.35D;
            this.xpMinDistance = 0.5D;
            this.buffPerLevel = 0.4D;
            this.buffMax = 20.0D;
         }
         case MINING -> {
            this.xpPerBlock = 3.0D;
            this.buffUnlockLevel = 5;
            this.hasteEveryLevels = 10;
            this.hasteMaxAmplifier = 4;
         }
         case WOODCUTTING -> {
            this.xpPerBlock = 4.0D;
            this.buffPerLevel = 0.4D;
            this.buffMax = 20.0D;
         }
         case FARMING -> {
            this.xpPerBlock = 5.0D;
            this.buffPerLevel = 0.5D;
            this.buffMax = 25.0D;
         }
         case FISHING -> {
            this.xpPerCatch = 18.0D;
            this.buffUnlockLevel = 3;
            this.buffPerLevel = 0.5D;
            this.buffMax = 25.0D;
         }
         case ENDURANCE -> {
            this.xpPerMinuteOnline = 20.0D;
            this.buffPerLevel = 0.6D;
            this.buffMax = 30.0D;
            this.causes = Set.of("FALL", "FIRE", "FIRE_TICK", "LAVA", "HOT_FLOOR", "DROWNING", "SUFFOCATION", "STARVATION", "FREEZE", "FLY_INTO_WALL", "CRAMMING", "DRYOUT", "MELTING");
         }
         case VITALITY -> {
            this.xpDamageSurvived = 1.0D;
            this.xpLowHealthThreshold = 6.0D;
            this.xpLowHealthMultiplier = 2.0D;
            this.buffUnlockLevel = 3;
            this.healPerLevel = 0.04D;
            this.healMax = 2.0D;
            this.healDelaySeconds = 10;
         }
         case RECOVERY -> {
            this.xpPerHeartRegen = 8.0D;
            this.buffUnlockLevel = 3;
            this.buffPerLevel = 0.6D;
            this.buffMax = 30.0D;
         }
      }
   }

   private static Set<String> readStrings(FileConfiguration config, String path) {
      List<String> values = config.getStringList(path);
      if (values.isEmpty()) {
         return Set.of();
      }

      Set<String> result = new LinkedHashSet<>(values.size());

      for (String value : values) {
         if (value != null && !value.isBlank()) {
            result.add(value.trim().toUpperCase(Locale.ROOT));
         }
      }

      return result;
   }

   /**
    * Daftar nama material dari config menjadi {@link EnumSet}. Nama yang tidak dikenal versi
    * server ini dicatat lalu dilewati - bukan membuat plugin gagal dimuat.
    */
   private static Set<Material> readMaterials(W2NSMP plugin, FileConfiguration config, String path, List<String> unknown) {
      List<String> names = config.getStringList(path);
      if (names.isEmpty()) {
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

   public int buffUnlockLevel() {
      return this.buffUnlockLevel;
   }

   public double buffPerLevel() {
      return this.buffPerLevel;
   }

   public double buffMax() {
      return this.buffMax;
   }

   public int hasteEveryLevels() {
      return this.hasteEveryLevels;
   }

   public int hasteMaxAmplifier() {
      return this.hasteMaxAmplifier;
   }

   public double healPerLevel() {
      return this.healPerLevel;
   }

   public double healMax() {
      return this.healMax;
   }

   public int healDelaySeconds() {
      return this.healDelaySeconds;
   }

   public Set<String> causes() {
      return this.causes;
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

   /**
    * Nilai buff pada level tertentu (persen, heart, atau peluang), 0 bila level masih di bawah
    * {@code buff.unlock-level} atau buff dimatikan.
    */
   public double buffValue(int level) {
      if (!this.enabled || level < this.buffUnlockLevel || this.buffPerLevel <= 0.0D) {
         return 0.0D;
      }

      double value = this.buffPerLevel * (level - this.buffUnlockLevel + 1);
      return this.buffMax > 0.0D ? Math.min(this.buffMax, value) : value;
   }

   /** Amplifier Haste (0 = tidak ada efek) untuk level tertentu. */
   public int hasteAmplifier(int level) {
      if (!this.enabled || level < this.buffUnlockLevel || this.hasteMaxAmplifier <= 0) {
         return 0;
      }

      int amplifier = (level - this.buffUnlockLevel + 1) / Math.max(1, this.hasteEveryLevels);
      return Math.max(0, Math.min(this.hasteMaxAmplifier - 1, amplifier));
   }

   /** Jumlah heart yang dipulihkan buff vitalitas tiap interval (0 = tidak ada). */
   public double healAmount(int level) {
      if (!this.enabled || level < this.buffUnlockLevel || this.healPerLevel <= 0.0D) {
         return 0.0D;
      }

      double amount = this.healPerLevel * (level - this.buffUnlockLevel + 1);
      return this.healMax > 0.0D ? Math.min(this.healMax, amount) : amount;
   }

   public boolean hasBlocks() {
      return !this.blocks.isEmpty();
   }

   public boolean tracksCause(String causeName) {
      return this.causes.isEmpty() || (causeName != null && this.causes.contains(causeName.toUpperCase(Locale.ROOT)));
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
         + trim(this.buffPerLevel)
         + "%/lvl maks "
         + trim(this.buffMax)
         + "%"
         + (this.blocks.isEmpty() ? "" : " blok=" + this.blocks.size());
   }

   private static String trim(double value) {
      return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(Math.round(value * 100.0D) / 100.0D);
   }
}
