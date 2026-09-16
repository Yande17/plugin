# Membangun W2NSMP

## Isi repo

```
W2NSMP-1.0.0.jar        JAR rilis asli (dipakai sebagai sumber class yang tidak diubah)
W2NSMP-1.1.0.jar        rilis Paket A+B+C (perbaikan statistik, /top, scoreboard)
W2NSMP-1.2.1.jar        rilis fitur /skill + perbaikan fatal listener
W2NSMP-1.3.0.jar        multi-buff, menu progres skill, keseimbangan ulang, top skill  <-- PAKAI INI
# (W2NSMP-1.2.0.jar ditarik/dihapus: listener skill tuli - lihat "Jebakan stub: @Retention anotasi")
src/main/java/          source Java (dipulihkan dari JAR via Vineflower 1.12) + kode baru
src/main/resources/     plugin.yml, config.yml, messages.yml, prices.yml, gui/*.yml
docs/                   catatan audit, desain, dan dokumentasi fitur
tools/                  toolchain build sandbox (stub API, ECJ, verifikasi, pengemasan)
pom.xml                 build Maven (untuk komputer lokal / CI)
```

> Source di `src/main/java` awalnya dipulihkan otomatis dari bytecode, jadi **komentar asli
> tidak ikut terpulihkan**. Struktur kelas, nama metode, dan logika utuh. Semua perubahan
> fitur dilakukan di `src/`, lalu JAR dibangun ulang lewat `tools/release.sh`.

## Riwayat rilis

| Versi   | Isi                                                                                     |
|---------|------------------------------------------------------------------------------------------|
| `1.0.0` | Build asli (sumber kebenaran bytecode untuk kelas yang tidak diedit)                     |
| `1.1.0` | Paket A+B+C: `docs/AUDIT-statistik-scoreboard.md` — perbaikan nametag×sidebar, migrasi format statistik, `/top` & `/sb` |
| `1.2.0` | Fitur `/skill`: 11 skill, kurva level (maks 50), buff per skill, GUI, admin — `docs/FITUR-skill.md`. **Ditarik:** listener tuli (lihat jebakan di bawah) |
| `1.2.1` | Perbaikan fatal v1.2.0 (`@Retention(RUNTIME)`), watchdog pendaftaran listener, `/skill check`, kegagalan tidak lagi diam-diam |
| `1.3.0` | Skill: **beberapa buff per skill** (3 buff × 11 skill, terbuka bertahap), **menu progres skill** di GUI (klik ikon skill), **keseimbangan buff ulang** (level 50 = progresi awal game), **`/skill top`** (total & per skill), 6 jenis buff baru (critical, block, loot mob, XP vanilla, XP ganda, potion apa pun) |

Aturan main yang dipegang untuk setiap rilis: **fitur lama tidak diubah perilakunya**. Data
fitur baru disimpan di berkas terpisah (`skills.yml`), dan class yang tidak disunting tetap
dipaketkan apa adanya dari `W2NSMP-1.0.0.jar`.

## Target build

- Paper/Minecraft **26.2** (`api-version: '26.2'` di `plugin.yml`)
- Bytecode **Java 25** (class file major version 69)
- Dependensi compile: `paper-api`, Vault (opsional/`provided`). Tidak ada dependensi runtime
  yang di-shade — semua soft-dependency (Vault, EssentialsX, LuckPerms, Geyser, floodgate)
  dibaca lewat refleksi/registry server.
- Fitur skill sengaja **tidak mengandaikan API tertentu ada**: setiap permukaan API dipakai
  melalui probe saat startup (`skill/SkillApiProbe.java`). Bila satu API tidak tersedia di
  versi server, hanya buff/XP terkait yang dimatikan dan alasannya ditulis ke log.

## Build di komputer lokal (disarankan)

Butuh JDK 25 + Maven, dan akses ke `repo.papermc.io` serta Maven Central.

```bash
mvn -q clean package
# hasil: target/W2NSMP-<versi>.jar  -> salin ke plugins/ server
```

