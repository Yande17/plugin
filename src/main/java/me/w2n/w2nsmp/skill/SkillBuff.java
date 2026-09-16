package me.w2n.w2nsmp.skill;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Satu buff milik sebuah skill (v1.3.0: tiap skill boleh punya beberapa buff).
 *
 * <p>Nilainya tumbuh linear dari level saat buff terbuka sampai plafon {@code max}:
 * <pre>
 *   level &lt; unlockLevel        -&gt; 0 (belum terbuka)
 *   level &gt;= unlockLevel        -&gt; min(max, perLevel * (level - unlockLevel + 1))
 * </pre>
 * Jadi buff yang terbuka di level 25 dengan {@code perLevel} 0,3 dan {@code max} 9 baru maksimal
 * di level 55 - dengan level maksimum 50 nilainya berhenti di 7,8%. Plafon tetap dijaga supaya
 * server yang menaikkan level maksimum tidak tiba-tiba kehilangan keseimbangan.
 *
 * <p>Buff potion (HASTE/POTION) memakai rumus tingkat (amplifier) yang naik bertahap:
 * {@code (level - unlockLevel) / amplifierEveryLevels}, dibatasi {@code amplifierMax}.
 */
public final class SkillBuff {
   private final BuffKind kind;
   private int unlockLevel = 1;
   private double perLevel = 0.0D;
   private double max = 0.0D;
   private double power = 0.0D;
   private String potionKey = "";
   private int amplifierEveryLevels = 15;
   /** Tingkat efek tertinggi (0 = hanya tingkat I); -1 berarti buff ini bukan buff potion. */
   private int amplifierMax = -1;
   private int delaySeconds = 10;
   private Set<String> causes = Collections.emptySet();

   private SkillBuff(BuffKind kind) {
      this.kind = kind;
      if (kind == BuffKind.HASTE) {
         this.potionKey = "haste";
      }
   }

   /** Buff baru; angka nol berarti "tidak ada efek" sehingga buff tidak pernah merusak permainan. */
   public static SkillBuff of(BuffKind kind, int unlockLevel, double perLevel, double max) {
      SkillBuff buff = new SkillBuff(kind);
      buff.unlockLevel = Math.max(1, unlockLevel);
      buff.perLevel = sanitize(perLevel);
      buff.max = sanitize(max);
      buff.amplifierMax = kind.isPotion() ? 0 : -1;
      return buff;
   }

   /** Buff potion generik (kunci efek dibaca dari config, mis. "regeneration"/"absorption"). */
   public static SkillBuff potion(BuffKind kind, String key, int unlockLevel, int everyLevels, int maxAmplifier) {
      SkillBuff buff = of(kind, unlockLevel, 0.0D, 0.0D);
      buff.potionKey = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
      buff.amplifierEveryLevels = Math.max(1, everyLevels);
      buff.amplifierMax = Math.max(0, maxAmplifier);
      return buff;
   }

   // ------------------------------------------------------------------ //
   //  Pembentuk (fluent) - dipakai saat membaca config / default
   // ------------------------------------------------------------------ //

   public SkillBuff power(double value) {
      this.power = sanitize(value);
      return this;
   }

   public SkillBuff potionKey(String key) {
      this.potionKey = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
      return this;
   }

   public SkillBuff amplifier(int everyLevels, int maxAmplifier) {
      this.amplifierEveryLevels = Math.max(1, everyLevels);
      this.amplifierMax = Math.max(-1, maxAmplifier);
      return this;
   }

   public SkillBuff delay(int seconds) {
      this.delaySeconds = Math.max(1, seconds);
      return this;
   }

   public SkillBuff causes(Set<String> values) {
      this.causes = values == null || values.isEmpty()
         ? Collections.emptySet()
         : Collections.unmodifiableSet(new LinkedHashSet<>(values));
      return this;
   }

   /** Salinan bebas (dipakai agar buff dari config tidak ikut diubah saat normalisasi). */
   public SkillBuff copy() {
      SkillBuff copy = new SkillBuff(this.kind);
      copy.unlockLevel = this.unlockLevel;
      copy.perLevel = this.perLevel;
      copy.max = this.max;
      copy.power = this.power;
      copy.potionKey = this.potionKey;
      copy.amplifierEveryLevels = this.amplifierEveryLevels;
      copy.amplifierMax = this.amplifierMax;
      copy.delaySeconds = this.delaySeconds;
      copy.causes = this.causes;
      return copy;
   }

