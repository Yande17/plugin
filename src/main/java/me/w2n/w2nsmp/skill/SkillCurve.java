package me.w2n.w2nsmp.skill;

/**
 * Kurva level skill.
 *
 * <p>XP yang dibutuhkan untuk naik dari level {@code L} ke {@code L+1}:
 * <pre>required(L) = base-xp * L ^ exponent</pre>
 * Tabel kumulatifnya dihitung sekali saat config dimuat, jadi menentukan level dari jumlah XP
 * hanya penelusuran array kecil (maks {@code max-level} entri) - aman dipanggil ribuan kali
 * per detik dari event permainan.
 *
 * <p>Level awal pemain adalah 1. XP disimpan sebagai total seumur hidup, jadi mengubah
 * {@code base-xp}/{@code exponent} di config langsung mengubah level semua pemain (tanpa
 * migrasi data) - level tidak pernah disimpan terpisah agar tidak bisa tidak sinkron.
 */
public final class SkillCurve {
   private final int maxLevel;
   private final double baseXp;
   private final double exponent;
   private final double[] cumulative;

   public SkillCurve(int maxLevel, double baseXp, double exponent) {
      this.maxLevel = Math.max(1, maxLevel);
      this.baseXp = Math.max(1.0D, baseXp);
      this.exponent = Math.max(1.0D, exponent);
      this.cumulative = new double[this.maxLevel + 2];

      for (int level = 1; level <= this.maxLevel; level++) {
         this.cumulative[level + 1] = this.cumulative[level] + this.required(level);
      }
   }

   public int maxLevel() {
      return this.maxLevel;
   }

   public double baseXp() {
      return this.baseXp;
   }

   public double exponent() {
      return this.exponent;
   }

   /** XP yang dibutuhkan untuk naik dari {@code level} ke level berikutnya. */
   public double required(int level) {
      if (level < 1) {
         return this.baseXp;
      }

      return level >= this.maxLevel ? 0.0D : this.baseXp * Math.pow(level, this.exponent);
   }

   /** Total XP yang dibutuhkan untuk mencapai {@code level} (level 1 = 0 XP). */
   public double xpToReach(int level) {
      int index = Math.max(1, Math.min(level, this.maxLevel + 1));
      return this.cumulative[index];
   }

   /** Total XP untuk mencapai level maksimal. */
   public double totalXpAtMaxLevel() {
      return this.cumulative[this.maxLevel];
   }

   /** Level untuk sejumlah XP total; selalu di antara 1 dan {@link #maxLevel()}. */
   public int levelFor(double totalXp) {
      if (!(totalXp > 0.0D)) {
         return 1;
      }

      int level = 1;

      while (level < this.maxLevel && totalXp >= this.cumulative[level + 1]) {
         level++;
      }

      return level;
   }

   /** XP yang sudah dikumpulkan pemain di dalam levelnya sekarang. */
   public double xpIntoLevel(double totalXp) {
      int level = this.levelFor(totalXp);
      return Math.max(0.0D, totalXp - this.cumulative[level]);
   }

   /** Sisa XP menuju level berikutnya (0 bila sudah level maksimal). */
   public double xpToNextLevel(double totalXp) {
      int level = this.levelFor(totalXp);
      if (level >= this.maxLevel) {
         return 0.0D;
      }

      return Math.max(0.0D, this.cumulative[level + 1] - totalXp);
   }

   public boolean isMaxLevel(double totalXp) {
      return this.levelFor(totalXp) >= this.maxLevel;
   }

   /** Kemajuan di level sekarang, 0.0 - 1.0 (1.0 bila sudah level maksimal). */
   public double progress(double totalXp) {
      int level = this.levelFor(totalXp);
      if (level >= this.maxLevel) {
         return 1.0D;
      }

      double need = this.cumulative[level + 1] - this.cumulative[level];
      return need <= 0.0D ? 1.0D : Math.max(0.0D, Math.min(1.0D, (totalXp - this.cumulative[level]) / need));
   }

   /** Bar kemajuan siap tampil, mis. {@code ||||||---- } untuk lebar 10. */
   public String bar(double totalXp, int width, String filled, String empty) {
      int size = Math.max(1, width);
      int done = (int) Math.round(this.progress(totalXp) * size);
      StringBuilder builder = new StringBuilder(size * 2);

      for (int index = 0; index < size; index++) {
         builder.append(index < done ? filled : empty);
      }

      return builder.toString();
   }
}
