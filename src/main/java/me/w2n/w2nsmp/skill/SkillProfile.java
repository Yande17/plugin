package me.w2n.w2nsmp.skill;

import java.util.UUID;

/**
 * Data skill satu pemain.
 *
 * <p>XP disimpan sebagai {@code double[]} yang diindeks dengan {@code SkillType.ordinal()}:
 * tidak ada boxing, tidak ada hash lookup, dan penambahan XP dari event yang sangat sering
 * (block break, damage, move) tetap murah. Level tidak disimpan - selalu dihitung dari XP
 * lewat {@link SkillCurve}, jadi data tidak pernah bisa tidak konsisten dengan config.
 *
 * <p>Objek ini hanya disentuh dari thread utama server (event + task sinkron), kecuali saat
 * disalin untuk disimpan ke disk. Karena itu tidak perlu sinkronisasi internal.
 */
public final class SkillProfile {
   private final UUID uniqueId;
   private final double[] xp = new double[SkillType.count()];
   private String name;
   private long updated;

   public SkillProfile(UUID uniqueId) {
      this.uniqueId = uniqueId;
   }

   public UUID uniqueId() {
      return this.uniqueId;
   }

   public String name() {
      return this.name;
   }

   public void name(String value) {
      if (value != null && !value.equals(this.name)) {
         this.name = value;
         this.updated = System.currentTimeMillis();
      }
   }

   public long updated() {
      return this.updated;
   }

   public void updated(long value) {
      this.updated = value;
   }

   public double xp(SkillType type) {
      return type == null ? 0.0D : this.xp[type.ordinal()];
   }

   /** Tambahkan XP; mengembalikan total XP skill itu setelah penambahan. */
   public double addXp(SkillType type, double amount) {
      if (type == null || !(amount > 0.0D)) {
         return this.xp(type);
      }

      int index = type.ordinal();
      double total = this.xp[index] + amount;
      this.xp[index] = total;
      this.updated = System.currentTimeMillis();
      return total;
   }

   public void xp(SkillType type, double total) {
      if (type != null) {
         this.xp[type.ordinal()] = Math.max(0.0D, total);
         this.updated = System.currentTimeMillis();
      }
   }

   public double totalXp() {
      double total = 0.0D;

      for (double value : this.xp) {
         total += value;
      }

      return total;
   }

   public int totalLevel(SkillCurve curve) {
      int total = 0;

      for (SkillType type : SkillType.values()) {
         total += curve.levelFor(this.xp(type));
      }

      return total;
   }

   public boolean isEmpty() {
      for (double value : this.xp) {
         if (value > 0.0D) {
            return false;
         }
      }

      return true;
   }

   /** Salinan untuk disimpan/dibaca lintas thread. */
   public SkillProfile copy() {
      SkillProfile copy = new SkillProfile(this.uniqueId);
      System.arraycopy(this.xp, 0, copy.xp, 0, this.xp.length);
      copy.name = this.name;
      copy.updated = this.updated;
      return copy;
   }
}
