# W2NSMP 1.4.0 - Daftar Fitur

Plugin SMP survival untuk **Paper 26.2** (Java 25). Semua fitur bisa dimatikan/diatur
lewat `config.yml`; semua teks lewat `messages.yml`; semua tampilan GUI lewat `gui/*.yml`.

## Fitur inti (sejak 1.0-1.1)
| Fitur | Command | Keterangan |
|---|---|---|
| Economy (Vault) | `/saldo`, `/bayar` | Provider dari plugin lain (mis. EssentialsX) |
| Jual item | `/jual` (GUI) | Harga di prices.yml + ekonomi dinamis opsional |
| Home | `/home`, `/sethome`, `/delhome` | GUI + slot home bertingkat |
| Random teleport | `/rtp` (`/wild`) | Pencarian lokasi aman |
| Combat tag | - | Anti combat-log, blokir command saat combat |
| Auction house | `/ah` | Listing antar pemain + kotak surat |
| Harga item | `/harga` | Lore harga di item |
| Statistik & peringkat | `/top`, `/profile` | Per-UUID, GUI |
| Scoreboard pribadi | `/sb` | Baris bisa diatur per pemain |
| Pengaturan pribadi | `/setting` | **1.4.0: GUI berkategori** (lihat bawah) |
| Teleport antar pemain | `/tpa`, `/tpahere`, ... | GUI + hitung mundur |
| Bounty | `/bounty` | Hadiah buruan |
| Nametag uang | - | Saldo di bawah nama |

## Skill (/skill) - 11 skill, max level 50 (configurable)
Fighting, Defense, Archery, Agility, Mining, Woodcutting, Farming, Fishing,
Endurance, Vitality, Recovery.

- **GUI premium**: menu utama ikon per skill (pedang/perisai/busur/bulu/beliung/kapak/
  cangkul/pancing/armor/heart/potion) -> menu kemajuan (bar XP, kartu buff, milestone)
  -> **jalur progres snake-path (baru 1.4.0)**: node per level, berkelok, berhalaman,
  milestone buff ditandai; klik milestone = detail buff.
- **Kurva XP configurable**: `skills.xp.base` / `skills.xp.multiplier`
  (alias baru; `skills.curve.*` lama tetap didukung), pengali global untuk event.
- **33 buff bawaan** (16 jenis), semuanya di `skills.list.*` - nilai per level, plafon,
  level pembuka bebas diatur; tidak ada yang OP (reduksi damage dijaga selftest rilis).
- **Anti-abuse (baru 1.4.0)**: plafon XP/menit, blok pasangan pemain tidak memberi XP,
  cooldown XP kill pemain yang sama. Semua di `skills.anti-abuse.*`.
- XP tersimpan di `skills.yml` (autosave berkala + saat keluar/stop; level dihitung
  ulang dari XP sehingga perubahan kurva aman).
- Admin: `/skill set|add|reset|info`, diagnostik `/skill check`, peringkat `/skill top`.

## Gear history & progresi (baru 1.4.0)
- Riwayat **di item (PDC)**: First Owner (UUID+nama), Player Kills, Usage - kebal
  rename/chest/trade/restart.
- Kelas gear dari config (`gear.classes.*`): swords, axes, bows, armor (bawaan) -
  materials, counter (kills/uses), syarat skill, milestone perk bebas diatur;
  kelas baru cukup ditambah di config.
- Milestone bawaan: pedang 10/50/100 kill -> Bleeding I/II/III (damage berkala,
  cooldown per config); kapak -> lifesteal; busur -> bonus damage; armor -> resistance
  per potongan (plafon total 80%).
- Syarat item (mis. Fighting 15): tidak terpenuhi = perk terkunci, pemakaian dasar
  tetap jalan (opsional: `gear.unmet-damage-percent`, `gear.first-owner-only-perks`).
- Lore riwayat dinamis di ekor lore, tidak merusak lore asli, ditulis hemat
  (`gear.lore-update-every`).

