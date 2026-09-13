import me.w2n.w2nsmp.skill.SkillCurve;
import me.w2n.w2nsmp.skill.SkillProfile;
import me.w2n.w2nsmp.skill.SkillType;
import java.util.UUID;

public class SkillSelfTest {
   static int gagal = 0;
   static void cek(String nama, boolean syarat) {
      if (!syarat) { gagal++; System.out.println("  GAGAL: " + nama); } else { System.out.println("  lulus: " + nama); }
   }
   public static void main(String[] args) {
      SkillCurve curve = new SkillCurve(50, 25.0D, 1.25D);
      cek("level awal = 1", curve.levelFor(0.0D) == 1);
      cek("level 2 tepat di XP syarat", curve.levelFor(curve.xpToReach(2)) == 2);
      boolean monoton = true, tepat = true;
      for (int level = 1; level <= 50; level++) {
         if (curve.levelFor(curve.xpToReach(level)) != level) tepat = false;
         if (level < 50 && curve.xpToReach(level + 1) <= curve.xpToReach(level)) monoton = false;
      }
      cek("setiap level tercapai tepat di XP kumulatifnya", tepat);
      cek("XP kumulatif selalu naik", monoton);
      cek("XP besar tetap level 50", curve.levelFor(1.0E12D) == 50);
      cek("xpToNextLevel di level maks = 0", curve.xpToNextLevel(1.0E12D) == 0.0D);
      boolean progresOk = true;
      for (double xp = 0; xp < 200000; xp += 137.0D) {
         double p = curve.progress(xp);
         if (!(p >= 0.0D && p <= 1.0D)) progresOk = false;
      }
      cek("progress selalu 0..1", progresOk);
      cek("bar 10 karakter", curve.bar(1000.0D, 10, "|", ".").length() == 10);

      System.out.println("\n  Tabel kurva bawaan (base 25, exponent 1.25, maks 50):");
      int[] contoh = {1, 2, 5, 10, 20, 30, 40, 49};
      for (int level : contoh) {
         System.out.printf("    Lv %2d -> %2d : %,10.0f XP   (kumulatif %,10.0f)%n",
            level, level + 1, curve.required(level), curve.xpToReach(level + 1));
      }
      System.out.printf("    Total XP untuk level 50: %,10.0f%n", curve.totalXpAtMaxLevel());

      SkillProfile profile = new SkillProfile(UUID.randomUUID());
      cek("profil baru kosong", profile.isEmpty());
      profile.addXp(SkillType.MINING, 500.0D);
      cek("addXp menambah", profile.xp(SkillType.MINING) == 500.0D);
      cek("profil tidak lagi kosong", !profile.isEmpty());
      cek("level dari profil = " + curve.levelFor(500.0D), curve.levelFor(profile.xp(SkillType.MINING)) == curve.levelFor(500.0D));
      profile.addXp(SkillType.MINING, -100.0D);
      cek("XP negatif diabaikan", profile.xp(SkillType.MINING) == 500.0D);
      SkillProfile copy = profile.copy();
      copy.addXp(SkillType.FISHING, 10.0D);
      cek("copy tidak mengubah aslinya", profile.xp(SkillType.FISHING) == 0.0D && copy.xp(SkillType.FISHING) == 10.0D);
      cek("totalLevel = jumlah level", profile.totalLevel(curve) == 10 * 1 + curve.levelFor(500.0D));

      boolean keyOk = true;
      for (SkillType type : SkillType.values()) {
         if (SkillType.fromKey(type.key()) != type) keyOk = false;
         if (SkillType.fromKey(type.key().toUpperCase()) != type) keyOk = false;
         if (SkillType.fromKey(type.name()) != type) keyOk = false;
         if (SkillType.fromKey(" " + type.key() + " ") != type) keyOk = false;
      }
      cek("fromKey tahan huruf besar/spasi/nama enum", keyOk);
      cek("fromKey null aman", SkillType.fromKey(null) == null && SkillType.fromKey("   ") == null);
      cek("jumlah skill = 11", SkillType.values().length == 11 && SkillType.keys().size() == 11);
      System.out.println(gagal == 0 ? "\nHASIL: semua uji lulus" : "\nHASIL: " + gagal + " uji GAGAL");
      if (gagal > 0) System.exit(1);
   }
}