Bila versi `paper-api` di `pom.xml` tidak cocok dengan yang tersedia di repo Paper,
sesuaikan properti `<paper.version>`.

## Build di sandbox Arena (tanpa akses Maven)

Sandbox hanya mengizinkan PyPI/npm/GitHub, sehingga `paper-api` **tidak bisa diunduh**.
Build sandbox memakai:

- **JRE 25** dari paket PyPI `jdk4py`
- **ECJ 3.46** (Eclipse Compiler for Java) dari wheel PyPI `karellen-jdtls`
- **stub API** (`tools/stubs/`) yang dibangkitkan dari referensi constant-pool JAR asli:
  87 tipe eksternal (Bukkit/Paper/Adventure/Vault) dengan 298 method + 81 field yang
  benar-benar dipakai plugin, menjadi 84 berkas stub. Deskriptor method stub identik dengan
  API asli sehingga bytecode hasil kompilasi memanggil simbol yang sama di server sungguhan.
  Stub **hanya** untuk kompilasi dan tidak pernah ikut dikemas ke JAR plugin.

Satu perintah untuk merilis (semua gerbang harus lulus, kalau tidak JAR dihapus):

```bash
bash tools/release.sh 1.3.0
```

Langkahnya:

| # | Langkah | Alat |
|---|---------|------|
| 1 | bangkitkan stub + kompilasi 140 sumber (→ 169 class, 0 error) | `tools/build.sh`, `tools/gen_stubs.py` |
| 2a | uji logika murni Java (kurva level, profil XP, `fromKey`, rumus buff) | `tools/selftest/SkillSelfTest.java` |
| 2b | uji refleksi ala Bukkit: setiap `@EventHandler` harus terlihat saat runtime | `tools/selftest/ListenerAnnotationTest.java` |
| 2c | uji buff bawaan **hasil kompilasi** (refleksi ke `SkillSettings.defaultBuffs`): 3 buff/skill, milestone bertahap, nilai monoton, tidak ada buff > 20% di Lv. 50, total reduksi tidak membuat kebal | `tools/selftest/BuffDefaultsTest.java` |
| 2d | uji peringkat (`/skill top`): urutan level/XP, pemecah seri, batas `limit`/`offset`, dan penguraian kunci slot menu progres | `tools/selftest/SkillTopTest.java` |
| 3 | verifikasi linkage bytecode (330 + 447 referensi internal) | `tools/verify_linkage.py` |
| 4 | verifikasi config/messages/plugin.yml/gui terhadap kode | `tools/verify_feature.py` (butuh `pyyaml`) |
| 5 | kemas JAR hibrida | `tools/package_jar.py` |
| 6 | verifikasi **isi** JAR byte-per-byte | `tools/verify_jar.py` |

### Jebakan stub: `@Retention` anotasi

Stub API dibangkitkan sendiri, jadi **sifat anotasi harus ditulis eksplisit**. `@EventHandler`
Bukkit dibaca lewat refleksi (`method.isAnnotationPresent(...)`); tanpa `@Retention(RUNTIME)`
Java memakai retention bawaan `CLASS`, anotasi tersimpan di class file tapi **tidak terlihat
saat runtime**. Kompilasi tetap sukses, plugin tetap aktif, tidak ada error di log — namun
Bukkit melihat nol handler, jadi listener "tuli": GUI tidak membatalkan klik (item bisa diambil),
XP tidak bertambah, buff tidak dipasang. Inilah bug fatal v1.2.0.

`tools/gen_stubs.py` kini selalu menulis `@Retention(RUNTIME)` + `@Target(METHOD)` untuk stub
anotasi, dan tiga gerbang menjaganya: `ListenerAnnotationTest` (refleksi ala Bukkit),
`verify_feature.py` (stub wajib memuat `RetentionPolicy.RUNTIME`), dan `verify_jar.py`
(setiap class di JAR yang merujuk `@EventHandler` wajib punya `RuntimeVisibleAnnotations`).

