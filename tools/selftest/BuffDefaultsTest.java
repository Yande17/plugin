import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import me.w2n.w2nsmp.skill.BuffKind;
import me.w2n.w2nsmp.skill.SkillBuff;
import me.w2n.w2nsmp.skill.SkillSettings;
import me.w2n.w2nsmp.skill.SkillType;

/**
 * Uji runtime nilai buff BAWAAN yang benar-benar dikompilasi ke JAR.
 *
 * <p>Kenapa perlu (dan kenapa bukan cuma uji angka di SkillSelfTest): SkillSelfTest menguji
 * kelas SkillBuff dengan angka contoh, sedangkan uji ini membaca angka bawaan dari
 * SkillSettings.defaultBuffs() lewat refleksi - jadi salah ketik di switch bawaan (jenis buff
 * tertukar, angka kelewat besar, buff milestone hilang) langsung ketahuan sebelum JAR dikemas.
 *
 * <p>Dijalankan dengan classpath stub (tools/work/stubs-classes) karena SkillSettings menyebut
 * tipe Bukkit di tanda tangannya; tidak ada server yang dijalankan.
 */
public class BuffDefaultsTest {
   static int gagal = 0;

   static void cek(String nama, boolean syarat) {
      if (!syarat) {
         gagal++;
         System.out.println("  GAGAL: " + nama);
      } else {
         System.out.println("  lulus: " + nama);
      }
   }

   @SuppressWarnings("unchecked")
   static List<SkillBuff> buffsOf(SkillType type) throws Exception {
      Method method = SkillSettings.class.getDeclaredMethod("defaultBuffs", SkillType.class);
      method.setAccessible(true);
      Object result = method.invoke(null, type);
      return result == null ? new ArrayList<SkillBuff>() : (List<SkillBuff>) result;
   }

   public static void main(String[] args) throws Exception {
      System.out.println("BuffDefaultsTest - buff bawaan hasil kompilasi (bukan angka contoh):");
      int total = 0;
      boolean tigaBuff = true;
      boolean utamaCocok = true;
      boolean milestone = true;
      boolean monoton = true;
      boolean wajar = true;
      boolean potionSah = true;
      List<String> jenisTerpakai = new ArrayList<>();

      for (SkillType type : SkillType.values()) {
         List<SkillBuff> buffs = buffsOf(type);
         total += buffs.size();
         if (buffs.size() < 2) {
            tigaBuff = false;
         }

         if (buffs.isEmpty() || buffs.get(0).kind() != type.buff()) {
            utamaCocok = false;
         }

         // buff pertama harus aktif sejak awal, dan harus ada buff yang baru terbuka belakangan
         boolean adaAwal = false;
         boolean adaMilestone = false;

         for (SkillBuff buff : buffs) {
            if (buff.unlockLevel() <= 5) {
               adaAwal = true;
            }

            if (buff.unlockLevel() >= 10) {
               adaMilestone = true;
            }

            if (buff.kind() == BuffKind.POTION || buff.kind() == BuffKind.HASTE) {
               String key = buff.potionKey();
               if (key == null || key.trim().isEmpty() || buff.amplifierMax() < 0) {
                  potionSah = false;
               }
            } else if (buff.perLevel() <= 0.0D && buff.max() > 0.0D) {
               potionSah = false;
            }

            if (!jenisTerpakai.contains(buff.kind().key())) {
               jenisTerpakai.add(buff.kind().key());
            }

            double sebelumnya = 0.0D;

            for (int level = 1; level <= 200; level++) {
               double nilai = buff.value(level);
               if (nilai < sebelumnya - 1.0E-9) {
                  monoton = false;
               }

               sebelumnya = nilai;
               if (nilai > 20.0D || buff.perLevel() > 0.5D) {
                  wajar = false;
               }
            }
         }

         if (!adaAwal || !adaMilestone) {
            milestone = false;
         }
      }

      cek("setiap skill punya lebih dari satu buff", tigaBuff);
      cek("buff pertama tiap skill = jenis utama skill itu", utamaCocok);
      cek("ada buff awal (<= Lv.5) dan buff milestone (>= Lv.10) di tiap skill", milestone);
      cek("nilai buff tidak pernah turun saat level naik", monoton);
      cek("tidak ada buff bawaan > 20% atau > 0,5%/level (progresi awal game)", wajar);
      cek("buff potion punya kunci efek & tingkat yang sah", potionSah);
      cek("total buff bawaan = 33 (11 skill x 3)", total == 33);
      cek("16 jenis buff semuanya terpakai", jenisTerpakai.size() == 16);

      // Pengurangan damage total (defense + endurance + agility) tidak boleh membuat pemain kebal.
      double reduksiMaks = 0.0D;
      double lingkunganMaks = 0.0D;

      for (SkillType type : SkillType.values()) {
         for (SkillBuff buff : buffsOf(type)) {
            if (buff.kind() == BuffKind.DAMAGE_REDUCTION) {
               reduksiMaks += buff.value(50);
            }

            if (buff.kind() == BuffKind.ENVIRONMENT_REDUCTION) {
               lingkunganMaks += buff.value(50);
            }
         }
      }

      cek("total pengurangan damage di Lv.50 <= 25% (nyata: " + fmt(reduksiMaks) + "%)", reduksiMaks <= 25.0D);
      cek("total pengurangan damage lingkungan di Lv.50 <= 45% (nyata: " + fmt(lingkunganMaks) + "%)",
         lingkunganMaks <= 45.0D);

      System.out.println("\n  Ringkasan buff tiap skill pada beberapa level:");

      for (SkillType type : SkillType.values()) {
         List<SkillBuff> buffs = buffsOf(type);
         StringBuilder baris = new StringBuilder();
         baris.append(String.format("    %-13s", type.key()));

         for (SkillBuff buff : buffs) {
            baris.append(String.format(" | %-22s Lv%-2d %s", buff.kind().key(), buff.unlockLevel(),
               buff.kind().isPotion()
                  ? "tingkat maks " + (buff.amplifierMax() + 1)
                  : fmt(buff.value(50)) + "% di Lv.50"));
         }

         System.out.println(baris);
      }

      System.out.println(gagal == 0 ? "\nHASIL: semua uji buff bawaan lulus" : "\nHASIL: " + gagal + " uji GAGAL");
      if (gagal > 0) {
         System.exit(1);
      }
   }

   static String fmt(double value) {
      long rounded = Math.round(value * 10.0D);
      return rounded % 10L == 0L ? Long.toString(rounded / 10L) : String.format(java.util.Locale.ROOT, "%.1f", value);
   }
}
