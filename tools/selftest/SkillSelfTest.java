import me.w2n.w2nsmp.skill.BuffKind;
import me.w2n.w2nsmp.skill.SkillBuff;
import me.w2n.w2nsmp.skill.SkillCurve;
import me.w2n.w2nsmp.skill.SkillProfile;
import me.w2n.w2nsmp.skill.SkillType;
import java.util.Set;
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
      // ---------------- v1.3.0: multi-buff (SkillBuff) & jenis buff baru ----------------
      SkillBuff melee = SkillBuff.of(BuffKind.MELEE_DAMAGE, 1, 0.30D, 15.0D);
      SkillBuff loot = SkillBuff.of(BuffKind.MOB_LOOT, 25, 0.30D, 9.0D);
      cek("buff milestone belum terbuka = 0", loot.value(24) == 0.0D && !loot.unlocked(24));
      cek("buff mulai bekerja di unlock-level", loot.unlocked(25) && loot.value(25) == 0.30D);
      cek("nilai tumbuh linear per level", melee.value(10) == 3.0D && melee.value(11) == 3.3D);
      cek("plafon (max) dihormati", melee.value(50) == 15.0D && melee.value(5000) == 15.0D);
      cek("capLevel = level saat plafon tercapai", melee.capLevel() == 50);
      cek("tanpa plafon -> capLevel -1", SkillBuff.of(BuffKind.MELEE_DAMAGE, 1, 0.5D, 0.0D).capLevel() == -1);

      SkillBuff haste = SkillBuff.potion(BuffKind.HASTE, "haste", 5, 12, 2);
      cek("amplifier sebelum unlock = -1", haste.amplifier(4) == -1);
      cek("tingkat I tepat di unlock", haste.amplifier(5) == 0);
      cek("tingkat naik tiap N level", haste.amplifier(17) == 1 && haste.amplifier(29) == 2);
      cek("tingkat dibatasi amplifier-max", haste.amplifier(50) == 2);
      cek("nextAmplifierLevel benar", haste.nextAmplifierLevel(5) == 17 && haste.nextAmplifierLevel(29) == -1);
      cek("potion satu tingkat (amp-max 0) tetap aktif", SkillBuff.potion(BuffKind.POTION, "slow_falling", 25, 25, 0).amplifier(30) == 0);

      SkillBuff env = SkillBuff.of(BuffKind.ENVIRONMENT_REDUCTION, 1, 0.40D, 20.0D).causes(Set.of("FALL", "FIRE"));
      cek("causes tahan huruf besar/kecil", env.tracksCause("fall") && env.tracksCause("FIRE"));
      cek("causes menolak penyebab lain", !env.tracksCause("lava") && !env.tracksCause(null));
      cek("causes kosong = semua penyebab", SkillBuff.of(BuffKind.ENVIRONMENT_REDUCTION, 1, 0.4D, 20.0D).tracksCause("LAVA"));

      SkillBuff salinan = haste.copy().power(10.0D);
      cek("copy() tidak mengubah buff asli", haste.power() == 0.0D && salinan.power() == 10.0D);
      cek("angka negatif dibersihkan", SkillBuff.of(BuffKind.MELEE_DAMAGE, 1, -5.0D, 10.0D).value(50) == 0.0D);
      cek("NaN/Infinity dibersihkan", SkillBuff.of(BuffKind.MELEE_DAMAGE, 1, Double.NaN, 10.0D).perLevel() == 0.0D
         && SkillBuff.of(BuffKind.MELEE_DAMAGE, 1, Double.POSITIVE_INFINITY, 10.0D).perLevel() == 0.0D);

      boolean kindOk = true;
      for (BuffKind kind : BuffKind.values()) {
         if (BuffKind.fromKey(kind.name()) != kind) kindOk = false;
         if (BuffKind.fromKey(kind.key()) != kind) kindOk = false;
         if (BuffKind.fromKey(" " + kind.key().toUpperCase().replace('-', '_') + " ") != kind) kindOk = false;
      }
      cek("BuffKind.fromKey tahan format config", kindOk);
      cek("BuffKind.fromKey aman untuk null/kosong/salah ketik",
         BuffKind.fromKey(null) == null && BuffKind.fromKey("   ") == null && BuffKind.fromKey("lifesteal") == null);
      cek("jumlah jenis buff = 16", BuffKind.values().length == 16 && BuffKind.keys().size() == 16);
      cek("pengelompokan potion benar", BuffKind.HASTE.isPotion() && BuffKind.POTION.isPotion() && !BuffKind.CRIT_CHANCE.isPotion());
      cek("pengelompokan peluang benar", BuffKind.CRIT_CHANCE.isChance() && BuffKind.MOB_LOOT.isChance()
         && BuffKind.DOUBLE_XP.isChance() && !BuffKind.MELEE_DAMAGE.isChance());

      // Keseimbangan: buff bawaan harus tetap terasa "awal game" di level maksimum.
      SkillBuff[] seimbang = {
         SkillBuff.of(BuffKind.MELEE_DAMAGE, 1, 0.30D, 15.0D),
         SkillBuff.of(BuffKind.PROJECTILE_DAMAGE, 1, 0.35D, 18.0D),
         SkillBuff.of(BuffKind.CRIT_CHANCE, 10, 0.25D, 12.5D).power(50.0D),
         SkillBuff.of(BuffKind.DAMAGE_REDUCTION, 1, 0.28D, 14.0D),
         SkillBuff.of(BuffKind.BLOCK_CHANCE, 15, 0.20D, 10.0D).power(50.0D),
         SkillBuff.of(BuffKind.ENVIRONMENT_REDUCTION, 1, 0.40D, 20.0D),
         SkillBuff.of(BuffKind.WALK_SPEED, 1, 0.25D, 12.5D),
         SkillBuff.of(BuffKind.EXTRA_DROP, 1, 0.30D, 15.0D),
         SkillBuff.of(BuffKind.EXTRA_CATCH, 1, 0.35D, 18.0D),
         SkillBuff.of(BuffKind.MOB_LOOT, 25, 0.30D, 9.0D),
         SkillBuff.of(BuffKind.VANILLA_XP, 15, 0.40D, 20.0D),
         SkillBuff.of(BuffKind.DOUBLE_XP, 30, 0.25D, 8.0D),
         SkillBuff.of(BuffKind.REGEN_BOOST, 3, 0.40D, 20.0D),
         SkillBuff.of(BuffKind.PASSIVE_HEAL, 3, 0.03D, 1.5D).delay(10),
      };
      boolean wajar = true;
      for (SkillBuff buff : seimbang) {
         if (buff.value(50) > 20.0D || buff.perLevel() > 0.5D) wajar = false;
      }
      cek("semua buff bawaan <= 20% di Lv.50 (progresi awal game)", wajar);

      System.out.println("\n  Kekuatan buff bawaan pada Lv. 50 (level maks = progresi awal game):");
      for (SkillBuff buff : seimbang) {
         System.out.printf("    %-24s = %5.1f%%   (buka Lv %2d, +%.2f%%/level, plafon %.1f%%)%n",
            buff.kind().key(), buff.value(50), buff.unlockLevel(), buff.perLevel(), buff.max());
      }

      System.out.println(gagal == 0 ? "\nHASIL: semua uji lulus" : "\nHASIL: " + gagal + " uji GAGAL");
      if (gagal > 0) System.exit(1);
   }
}
