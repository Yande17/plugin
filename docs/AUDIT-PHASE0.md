# PHASE 0 - Laporan Audit Menyeluruh (basis: v1.4.0 @ 526ec42)

Tanggal: 2026-09-16. Target: Paper 26.2. Sumber: 159 file Java, 189 class hasil kompilasi.

## 1. Arsitektur yang Ada

- Entry point `W2NSMP.java`: inisialisasi service (economy, stats, skills, gear, fishing,
  auction, bounty, home, tpa, scoreboard, nametag, worth, sell, rtp, teleport, combat),
  registrasi command runtime + plugin.yml, registrasi listener terpusat.
- Pola GUI: satu class `XxxMenu` (render statis + keyAt slot->aksi) + `XxxMenuHolder`
  (InventoryHolder, pemilik + guard beginProcessing/endProcessing/pending) + listener
  `XxxGuiListener` (cancel semua klik, filter klik kiri bersih, drag dibatalkan).
  Helper bersama: `utility/GuiKit` (shell/filler/border/nav, NAV_BACK).
- Penyimpanan: data pemain YAML periodik (bukan per-tick), identitas item via PDC
  (`gear/GearKeys`, `fishing/FishingKeys`) - tidak pernah lewat display name.
- Konfigurasi: `config.yml` (ekonomi, skill 11 jenis + buff, gear, fishing, rod),
  `messages.yml` (Bahasa Indonesia), `gui/*.yml` per menu (slot, material, filler, sound).
- Build: toolchain sandbox `tools/build.sh` (stub Bukkit via gen_stubs.py + ECJ),
  rilis `tools/release.sh` 6 gate (build, 5 selftest, verify_linkage, verify_feature,
  package_jar, verify_jar byte-identik).

## 2. Fitur yang Ada (semua wajib tetap hidup)

- Ekonomi: /balance /pay /worth /sell (+GUI), auction house /ah (+GUI), bounty.
- Dunia: /home /sethome /delhome (+GUI), /tpa /tpahere /tpaccept /tpdeny (+GUI), /rtp.
- Info: /top (+leaderboard GUI), /profile, /scoreboard, nametag uang, stats blok.
- Skill: 11 skill, XP kurva configurable, buff bertingkat, /skill GUI -> progres ->
  jalur snake path (SkillPathMenu, 28 node/halaman), /skill top.
- Gear: histori item via PDC (first owner, kill count, dsb) - GearListener.
- Fishing v1.4.0: registry ikan custom (6 rarity, 7 ikan contoh), gerbang level rarity,
  rod ber-level + XP + attachment (PDC), /rod GUI, /w2nsmp fishing give.
- /setting GUI berkategori (5 kategori, slot kartu 20-24).

## 3. Class Relevan untuk Task Ini

| Area | Class |
|---|---|
| /setting | gui/SettingsMenu, SettingsMenuHolder, listener/SettingsGuiListener, gui/settings.yml |
| /skill flow | gui/SkillMenu, SkillProgressMenu, SkillPathMenu (+Holder), listener/SkillGuiListener, skill/SkillPath, SkillService, skill.yml |
| fishing | fishing/CustomFish, FishRarity, FishingItem, FishingKeys, FishingService; listener/FishingListener, SkillFishingListener |
| rod | gui/RodMenu(+Holder), listener/RodGuiListener, command/RodCommand |
| util | utility/GuiKit, ItemTags, config/GuiConfig |

## 4. Masalah yang Ditemukan

1. **BUG /setting (PHASE 1)**: `SettingsMenu.render()` merender ulang di inventory yang
   sama saat pindah Main <-> Kategori, tetapi `GuiKit.shell()` hanya menimpa slot
   non-konten BILA filler aktif, dan predicate konten lama tidak membedakan halaman:
   `slot >= 9 && slot <= 44` dianggap konten sehingga TIDAK PERNAH ditimpa filler.
   Akibat: kartu kategori halaman utama tetap terlihat di sub-halaman (ghost item).
   Klik ghost item aman secara aksi (keyAt sadar kategori) tapi tampilan salah.
2. **Flow /skill (PHASE 2)**: klik skill membuka SkillProgressMenu (halaman info
   perantara), baru tombol "path" membuka SkillPathMenu. Permintaan: klik skill ->
   langsung halaman progresi (snake path). Info item di SkillPathMenu perlu diperkaya
   (level, XP sekarang/butuh, %progres, total XP, buff aktif, buff berikut, max level).
3. **Belum ada** /fish hub + Fish Gallery + status discovered/undiscovered (PHASE 3);
   belum ada penyimpanan "ikan yang pernah ditangkap" per pemain.
4. **Belum ada** /autofishing (PHASE 6): perlu scheduler terpusat (satu task, bukan
   per-pemain-per-tick), syarat configurable, anti-abuse lengkap.
5. Rod upgrade (PHASE 4) sudah ada dasar (level, XP, biaya, attachment PDC) - perlu
   verifikasi anti-dupe pada install/remove attachment + perilaku close/disconnect.
6. Tidak ditemukan: duplikasi manager, listener rusak, konflik command, akses Bukkit
   async, task tak dibatalkan. `git log` lokal sempat tereset sandbox; sudah
   direkonsiliasi ke 526ec42 tanpa mengubah working tree.

## 5. Class yang Perlu Dimodifikasi

- `gui/SettingsMenu` - clear eksplisit slot non-konten per halaman (PHASE 1). [SELESAI]
- `listener/SkillGuiListener`, `gui/SkillMenu`, `gui/SkillPathMenu` - flow langsung ke
  progresi; info item diperkaya; back dari path -> menu skill utama (PHASE 2).
- `gui/SkillProgressMenu(+Holder)` - tidak lagi jadi perantara; dihapus dari flow.
- `fishing/FishingService`, `listener/FishingListener` - pencatatan discovery per
  pemain (PHASE 3/5), integrasi autofishing (PHASE 6).
- `plugin.yml`, `config.yml`, `messages.yml` - command/perm/pesan baru.

## 6. Class Baru yang Dibutuhkan

- `command/FishCommand` + `gui/FishHubMenu(+Holder)` + `gui/FishGalleryMenu(+Holder)`
  + `listener/FishGuiListener` (PHASE 3).
- `fishing/FishDiscovery` (penyimpanan YAML ikan yang pernah ditangkap per UUID).
- `command/AutoFishCommand` + `gui/AutoFishMenu(+Holder)` + `fishing/AutoFishService`
  (satu scheduler global, interval configurable) (PHASE 6).
- `gui/fish.yml`, `gui/autofish.yml` (konfigurasi GUI baru).
