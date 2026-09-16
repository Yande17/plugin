import java.util.List;
import me.w2n.w2nsmp.fishing.CustomFish;
import me.w2n.w2nsmp.fishing.FishRarity;
import me.w2n.w2nsmp.skill.SkillPath;

/**
 * Uji logika murni v1.4.0 (tanpa server):
 *  - matematika jalur progres (snake path): paging, slot, level, status node
 *  - rarity ikan custom: urutan & penguraian kunci
 *  - syarat lingkungan ikan custom (biome/cuaca/waktu)
 * Dipakai class hasil kompilasi yang sama dengan yang dikemas ke JAR.
 */
public final class SkillPathTest {
   private static int failures = 0;

   public static void main(String[] args) {
      // --- SkillPath: paging untuk max-level 50 (bawaan) ---
      check("perPage 28", SkillPath.perPage() == 28);
      check("pages(50) = 2", SkillPath.pages(50) == 2);
      check("pages(28) = 1", SkillPath.pages(28) == 1);
      check("pages(29) = 2", SkillPath.pages(29) == 2);
      check("pages(1) = 1", SkillPath.pages(1) == 1);
      check("clampPage(-3) = 0", SkillPath.clampPage(-3, 50) == 0);
      check("clampPage(9) = 1", SkillPath.clampPage(9, 50) == 1);
      check("pageOf(1) = 0", SkillPath.pageOf(1, 50) == 0);
      check("pageOf(28) = 0", SkillPath.pageOf(28, 50) == 0);
      check("pageOf(29) = 1", SkillPath.pageOf(29, 50) == 1);
      check("pageOf(50) = 1", SkillPath.pageOf(50, 50) == 1);

      // --- level <-> slot bolak-balik konsisten di kedua halaman ---
      boolean roundtrip = true;
      for (int page = 0; page < SkillPath.pages(50); page++) {
         for (int index = 0; index < SkillPath.perPage(); index++) {
            int level = SkillPath.levelAt(page, index, 50);
            if (level == 0) {
               continue;
            }

            int slot = SkillPath.slotOf(index);
            if (slot < 0 || SkillPath.levelAtSlot(page, slot, 50) != level
               || SkillPath.pageOf(level, 50) != page) {
               roundtrip = false;
            }
         }
      }
      check("level <-> slot bolak-balik konsisten", roundtrip);
      check("levelAt melewati max = 0", SkillPath.levelAt(1, 27, 50) == 0);
      check("levelAtSlot slot bukan node = 0", SkillPath.levelAtSlot(0, 0, 50) == 0);

      // --- status node ---
      check("status current", SkillPath.status(7, 7, false) == SkillPath.Node.CURRENT);
      check("status unlocked", SkillPath.status(3, 7, false) == SkillPath.Node.UNLOCKED);
      check("status locked", SkillPath.status(9, 7, false) == SkillPath.Node.LOCKED);
      check("status milestone unlocked", SkillPath.status(5, 7, true) == SkillPath.Node.MILESTONE_UNLOCKED);
      check("status milestone locked", SkillPath.status(20, 7, true) == SkillPath.Node.MILESTONE_LOCKED);

      // --- FishRarity ---
      check("rarity fromKey", FishRarity.fromKey("LeGeNdArY") == FishRarity.LEGENDARY);
      check("rarity fromKey null", FishRarity.fromKey("tidak-ada") == null);
      check("urutan rarity", FishRarity.COMMON.ordinal() < FishRarity.MYTHIC.ordinal());

      // --- CustomFish: syarat lingkungan ---
      CustomFish koi = new CustomFish("golden_koi", "Golden Koi", FishRarity.LEGENDARY, null,
         List.of(), 1.5D, 60.0D, 150.0D, 600L, 60.0D, List.of("RIVER"), "any", "day", 30, 5);
      check("biome cocok (RIVER)", koi.matchesEnvironment("minecraft:river", false, 1000L));
      check("biome tidak cocok (DESERT)", !koi.matchesEnvironment("DESERT", false, 1000L));
      check("waktu siang cocok", koi.matchesEnvironment("RIVER", false, 6000L));
      check("waktu malam ditolak", !koi.matchesEnvironment("RIVER", false, 18000L));

      CustomFish badai = new CustomFish("salmon_badai", "Salmon Badai", FishRarity.UNCOMMON, null,
         List.of(), 18.0D, 20.0D, 60.0D, 35L, 8.0D, List.of(), "rain", "any", 0, 0);
      check("cuaca hujan wajib", !badai.matchesEnvironment("RIVER", false, 1000L));
      check("cuaca hujan cocok", badai.matchesEnvironment("RIVER", true, 1000L));
      check("biome kosong = semua", badai.matchesEnvironment(null, true, 1000L));

      CustomFish malam = new CustomFish("pari_bulan", "Pari Bulan", FishRarity.EPIC, null,
         List.of(), 4.0D, 40.0D, 120.0D, 220L, 28.0D, List.of(), "any", "night", 20, 0);
      check("waktu malam cocok", malam.matchesEnvironment("OCEAN", false, 18000L));
      check("waktu siang ditolak (night)", !malam.matchesEnvironment("OCEAN", false, 3000L));

      if (failures > 0) {
         System.out.println("GAGAL: " + failures + " pemeriksaan jalur/fishing tidak lulus");
         System.exit(1);
      }

      System.out.println("HASIL: semua pemeriksaan jalur progres & fishing lulus");
   }

   private static void check(String label, boolean ok) {
      System.out.println((ok ? "  ok  " : "  GAGAL ") + label);
      if (!ok) {
         failures++;
      }
   }
}
