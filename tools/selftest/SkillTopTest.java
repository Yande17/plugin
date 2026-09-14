import me.w2n.w2nsmp.gui.SkillProgressMenu;
import me.w2n.w2nsmp.skill.SkillTop;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Uji logika fitur peringkat ({@code /skill top}) dan pembacaan kunci slot menu progres.
 *
 * <p>Bagian yang diuji di sini memang tidak menyentuh server: {@code SkillTop.page} hanya
 * mengurutkan + memotong daftar, dan {@code SkillProgressMenu.buffIndex} hanya mengurai teks
 * kunci. Keduanya dipanggil lewat refleksi karena privat, memakai class hasil kompilasi yang
 * sama dengan yang dikemas ke JAR (jadi yang diuji benar-benar kode rilis).
 *
 * <p>Dijalankan oleh {@code tools/release.sh} langkah 2d dengan classpath stub + kelas plugin;
 * stub Bukkit ada di classpath hanya agar kelasnya bisa dimuat, bukan karena dipakai.
 */
public class SkillTopTest {
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
   static List<SkillTop.Entry> daftar(Object hasil) {
      return (List<SkillTop.Entry>) hasil;
   }

   public static void main(String[] args) throws Exception {
      Constructor<SkillTop.Entry> baris = SkillTop.Entry.class.getDeclaredConstructor(
         UUID.class, String.class, int.class, double.class, int.class, double.class);
      baris.setAccessible(true);
      Method page = SkillTop.class.getDeclaredMethod("page", List.class, Comparator.class, int.class, int.class);
      page.setAccessible(true);

      // urutan yang dipakai SkillTop.total()/skill(): level menurun, seri dipecah XP terbesar
      Comparator<SkillTop.Entry> urutan = Comparator.comparingInt(SkillTop.Entry::level).reversed()
         .thenComparing(Comparator.comparingDouble(SkillTop.Entry::xp).reversed());

      List<SkillTop.Entry> acak = new ArrayList<>();
      acak.add(baris.newInstance(UUID.randomUUID(), "Budi", Integer.valueOf(12), Double.valueOf(5000.0D),
         Integer.valueOf(12), Double.valueOf(5000.0D)));
      acak.add(baris.newInstance(UUID.randomUUID(), "Sari", Integer.valueOf(30), Double.valueOf(90000.0D),
         Integer.valueOf(30), Double.valueOf(90000.0D)));
      acak.add(baris.newInstance(UUID.randomUUID(), "Ani", Integer.valueOf(30), Double.valueOf(120000.0D),
         Integer.valueOf(30), Double.valueOf(120000.0D)));
      acak.add(baris.newInstance(UUID.randomUUID(), null, Integer.valueOf(1), Double.valueOf(10.0D),
         Integer.valueOf(1), Double.valueOf(10.0D)));
      acak.add(baris.newInstance(UUID.randomUUID(), "  ", Integer.valueOf(7), Double.valueOf(700.0D),
         Integer.valueOf(7), Double.valueOf(700.0D)));

      List<SkillTop.Entry> terurut = daftar(page.invoke(null, new ArrayList<>(acak), urutan,
         Integer.valueOf(10), Integer.valueOf(0)));
      cek("semua baris ikut terurut", terurut.size() == acak.size());
      cek("level tertinggi di urutan pertama", terurut.get(0).level() == 30 && terurut.get(1).level() == 30);
      cek("seri level dipecah XP terbesar dulu", terurut.get(0).name().equals("Ani")
         && terurut.get(1).name().equals("Sari"));
      cek("urutan menurun sampai baris terakhir", terurut.get(2).name().equals("Budi")
         && terurut.get(3).level() == 7 && terurut.get(4).level() == 1);
      cek("nama null/blank ditampilkan 'pemain'", terurut.get(3).name().equals("pemain")
         && terurut.get(4).name().equals("pemain"));
      cek("daftar asli tidak diubah urutannya", acak.get(0).name().equals("Budi"));

      cek("MAX_LIMIT = 50", SkillTop.MAX_LIMIT == 50);
      cek("limit besar dipatok MAX_LIMIT", daftar(page.invoke(null, new ArrayList<>(acak), urutan,
         Integer.valueOf(999), Integer.valueOf(0))).size() == Math.min(acak.size(), SkillTop.MAX_LIMIT));
      cek("limit 2 memotong tepat 2 baris teratas", daftar(page.invoke(null, new ArrayList<>(acak), urutan,
         Integer.valueOf(2), Integer.valueOf(0))).size() == 2);
      cek("limit 0/negatif tetap menampilkan satu baris", daftar(page.invoke(null, new ArrayList<>(acak), urutan,
         Integer.valueOf(0), Integer.valueOf(0))).size() == 1
         && daftar(page.invoke(null, new ArrayList<>(acak), urutan, Integer.valueOf(-3), Integer.valueOf(0))).size() == 1);
      cek("offset 2 melewati dua baris teratas", daftar(page.invoke(null, new ArrayList<>(acak), urutan,
         Integer.valueOf(10), Integer.valueOf(2))).get(0).name().equals("Budi"));
      cek("offset melebihi isi daftar -> kosong", daftar(page.invoke(null, new ArrayList<>(acak), urutan,
         Integer.valueOf(10), Integer.valueOf(99))).isEmpty());
      cek("offset negatif dianggap 0", daftar(page.invoke(null, new ArrayList<>(acak), urutan,
         Integer.valueOf(2), Integer.valueOf(-5))).size() == 2);
      cek("daftar kosong aman", daftar(page.invoke(null, new ArrayList<SkillTop.Entry>(), urutan,
         Integer.valueOf(10), Integer.valueOf(0))).isEmpty());

      // kunci slot menu progres: dipakai SkillGuiListener untuk tahu apa yang diklik pemain
      cek("buffIndex membaca buff:<indeks>", SkillProgressMenu.buffIndex("buff:0") == 0
         && SkillProgressMenu.buffIndex("buff:2") == 2 && SkillProgressMenu.buffIndex("buff:11") == 11);
      cek("buffIndex menolak kunci lain", SkillProgressMenu.buffIndex("back") == -1
         && SkillProgressMenu.buffIndex("detail") == -1 && SkillProgressMenu.buffIndex("close") == -1
         && SkillProgressMenu.buffIndex("") == -1);
      cek("buffIndex aman untuk null/teks rusak", SkillProgressMenu.buffIndex(null) == -1
         && SkillProgressMenu.buffIndex("buff:") == -1 && SkillProgressMenu.buffIndex("buff:abc") == -1
         && SkillProgressMenu.buffIndex("buff:-1") == -1);

      System.out.println(gagal == 0 ? "\nHASIL: semua uji peringkat & kunci slot lulus"
         : "\nHASIL: " + gagal + " uji GAGAL");
      if (gagal > 0) {
         System.exit(1);
      }
   }
}
