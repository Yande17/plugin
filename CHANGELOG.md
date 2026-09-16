# Changelog W2NSMP

Semua perubahan penting dicatat di sini. Format mengikuti [Keep a Changelog](https://keepachangelog.com/).

## [1.4.0] - 2026-09-16

Update besar: rework skill, gear history, custom fishing + rod, /setting berkategori.
**Kompatibel penuh ke belakang** - tidak ada data (uang, skill, home, auction, setting,
statistik) yang hilang; semua fitur baru punya nilai bawaan dan aktif tanpa migrasi manual.

### Ditambahkan

#### Skill (/skill) - jalur progres & anti-abuse
- **Jalur progres (snake path)**: tombol baru di menu kemajuan skill membuka visual jalur
  Lv. 1 -> max sebagai node berkelok (28 node/halaman, otomatis berhalaman untuk level 29+).
  Status node: sudah dicapai (hijau), posisi sekarang (kuning), terkunci (abu), milestone
  buff tercapai (botol XP) / belum (ungu). Klik node milestone = detail buff di chat.
  Kelas baru: `SkillPath`, `SkillPathMenu`, `SkillPathMenuHolder`.
- **Anti-abuse XP** (`skills.anti-abuse.*`):
  - `max-xp-per-minute` - plafon XP per skill per menit per pemain (bawaan 600; 0 = mati).
  - `track-placed-blocks` - blok yang baru dipasang pemain **tidak** memberi XP saat
    dipecah (anti place-break farming; catatan kedaluwarsa otomatis,
    `placed-block-expiry-seconds`).
  - `same-kill-cooldown-seconds` - kill atas pemain yang sama hanya memberi XP sekali per
    jeda (bawaan 120 dtk; anti farming teman/alt).
- **Alias kurva XP**: `skills.xp.base` / `skills.xp.multiplier` (opsional) menang atas
  `skills.curve.base-xp` / `skills.curve.exponent` lama - config lama tetap jalan.

#### Gear history & progresi (paket baru `gear/`)
- Riwayat item disimpan **di item lewat PersistentDataContainer** (bukan display name):
  pemilik pertama (UUID + nama), jumlah kill pemain, jumlah pemakaian. Aman terhadap
  rename anvil, chest, perdagangan, dan restart.
- **Kelas gear dari config** (`gear.classes.*`): materials, counter milestone
  (kills/uses), slot (weapon/armor), syarat skill, milestone perk. Bawaan: swords
  (Bleeding I/II/III pada 10/50/100 kill), axes (lifesteal), bows (bonus damage),
  armor (resistance). Menambah kelas baru cukup lewat config.
- **Syarat First Owner / skill** (`require-skill`, `require-level`): syarat tidak
  terpenuhi = perk terkunci, **pemakaian dasar tetap jalan** (bisa diperketat lewat
  `gear.unmet-damage-percent`); `gear.first-owner-only-perks` membatasi perk hanya
  untuk pemilik pertama.
- **Lore dinamis**: blok riwayat ditulis di ekor lore tanpa menyentuh lore asli item,
  di-refresh berkala (`gear.lore-update-every`), jumlah baris dicatat di PDC sehingga
  selalu ditimpa bersih.
- Perk tidak OP: resistance armor dijumlahkan per potongan dengan **plafon 80%**,
  lifesteal dibatasi `gear.heal-cap`, semua nilai dan cooldown dari config.

#### Custom fishing (paket baru `fishing/`)
- **Registry terpusat** (`FishingService`): ikan custom, item pancing, level rod, dan
  attachment dimuat dari `fishing.*` di config - listener tidak berisi logika.
- **6 rarity**: Common, Uncommon, Rare, Epic, Legendary, Mythic; gerbang level skill
  Fishing per rarity (`fishing.rarity-level.*`, mis. Legendary butuh Fishing 30).
- **7 ikan contoh** (Sungai Perak ... Golden Koi Legendary, Leviathan Muda Mythic)
  dengan nama, rarity, lore, ukuran acak (cm), nilai jual, XP, bobot undian, syarat
  biome/cuaca/waktu/level fishing/level rod - semuanya config.
- Ikan custom diidentifikasi lewat **PDC** (`fish-id`, `fish-rarity`, `fish-size`,
  `fish-value`): tidak pernah tertukar dengan ikan vanilla, aman di chest/trade/restart.
- **Jual ikan custom**: /sell (GUI) otomatis menghargai ikan custom sesuai nilai di
  PDC-nya (menang atas harga material di prices.yml).
- **Bonus bahan upgrade**: menangkap ikan custom berpeluang memberi bahan
  (drop-chance-percent per item).

#### Rod & /rod GUI (command baru)
- **/rod** (alias `/pancingan`, permission `w2nsmp.rod`, `commands.rod.enabled`):
  menampilkan rod di tangan, level (1-10), XP rod, statistik efek aktif, slot
  attachment, dan biaya upgrade level berikutnya.
- **Upgrade rod Lv. 1-10** memakai item custom: Rod Upgrade Crystal, Fishing Core,
  Rare Scale, Golden Thread (jumlah + XP rod per level dari `fishing.rod.levels.*`).
  Efek per level bersifat kumulatif: bite-speed, custom-chance, rarity-boost, value,
  xp, double-catch.
- **7 attachment**: Lucky Hook, Golden Hook, Reinforced Line, Magic Float, Deep Sea
  Weight, Treasure Charm, XP Reel. Pasang = klik item di inventory sendiri saat menu
  terbuka; copot = klik kartunya di menu (item dikembalikan; inventory penuh -> jatuh
  di kaki). **Tidak ada item yang pernah dipindahkan ke GUI - bebas dupe/hilang.**
- Admin: `/w2nsmp fishing give <pemain> <id-item> [jumlah]`
  (permission `w2nsmp.fishing.admin`).

#### /setting berkategori
- GUI /setting kini berhalaman: halaman utama berisi **5 kartu kategori**
  (Scoreboard, Notifikasi, Gameplay, Visual, Lain-lain); klik kartu membuka
  sub-halaman berisi toggle kategori itu (termasuk toggle per-baris scoreboard).
  Tombol kembali di sub-halaman. Command `/setting toggle <kunci>` lama tetap jalan.

#### Utilitas
- `ItemTags` - pembungkus PDC aman (semua akses dibungkus try/catch).
- Selftest baru `tools/selftest/SkillPathTest.java` (gerbang rilis 2e): matematika
  snake path + rarity + syarat lingkungan ikan.

### Diubah
- `SkillService.addXp` menerapkan plafon XP per menit (jendela per epoch-menit).
- `SkillListener` mencatat blok pasangan pemain (BlockPlaceEvent MONITOR) dan
  melewatkan XP untuk blok tersebut saat dipecah; XP kill pemain melewati cooldown
  same-kill.
- `SkillProgressMenu` mendapat tombol "Jalur progres" (slot 47, bisa digeser lewat
  `gui/skill.yml slots.path`).
- `SellManager` mengenali nilai jual ikan custom dari PDC.
- `SettingsMenu`/`SettingsGuiListener` dirombak ke navigasi kategori (logika toggle
  per-kunci lama dipertahankan utuh - `SettingCommand` chat tidak berubah).
- Versi: pom.xml & plugin.yml -> 1.4.0.

### Kompatibilitas & migrasi
- **Tidak ada perubahan format data lama**: skills.yml, homes.yml, auctions.yml,
  stats.yml, settings.yml dibaca persis seperti sebelumnya.
- Kunci config/messages baru digabung otomatis oleh ResourceMerger saat startup;
  config lama tanpa bagian `gear:`/`fishing:` mendapat bawaan lengkap.
- Riwayat gear/rod/ikan hidup di PDC item - tidak ada file penyimpanan baru.
- `gui/settings.yml` lama tetap dibaca; kunci slot toggle lama tidak dipakai lagi
  (diabaikan tanpa error), kartu kategori memakai kunci `slots.category-*` baru.

### Perbaikan
- Tulis-balik salinan item (getItemInMainHand/getArmorContents mengembalikan salinan
  di CraftBukkit) dipastikan di semua jalur gear - tanpa ini counter/PDC akan hilang.
- Cap pemilik pertama tidak hilang saat kill mob (urutan tulis-balik diperbaiki
  sebelum rilis).

## [1.3.0] - sebelumnya
- Multi-buff per skill, menu progres skill, /skill top, diagnostik /skill check.

## [1.2.x]
- Sistem skill pertama (11 skill), GUI /skill, buff dasar.

## [1.1.0] / [1.0.0]
- Fondasi: economy (Vault), sell, home, rtp, combat, auction, worth, statistik,
  scoreboard, profile, setting, tpa, bounty, nametag.