## Custom fishing (baru 1.4.0)
- **Registry terpusat** di config: `fishing.fish.*` (7 ikan contoh termasuk
  **Golden Koi** Legendary dan **Leviathan Muda** Mythic).
- 6 rarity Common -> Mythic; gerbang level skill Fishing per rarity
  (`fishing.rarity-level.*`).
- Tiap ikan: nama, rarity, material, lore, ukuran acak (cm; ikut menaikkan nilai),
  nilai jual, XP, bobot undian, syarat biome/cuaca/waktu/level fishing/level rod.
- Identitas via PDC - beda dari ikan vanilla, aman disimpan/dipindah/dijual;
  `/jual` otomatis memakai nilai di PDC.
- Peluang dasar ikan custom `fishing.custom-chance-percent` + bonus dari rod/attachment.

## Rod & /rod (baru 1.4.0)
- `/rod` (alias `/pancingan`): GUI level rod, XP rod, statistik efek, attachment, upgrade.
- Upgrade Lv. 1-10 dengan bahan custom: **Rod Upgrade Crystal, Fishing Core, Rare
  Scale, Golden Thread** (didapat sebagai bonus tangkapan custom atau
  `/w2nsmp fishing give`). Biaya + XP per level di `fishing.rod.levels.*`.
- Efek kumulatif per level + attachment: bite-speed (gigitan lebih cepat),
  custom-chance, rarity-boost, value (nilai jual), xp (XP skill), double-catch.
- **7 attachment**: Lucky Hook, Golden Hook, Reinforced Line, Magic Float,
  Deep Sea Weight, Treasure Charm, XP Reel - pasang/copot lewat GUI tanpa risiko
  dupe/hilang (item tidak pernah dititipkan ke GUI).

## /setting berkategori (baru 1.4.0)
Halaman utama: kartu **Scoreboard / Notifikasi / Gameplay / Visual / Lain-lain**.
Sub-halaman berisi toggle kategori (termasuk toggle per-baris scoreboard).
`/setting toggle <kunci>` via chat tetap didukung.

## Command & permission baru/berubah (1.4.0)
| Command | Permission | Default | Keterangan |
|---|---|---|---|
| `/rod` (`/pancingan`) | `w2nsmp.rod` | true | GUI rod (baru) |
| `/w2nsmp fishing give` | `w2nsmp.fishing.admin` | op | Beri item pancing custom (baru) |
| `/skill`, `/setting` | (tidak berubah) | | GUI di-rework, kunci lama tetap |

Config on/off command: `commands.rod.enabled`.

## Kunci config baru (1.4.0)
- `skills.xp.base`, `skills.xp.multiplier` (alias opsional)
- `skills.anti-abuse.{max-xp-per-minute, same-kill-cooldown-seconds,
  track-placed-blocks, placed-block-expiry-seconds}`
- `gear.*` (enabled, write-lore, lore-update-every, first-owner-only-perks,
  unmet-damage-percent, heal-cap, notify-milestone, classes.*)
- `fishing.*` (enabled, custom-chance-percent, rarity-level.*, rod.*, items.*, fish.*)
- `gui/skill.yml`: `slots.path`, `path-material`, bagian `path.*`
- `gui/settings.yml`: `slots.category-*`, `slots.back`, `category-materials.*`
- `gui/rod.yml` (file baru)

## Penyimpanan
| Data | Tempat | Catatan |
|---|---|---|
| XP skill | `skills.yml` | Format lama, tidak berubah |
| Riwayat gear | PDC item | Tanpa file baru |
| Ikan/rod/attachment | PDC item | Tanpa file baru |
| Setting pemain | `settings.yml` | Format lama, tidak berubah |

## Build & rilis
`bash tools/build.sh` (kompilasi) - `bash tools/release.sh 1.4.0` (6 gerbang:
kompilasi, selftest logika/listener/buff/top/path, linkage, konsistensi config,
kemas JAR, verifikasi isi JAR). Hasil: `W2NSMP-1.4.0.jar`.
