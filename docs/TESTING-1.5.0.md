# Laporan Pengujian W2NSMP 1.5.0

Tanggal: 2026-09-16. Target: Paper 26.2 (Java 25). Metodologi: fase disiplin PHASE 0-9 -
tiap fase diaudit, diimplementasi, dibangun, diuji, diregresi sebelum fase berikutnya.

## 1. Hasil 6 gerbang rilis (tools/release.sh 1.5.0)

| Gerbang | Isi | Hasil |
|---|---|---|
| 1 | Stub API dari bytecode JAR asli + kompilasi ECJ (169 sumber -> 201 class) | LULUS (0 error) |
| 2a | SkillSelfTest (kurva XP, level, format) | LULUS |
| 2b | ListenerAnnotationTest (semua @EventHandler terlihat runtime) | LULUS |
| 2c | BuffDefaultsTest (buff tidak OP; reduksi damage <= 25%/45%) | LULUS |
| 2d | SkillTopTest (peringkat + kunci slot GUI) | LULUS |
| 2e | SkillPathTest (matematika snake path + rarity/lingkungan ikan) | LULUS |
| 2f | SettingsPageTest (BARU - isi slot per halaman /setting, anti ghost item) | LULUS |
| 2g | FishGalleryTest (BARU - paging galeri /fish) | LULUS |
| 3 | verify_linkage (65 keluarga class tersentuh, panggilan lama<->baru utuh) | AMAN (0 masalah) |
| 4 | verify_feature (143 kunci pesan, 26 jalur config, wiring 30 titik, ASCII) | LULUS (0 masalah) |
| 5 | package_jar (136 class asli byte-identik dipertahankan) | LULUS |
| 6 | verify_jar (230 entri, manifest, handler mancing/GUI ada di bytecode) | LULUS |

## 2. Pengujian per fase

### PHASE 1 - /setting
- Logika slot per halaman diekstrak ke `SettingsMenu.pageContent` (murni, tanpa Bukkit)
  dan diuji 12 kasus di SettingsPageTest: halaman utama TANPA slot toggle/back,
  sub-halaman TANPA slot kartu kategori, kategori kosong aman, keyCount negatif/999 aman.
- Render kini mengosongkan SEMUA slot non-konten sebelum mengisi - ghost item mustahil.
- Klik: `keyAt` selalu membaca kategori holder saat klik, jadi aksi basi tidak mungkin;
  filter klik (shift/kanan/keyboard/double/drop/drag) tidak berubah dari 1.4.0.

### PHASE 2 - /skill langsung ke snake path
- `SkillGuiListener.handleMenuClick`: klik skill -> `SkillPathMenu.open(type, -1)`
  (halaman tempat level pemain berada). Halaman info perantara dilepas dari alur.
- Back dari snake path -> menu skill utama (bukan lagi menu info).
- `openProgressLater` (kode mati) dihapus. Ikon path menampilkan level/XP/progres/
  buff aktif/buff berikut/max (placeholder dari `SkillInfo.placeholders`, sudah teruji).
- Regresi: SkillPathTest & SkillTopTest tetap lulus; `SkillProgressMenu` masih dikompilasi
  (dipakai selftest buffIndex) tetapi tidak lagi dibuka dari alur GUI.

### PHASE 3 - /fish hub + galeri
- FishGalleryTest menguji paging murni: pages/clampPage/indexAt/slotIndexOf 20 kasus
  (0 ikan, 7, 28, 29, 60; halaman negatif; slot border).
- Anti-exploit klik: semua klik di menu dibatalkan; hanya klik kiri bersih pemilik;
  drag dibatalkan; holder OPEN dibersihkan saat close/quit; transisi hub<->galeri lewat
  closeInventory + open baru (ukuran beda), tidak ada slot basi.
- Discovery: `FishDiscovery.discover` idempoten (set), tulis sinkron saat penemuan baru
  (kejadian langka), berkas rusak -> galeri mulai kosong dengan warning, id usang
  dipertahankan tetapi tidak dihitung.

### PHASE 4 - rod upgrade & attachment
- Audit ulang jalur dupe RodGuiListener 1.4.0: pasang = kurangi item DULU baru tulis
  PDC; copot = hapus PDC DULU baru beri item (penuh -> jatuh di kaki); upgrade =
  validasi semua syarat sebelum konsumsi; semua di main thread satu event. TIDAK ADA
  perubahan diperlukan - tidak ada jalur kehilangan/duplikasi item.
