import me.w2n.w2nsmp.gui.FishMenu;

/**
 * Uji logika murni PHASE 3 (v1.4.1): paging galeri ikan /fish.
 */
public final class FishGalleryTest {
   private static int failures = 0;

   public static void main(String[] args) {
      check("perPage 28", FishMenu.perPage() == 28);
      check("pages(0) = 1", FishMenu.pages(0) == 1);
      check("pages(7) = 1", FishMenu.pages(7) == 1);
      check("pages(28) = 1", FishMenu.pages(28) == 1);
      check("pages(29) = 2", FishMenu.pages(29) == 2);
      check("pages(-4) = 1", FishMenu.pages(-4) == 1);
      check("clampPage(-1) = 0", FishMenu.clampPage(-1, 7) == 0);
      check("clampPage(5, 7 ikan) = 0", FishMenu.clampPage(5, 7) == 0);
      check("clampPage(5, 60 ikan) = 2", FishMenu.clampPage(5, 60) == 2);

      // indexAt: halaman 0 slot 0 = ikan 0; halaman 1 slot 0 = ikan 28; di luar = -1.
      check("indexAt(0,0,7) = 0", FishMenu.indexAt(0, 0, 7) == 0);
      check("indexAt(0,6,7) = 6", FishMenu.indexAt(0, 6, 7) == 6);
      check("indexAt(0,7,7) = -1", FishMenu.indexAt(0, 7, 7) == -1);
      check("indexAt(1,0,29) = 28", FishMenu.indexAt(1, 0, 29) == 28);
      check("indexAt(1,1,29) = -1", FishMenu.indexAt(1, 1, 29) == -1);
      check("indexAt(-1,0,7) = -1", FishMenu.indexAt(-1, 0, 7) == -1);
      check("indexAt(0,28,7) = -1", FishMenu.indexAt(0, 28, 7) == -1);

      // slotIndexOf: slot konten pertama 10 -> 0; slot border 9 -> -1; 43 -> 27.
      check("slotIndexOf(10) = 0", FishMenu.slotIndexOf(10) == 0);
      check("slotIndexOf(43) = 27", FishMenu.slotIndexOf(43) == 27);
      check("slotIndexOf(9) = -1", FishMenu.slotIndexOf(9) == -1);
      check("slotIndexOf(45) = -1", FishMenu.slotIndexOf(45) == -1);
      check("slotIndexOf(-1) = -1", FishMenu.slotIndexOf(-1) == -1);

      if (failures > 0) {
         System.out.println("GAGAL: " + failures + " pemeriksaan");
         System.exit(1);
      }

      System.out.println("FishGalleryTest: semua pemeriksaan lulus");
   }

   private static void check(String name, boolean ok) {
      if (!ok) {
         failures++;
         System.out.println("  GAGAL: " + name);
      }
   }
}