   // ------------------------------------------------------------------ //
   //  Perhitungan
   // ------------------------------------------------------------------ //

   /** Apakah buff sudah terbuka pada level ini. */
   public boolean unlocked(int level) {
      return level >= this.unlockLevel;
   }

   /** Nilai buff (persen / jumlah) pada level tertentu; 0 bila belum terbuka. */
   public double value(int level) {
      if (level < this.unlockLevel) {
         return 0.0D;
      }

      double raw = this.perLevel * (double)(level - this.unlockLevel + 1);
      double capped = this.max > 0.0D ? Math.min(this.max, raw) : raw;
      return capped > 0.0D ? capped : 0.0D;
   }

   /** Level ketika buff ini mencapai plafonnya; {@code -1} bila tidak pernah (tanpa plafon). */
   public int capLevel() {
      if (this.max <= 0.0D || this.perLevel <= 0.0D) {
         return -1;
      }

      return this.unlockLevel + (int)Math.ceil(this.max / this.perLevel) - 1;
   }

   /**
    * Tingkat (amplifier) efek potion pada level tertentu; -1 bila belum terbuka atau buff ini bukan
    * buff potion. Tingkatnya naik tepat tiap {@code amplifierEveryLevels} level di atas
    * {@code unlockLevel} (Lv. unlock = tingkat I, Lv. unlock+N = tingkat II, dan seterusnya).
    */
   public int amplifier(int level) {
      if (level < this.unlockLevel || this.amplifierMax < 0) {
         return -1;
      }

      return Math.min(this.amplifierMax, (level - this.unlockLevel) / this.amplifierEveryLevels);
   }

   /** Level berikutnya ketika tingkat potion naik; -1 bila sudah tingkat tertinggi. */
   public int nextAmplifierLevel(int level) {
      if (this.amplifierMax < 0) {
         return -1;
      }

      if (level < this.unlockLevel) {
         return this.unlockLevel;
      }

      int current = this.amplifier(level);
      if (current >= this.amplifierMax) {
         return -1;
      }

      return this.unlockLevel + (current + 1) * this.amplifierEveryLevels;
   }

   /**
    * Apakah buff lingkungan ini berlaku untuk penyebab kerusakan bernama {@code name}
    * (format {@code EntityDamageEvent.DamageCause.name()}, huruf besar/kecil diabaikan).
    * Daftar penyebab kosong berarti "semua penyebab".
    */
   public boolean tracksCause(String name) {
      if (name == null) {
         return false;
      }

      String cleaned = name.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
      return this.causes.isEmpty() || this.causes.contains(cleaned);
   }

   private static double sanitize(double value) {
      return Double.isNaN(value) || Double.isInfinite(value) || value < 0.0D ? 0.0D : value;
   }

   // ------------------------------------------------------------------ //
   //  Akses
   // ------------------------------------------------------------------ //

   public BuffKind kind() {
      return this.kind;
   }

   public int unlockLevel() {
      return this.unlockLevel;
   }

   public double perLevel() {
      return this.perLevel;
   }

   public double max() {
      return this.max;
   }

   public double power() {
      return this.power;
   }

   public String potionKey() {
      return this.potionKey;
   }

   public int amplifierEveryLevels() {
      return this.amplifierEveryLevels;
   }

   public int amplifierMax() {
      return this.amplifierMax;
   }

   public int delaySeconds() {
      return this.delaySeconds;
   }

   public Set<String> causes() {
      return this.causes;
   }

   /** Nama jenis buff seperti yang ditulis di config (untuk pesan / diagnostik). */
   public String kindName() {
      return this.kind == null ? "unknown" : this.kind.name();
   }

   @Override
   public String toString() {
      return this.kindName() + "{unlock=" + this.unlockLevel + ", per=" + this.perLevel + ", max=" + this.max
         + (this.power > 0.0D ? ", power=" + this.power : "")
         + (this.potionKey.isEmpty() ? "" : ", potion=" + this.potionKey) + "}";
   }
}
