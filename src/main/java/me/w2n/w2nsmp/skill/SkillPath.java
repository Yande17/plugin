package me.w2n.w2nsmp.skill;

/**
 * Tata letak "jalur ular" (snake path) menu progres skill (v1.4.0).
 *
 * <p>Setiap level menjadi satu node di dalam GUI. Node disusun berkelok seperti ular pada
 * empat baris tengah inventory 54 slot (kolom 1-7), 28 node per halaman:
 *
 * <pre>
 *   baris 1 :  10 → 11 → 12 → 13 → 14 → 15 → 16
 *                                             ↓
 *   baris 2 :  19 ← 20 ← 21 ← 22 ← 23 ← 24 ← 25
 *               ↓
 *   baris 3 :  28 → 29 → 30 → 31 → 32 → 33 → 34
 *                                             ↓
 *   baris 4 :  37 ← 38 ← 39 ← 40 ← 41 ← 42 ← 43
 * </pre>
 *
 * <p>Kelas ini murni aritmetika (tanpa Bukkit) supaya bisa diuji oleh
 * {@code tools/selftest/SkillPathTest.java} persis seperti logika kurva level.
 */
public final class SkillPath {
   /** Urutan slot node per halaman, sudah berkelok (lihat javadoc kelas). */
   private static final int[] SLOTS = {
      10, 11, 12, 13, 14, 15, 16,
      25, 24, 23, 22, 21, 20, 19,
      28, 29, 30, 31, 32, 33, 34,
      43, 42, 41, 40, 39, 38, 37
   };

   /** Status satu node level di jalur. */
   public enum Node {
      /** Level sudah dilewati pemain. */
      UNLOCKED,
      /** Level pemain sekarang. */
      CURRENT,
      /** Level belum dicapai. */
      LOCKED,
      /** Level milestone (ada buff terbuka di sini) yang sudah dicapai. */
      MILESTONE_UNLOCKED,
      /** Level milestone yang belum dicapai. */
      MILESTONE_LOCKED
   }

   private SkillPath() {
   }

   /** Jumlah node per halaman. */
   public static int perPage() {
      return SLOTS.length;
   }

   /** Jumlah halaman yang dibutuhkan untuk {@code maxLevel} level (minimal 1). */
   public static int pages(int maxLevel) {
      return Math.max(1, (Math.max(1, maxLevel) + SLOTS.length - 1) / SLOTS.length);
   }

   /** Jepit nomor halaman ke rentang yang sah (0 .. pages-1). */
   public static int clampPage(int page, int maxLevel) {
      return Math.max(0, Math.min(page, pages(maxLevel) - 1));
   }

   /** Halaman tempat sebuah level berada (0-based); level di luar rentang dijepit. */
   public static int pageOf(int level, int maxLevel) {
      int clamped = Math.max(1, Math.min(level, Math.max(1, maxLevel)));
      return (clamped - 1) / SLOTS.length;
   }

   /** Slot GUI untuk node ke-{@code index} (0-based) pada halamannya; -1 bila indeks tak sah. */
   public static int slotOf(int index) {
      return index >= 0 && index < SLOTS.length ? SLOTS[index] : -1;
   }

   /** Level pada halaman {@code page} indeks {@code index}; 0 bila melewati {@code maxLevel}. */
   public static int levelAt(int page, int index, int maxLevel) {
      if (page < 0 || index < 0 || index >= SLOTS.length) {
         return 0;
      }

      int level = page * SLOTS.length + index + 1;
      return level > Math.max(1, maxLevel) ? 0 : level;
   }

   /** Level yang menempati {@code slot} di halaman {@code page}; 0 bila slot bukan node. */
   public static int levelAtSlot(int page, int slot, int maxLevel) {
      for (int index = 0; index < SLOTS.length; index++) {
         if (SLOTS[index] == slot) {
            return levelAt(page, index, maxLevel);
         }
      }

      return 0;
   }

   /** Status node {@code level} untuk pemain berlevel {@code playerLevel}. */
   public static Node status(int level, int playerLevel, boolean milestone) {
      if (level == playerLevel) {
         return Node.CURRENT;
      }

      if (level < playerLevel) {
         return milestone ? Node.MILESTONE_UNLOCKED : Node.UNLOCKED;
      }

      return milestone ? Node.MILESTONE_LOCKED : Node.LOCKED;
   }
}
