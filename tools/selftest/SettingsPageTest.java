import java.util.Set;
import me.w2n.w2nsmp.gui.SettingsMenu;

/**
 * Uji logika murni PHASE 1 (v1.4.1): isi slot per halaman /setting.
 * Memastikan sub-halaman kategori TIDAK memuat slot kartu kategori halaman utama
 * (akar bug ghost item v1.4.0) dan sebaliknya.
 */
public final class SettingsPageTest {
   private static int failures = 0;

   public static void main(String[] args) {
      int[] cards = {20, 21, 22, 23, 24};
      int back = 45;
      int info = 49;
      int close = 53;

      // --- Halaman utama: kartu + info + close, TANPA back, TANPA slot toggle ---
      Set<Integer> home = SettingsMenu.pageContent("", 0, cards, back, info, close);
      check("home berisi kartu 20-24", home.contains(20) && home.contains(24));
      check("home berisi info+close", home.contains(info) && home.contains(close));
      check("home TANPA back", !home.contains(back));
      check("home TANPA slot toggle 10", !home.contains(10));
      check("home ukuran = 7", home.size() == 7);

      // --- Sub-halaman scoreboard (4 toggle): toggle di 10-13 + back + info + close ---
      Set<Integer> sub = SettingsMenu.pageContent("scoreboard", 4, cards, back, info, close);
      check("sub berisi toggle 10-13", sub.contains(10) && sub.contains(11) && sub.contains(12) && sub.contains(13));
      check("sub berisi back", sub.contains(back));
      check("sub TANPA kartu kategori 20", !sub.contains(20));
      check("sub TANPA kartu kategori 21-24", !sub.contains(21) && !sub.contains(22) && !sub.contains(23) && !sub.contains(24));
      check("sub ukuran = 7", sub.size() == 7);

      // --- Kategori kosong tetap punya back/info/close, tidak crash ---
      Set<Integer> empty = SettingsMenu.pageContent("misc", 0, cards, back, info, close);
      check("kategori kosong = back+info+close", empty.size() == 3 && empty.contains(back));

      // --- keyCount negatif / berlebihan aman ---
      check("keyCount -5 aman", SettingsMenu.pageContent("x", -5, cards, back, info, close).size() == 3);
      Set<Integer> full = SettingsMenu.pageContent("x", 999, cards, back, info, close);
      check("keyCount 999 dibatasi 28 konten", full.size() == 28 + 3);

      // --- null category = halaman utama ---
      check("null category = home", SettingsMenu.pageContent(null, 0, cards, back, info, close).equals(home));

      if (failures > 0) {
         System.out.println("GAGAL: " + failures + " pemeriksaan");
         System.exit(1);
      }

      System.out.println("SettingsPageTest: semua pemeriksaan lulus");
   }

   private static void check(String name, boolean ok) {
      if (!ok) {
         failures++;
         System.out.println("  GAGAL: " + name);
      }
   }
}