- Efek baru `luck` dan `treasure` ditambahkan ke FishingService (rollBonusItem(luck),
  rollTreasure(chance)) - keduanya dibatasi 0-100%, daftar harta dari config dengan
  validasi material + warning, harta inventory penuh -> jatuh di kaki (tidak hilang).

### PHASE 5 - custom fishing penuh
- Identitas ikan tetap PDC (fish-id, fish-value) - bukan rename vanilla; nilai jual
  PDC menang atas harga material di SellManager (terverifikasi wiring gate 4).
- Gerbang rarity bawaan diselaraskan 10/20/30/40/50; tetap configurable per rarity.
- Ikan hasil autofishing memakai `createFish` yang sama -> lore == PDC, dapat dijual,
  bertahan restart (PDC), masuk galeri.

### PHASE 6 - /autofishing
- Scheduler: SATU `runTaskTimer` 20 tick untuk semua sesi; dibuat saat sesi pertama,
  dibatalkan otomatis saat sesi terakhir berhenti dan di `shutdown()` (onDisable).
- Interval per tangkapan dibatasi keras minimal 3 detik (config lebih kecil dinaikkan).
- Skenario berhenti otomatis (kode + listener MONITOR): quit (PlayerQuitEvent),
  mati (PlayerDeathEvent), pindah dunia (PlayerChangedWorldEvent), rod tidak dipegang,
  menjauh dari air, syarat attachment/item/level gugur, fitur di-disable admin
  (reload memanggil stopAll dengan notifikasi), plugin disable (shutdown).
- Inventory penuh: `stop` -> pesan + berhenti, item TIDAK dibuang; `sell` -> jual
  tangkapan itu saja bila auto-sell diizinkan; harga 0/economy gagal -> fallback simpan,
  bila tetap penuh -> berhenti dengan pesan. Tidak ada jalur pembuangan diam-diam.
- Konsol: command menolak non-pemain dengan pesan `command.player-only` (uji manual
  jalur kode). Permission `w2nsmp.autofish` dicek sebelum aksi apa pun.

### PHASE 7 - regresi global
- Seluruh 8 selftest lulus pada bytecode final; verify_linkage menjamin class lama yang
  memanggil class tersentuh tetap terhubung (0 masalah pada 65 keluarga).
- 136 class yang TIDAK tersentuh dipertahankan byte-per-byte dari JAR 1.4.0 asli -
  regresi fitur legacy (top/tpa/sell/ah/home/scoreboard/economy/bounty/rtp/profile)
  secara bytecode mustahil berasal dari rilis ini.
- Command map: 2 label baru (fish, autofishing) + 4 alias; tidak bentrok dengan 21
  command lama (diperiksa daftar registerRuntime).

### PHASE 8 - audit kode akhir
- grep menyeluruh: 0 System.out / printStackTrace / TODO / FIXME di src/main/java.
- Semua 12 pemakai runTaskTimer memiliki cancel() di shutdown masing-masing.
- Tidak ada akses Bukkit API dari thread async (semua task = runTask/runTaskTimer sinkron).
- Holder GUI baru mengikuti pola beginProcessing/pending; OPEN map dibersihkan di
  close/quit/closeAll (dipanggil dari onDisable).

## 3. Bug ditemukan & diperbaiki selama pengembangan 1.5.0

1. Stub Material BLUE_STAINED_GLASS_PANE/GRAY_DYE belum ada -> ditambah FORCED_MATERIALS.
2. `ItemStack(Material,int)` tidak ada di stub -> diganti setAmount (juga lebih aman).
3. PlayerChangedWorldEvent + PlayerInventory.getContents/getStorageContents/firstEmpty +
   ItemStack.isSimilar/getMaxStackSize + Player.isDead belum ada di stub -> ditambah
   (semua API Bukkit lama & stabil).
4. Gate 4 menandai wiring lama "skill -> menu progres" yang memang sengaja diganti -
   aturan verifikasi diperbarui ke alur baru (SkillPathMenu.open).
5. Kartu syarat GUI autofishing sempat menebak status item lewat requirementBlocking
   (salah bila ada syarat lain yang gugur duluan) -> ditambah `hasRequiredItem` khusus.

## 4. Belum diuji di server hidup

Sandbox tidak menjalankan server Paper sungguhan, jadi yang berikut diuji lewat
selftest bytecode + audit jalur kode, belum klik-nyata: interaksi multi-pemain
serentak di GUI, perilaku klik versi klien tertentu, dan timing autofishing di server
dengan TPS rendah. Semua jalur memiliki guard try/catch + fallback yang tidak melempar.
