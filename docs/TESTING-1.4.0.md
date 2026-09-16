# Laporan Pengujian W2NSMP 1.4.0

Lingkungan build: JRE 25 (jdk4py) + ECJ, tanpa server hidup - pengujian memakai
pipeline 6 gerbang `tools/release.sh` (kompilasi penuh, selftest runtime atas class
hasil kompilasi, verifikasi linkage bytecode, verifikasi konsistensi config/messages/gui
vs kode, pengemasan JAR, verifikasi isi JAR byte-per-byte).

## Hasil ringkas
| Gerbang | Hasil |
|---|---|
| 1. Kompilasi (86 stub + 159 sumber -> 189 class) | LULUS |
| 2a. SkillSelfTest (kurva XP, profil, kunci skill) | LULUS |
| 2b. ListenerAnnotationTest (handler terlihat runtime) | LULUS |
| 2c. BuffDefaultsTest (buff tidak OP, reduksi dijaga) | LULUS |
| 2d. SkillTopTest (peringkat + kunci slot menu progres) | LULUS |
| 2e. **SkillPathTest (baru)** - matematika snake path, rarity, syarat lingkungan ikan | LULUS (30 pemeriksaan) |
| 3. verify_linkage (55 keluarga class tersentuh vs JAR asli) | LULUS |
| 4. verify_feature (config/messages/plugin.yml/gui vs kode) | LULUS (0 masalah) |
| 5. Kemas JAR (136 class asli dipertahankan + 63 diganti/baru + resource) | LULUS |
| 6. Verifikasi isi `W2NSMP-1.4.0.jar` (216 entri) | LULUS |

## Pemeriksaan per item daftar uji
1. **Kompilasi tanpa error** - ya (gerbang 1, 0 error/warning ECJ `-nowarn` untuk stub).
2. **plugin.yml valid & versi 1.4.0** - ya (YAML tervalidasi; gerbang 6 memeriksa isi JAR).
3. **Semua listener terdaftar & handler terlihat refleksi** - gerbang 2b memeriksa 23
   class listener (termasuk GearListener, FishingListener, RodGuiListener baru).
4. **/skill: 11 skill, max-level 50, kurva configurable** - gerbang 4 + 6 memeriksa
   config & bytecode; alias `skills.xp.*` diuji lewat pembacaan `reloadSettings`.
5. **Jalur progres (snake path)** - SkillPathTest menguji: 28 node/halaman, 2 halaman
   untuk level 50, bolak-balik level<->slot konsisten di semua halaman, clamp halaman,
   status node (current/unlocked/locked/milestone), parser kunci `node:<level>`.
6. **Anti-abuse** - logika murni (jendela menit, kunci blok `world:x:y:z`, cooldown
   same-kill) ditinjau kode + dikompilasi; jalur event dibungkus try/catch sehingga
   kegagalan tidak mematikan XP normal.
7. **Gear PDC** - identitas & counter lewat `ItemTags` (semua akses try/catch);
   tulis-balik salinan item dipastikan di ketiga jalur (serang, bertahan, kill).
8. **Milestone gear dari config** - parser `gear.classes.*` diuji kompilasi; nilai
   bawaan diverifikasi YAML valid; `crossed()` hanya memicu saat ambang terlewati.
9. **Custom fishing** - CustomFish.matchesEnvironment diuji langsung (biome substring
   case-insensitive, hujan/cerah, siang/malam); gerbang rarity + bobot undian ditinjau.
10. **Ikan custom vs vanilla** - `fish-id` PDC; isCustomFish/fishValue diuji kompilasi;
    /sell membaca nilai PDC (fallback aman bila layanan mati).
11. **/rod GUI aman** - semua klik dibatalkan LOWEST (shift/hotbar/drag/double);
    item tidak pernah dititipkan ke GUI; pasang = konsumsi dulu lalu tulis rod;
    copot = tulis rod dulu lalu beri item (inventory penuh -> jatuh); upgrade
    memeriksa syarat lengkap sebelum konsumsi; menu ditutup saat quit/disable.
12. **/setting kategori** - navigasi kategori/back dirender ulang di inventory yang
    sama (tanpa reopen); toggle lama & `/setting toggle` chat tidak berubah.
13. **Persistensi restart** - tidak ada penyimpanan baru: XP skill tetap skills.yml
    (format lama), gear/ikan/rod di PDC item (persisten oleh server).
14. **Backward compat** - gerbang 6 memverifikasi 136 class lama dipertahankan
    byte-per-byte; resource lama yang tidak tersentuh identik dengan JAR 1.0.0.

## Kesalahan yang ditemukan & diperbaiki selama pengembangan
1. **Cap pemilik pertama hilang saat kill mob** - `handleDeath` menulis balik item
   hanya untuk kill pemain; diperbaiki: tulis-balik selalu dilakukan.
2. **Salinan item tidak ditulis balik** - `getItemInMainHand()`/`getArmorContents()`
   mengembalikan salinan di CraftBukkit; semua jalur gear kini menulis balik
   (`setItemInMainHand`/`setArmorContents`), stub setter ditambahkan.
3. **Lore ikan vs nilai PDC bisa beda** - bonus "value" awalnya diterapkan setelah
   item dibuat sehingga lore menampilkan angka lama; diperbaiki dengan menghitung
   bonus di `createFish(definition, bonus)`.
4. **Karakter non-ASCII di messages baru** (gate 5f) - simbol centang/silang diganti
   `+`/`-` agar aman di semua encoding console.
5. **Material stub hilang** (`KNOWLEDGE_BOOK`, `FILLED_MAP`, dst) - daftar
   FORCED_MATERIALS gen_stubs.py dilengkapi; satu pemakaian diganti `BOOK`.
6. **`PlayerFishEvent.State`/`FishHook` tidak ada di stub** - ditambahkan sebagai
   FORCED_TYPES + enum konstanta; pemanggilan runtime tetap dibungkus try/catch
   sehingga server tanpa API tsb hanya kehilangan fitur terkait.

## Yang belum diuji (butuh server hidup)
- Interaksi klien nyata (buka GUI, klik, suara) di Paper 26.2.
- Nilai `Biome` string di versi registry terbaru (kode memakai `String.valueOf` +
  pencocokan substring supaya toleran terhadap `minecraft:river` maupun `RIVER`).
- Perilaku `FishHook.setWaitTime` antar versi (dibungkus try/catch; gagal = hanya
  efek bite-speed yang tidak terasa, tanpa error).