> Pelajaran umumnya: bila bytecode dikompilasi terhadap stub, yang berbahaya bukan hanya
> *nama* member, tapi juga **bentuk metadata** (retention anotasi, class vs interface,
> static vs instance). Karena itu rilis selalu diverifikasi di tingkat bytecode, bukan cuma
> "kompilasi sukses".

### Cara kerja pengemasan hibrida

`tools/package_jar.py` menyusun JAR rilis dari dua sumber:

- **class**: byte asli dari `W2NSMP-1.0.0.jar`, kecuali untuk *keluarga* class yang diedit
  (daftar `TOUCHED` di `tools/release.sh`). Satu keluarga = class luar + seluruh inner
  class/lambda-nya, mis. `SkillService` bersama `SkillService$RuntimeState`.
- **resource**: seluruh isi `src/main/resources/` (sumber kebenaran), termasuk berkas baru
  seperti `gui/skill.yml`.

> **Jebakan yang pernah terjadi:** menghitung keluarga dengan
> `name.split('$')[0][:-6]` salah untuk inner class — `"Foo$Bar.class".split('$')[0]`
> tidak berakhir dengan `.class`, jadi pemotongan 6 karakter menggerogoti nama class luar
> dan keluarga inner class tidak pernah cocok. Akibatnya inner class ikut terkemas dari
> byte lama walau class luarnya baru (`NoClassDefFoundError`/`NoSuchMethodError` saat
> runtime). Sekarang dipakai `family_of()` (potong `.class` dulu, baru `$`) dan
> `tools/verify_jar.py` membandingkan **setiap** byte class di JAR rilis dengan hasil
> kompilasi, sehingga kesalahan semacam itu tidak bisa lolos lagi.

### Memeriksa tanpa merilis

```bash
bash tools/build.sh                                       # kompilasi saja
python3 tools/verify_linkage.py W2NSMP-1.0.0.jar tools/work/classes <kelas...>
python3 tools/verify_feature.py                           # butuh: pip install pyyaml
python3 tools/verify_jar.py W2NSMP-1.3.0.jar W2NSMP-1.0.0.jar tools/work/classes <kelas...>
```

### Menambah/mengubah fitur

1. Sunting `src/main/java` (ikuti gaya kode yang ada: 3 spasi, komentar Indonesia).
2. Tambahkan kunci pesan ke `messages.yml`, opsi ke `config.yml`, dan — bila ada GUI baru —
   berkas `gui/<id>.yml` plus id-nya di `config/GuiConfigs.java`.
3. Daftarkan command lewat `manager/CommandManager.java` (`registerRuntime`) dan listener
   lewat `manager/ListenerManager.java`. **Jangan** mendeklarasikan command fitur di
   `plugin.yml` — lihat catatan "anti-timpa command" di berkas itu.
4. Tambahkan keluarga class yang disunting ke `TOUCHED` di `tools/release.sh`.
5. Jalankan `bash tools/release.sh <versi>` sampai semua gerbang hijau.

## Pemasangan di server

1. Hentikan server, ganti `plugins/W2NSMP-*.jar` dengan `W2NSMP-1.3.0.jar`.
2. Jalankan server. Berkas config yang belum ada akan dibuat otomatis; config lama tidak
   ditimpa (kunci baru memakai nilai bawaan bila tidak ada di berkas Anda).
3. Statistik versi lama dimigrasi otomatis ke `statistics.yml` format baru (v1.1.0).
4. Data skill disimpan di `plugins/W2NSMP/skills.yml` (terpisah; menghapusnya hanya mereset
   skill ke level 1).
5. Cek baris `Skill:` di log startup untuk melihat buff/XP mana yang aktif di server Anda.
6. Di dalam game: `/skill` → klik ikon skill untuk membuka **menu progres** (level, bar, kartu
   tiap buff beserta level pembukaannya, milestone, peringkat), dan `/skill top [skill]` untuk
   peringkat. `config.yml`/`messages.yml` lama disisipi kunci baru otomatis; `gui/skill.yml`
   lama tidak ditimpa (nilai bawaan menu progres ada di kode).
