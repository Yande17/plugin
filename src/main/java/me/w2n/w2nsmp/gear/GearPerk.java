package me.w2n.w2nsmp.gear;

import java.util.Locale;

/**
 * Satu perk gear yang terbuka pada milestone tertentu (v1.4.0).
 *
 * <p>Semua nilai berasal dari config ({@code gear.classes.<kelas>.milestones}) - tidak ada
 * progresi yang di-hardcode. Jenis perk yang dikenal:
 *
 * <ul>
 *   <li>{@code bleeding} - korban menerima damage berkala setelah dipukul (senjata).</li>
 *   <li>{@code lifesteal} - penyerang pulih sekian persen dari damage yang diberikan (senjata).</li>
 *   <li>{@code bonus-damage} - damage pukulan dinaikkan sekian persen (senjata).</li>
 *   <li>{@code resistance} - damage yang diterima dikurangi sekian persen (armor).</li>
 * </ul>
 */
public final class GearPerk {
   private final String kind;
   private final int threshold;
   private final double value;
   private final int ticks;
   private final int intervalSeconds;
   private final int cooldownSeconds;

   public GearPerk(String kind, int threshold, double value, int ticks, int intervalSeconds, int cooldownSeconds) {
      this.kind = kind == null ? "" : kind.toLowerCase(Locale.ROOT);
      this.threshold = Math.max(1, threshold);
      this.value = Math.max(0.0D, value);
      this.ticks = Math.max(1, ticks);
      this.intervalSeconds = Math.max(1, intervalSeconds);
      this.cooldownSeconds = Math.max(0, cooldownSeconds);
   }

   public String kind() {
      return this.kind;
   }

   /** Nilai counter (kill/pemakaian) yang membuka perk ini. */
   public int threshold() {
      return this.threshold;
   }

   /** bleeding: damage per detak; lifesteal/bonus-damage/resistance: persen. */
   public double value() {
      return this.value;
   }

   /** bleeding: jumlah detak damage. */
   public int ticks() {
      return this.ticks;
   }

   /** bleeding: jeda antar detak (detik). */
   public int intervalSeconds() {
      return this.intervalSeconds;
   }

   /** Jeda minimal antar pemicuan per pemain (detik; 0 = tiap pukulan). */
   public int cooldownSeconds() {
      return this.cooldownSeconds;
   }
}
