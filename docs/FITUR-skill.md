# Fitur `/skill` — 11 skill dengan level & buff (v1.2.0, diperbaiki di v1.2.1)

Ditambahkan pada rilis `W2NSMP-1.2.0.jar`. Prinsip yang dipegang: **fitur lama tidak
diubah perilakunya**. Skill punya berkas data sendiri, config sendiri, seksi pesan sendiri,
dan GUI sendiri; tidak ada satu pun class fitur lain yang diganti logikanya (yang disentuh
hanya titik pemasangan: `W2NSMP`, `CommandManager`, `ListenerManager`, `GuiConfigs`).

---

## 1. Perintah & izin

| Perintah | Alias | Izin | Fungsi |
|---|---|---|---|
| `/skill` | `/skills`, `/keahlian` | `w2nsmp.skill` (default: semua pemain) | Membuka GUI skill. Dari konsol: menampilkan bantuan. |
| `/skill <skill>` | | `w2nsmp.skill` | Detail satu skill (level, bar, XP, buff) |
| `/skill list` | `daftar` | `w2nsmp.skill` | Ringkasan semua skill sendiri |
| `/skill help` | `bantuan` | `w2nsmp.skill` | Daftar perintah |
| `/skill info <pemain> [skill]` | `lihat` | `w2nsmp.skill.admin` (default: op) | Melihat skill pemain lain |
| `/skill set <pemain> <skill> <level>` | `setel` | `w2nsmp.skill.admin` | Menyetel level (XP dihitung ulang ke awal level itu) |
| `/skill add <pemain> <skill> <xp>` | `tambah` | `w2nsmp.skill.admin` | Menambah/mengurangi XP (boleh negatif) |
| `/skill reset <pemain> [skill]` | `hapus` | `w2nsmp.skill.admin` | Mereset satu skill atau semua skill pemain |
| `/skill check` | `cek`, `diagnosa` | `w2nsmp.skill` (rincian penuh: `w2nsmp.skill.admin`) | Diagnostik: status fitur, mode game/world Anda, **uji XP +1**, dan (admin) laporan listener, API server, data, serta kesalahan terakhir |

Tab-completion aktif untuk nama skill, nama pemain online, dan angka level/XP yang wajar.
`w2nsmp.skill` dan `w2nsmp.skill.admin` sudah menjadi anak dari `w2nsmp.admin`.

Sesuai aturan "anti-timpa command" plugin ini, `/skill` **tidak** dideklarasikan di
`plugin.yml`; command didaftarkan saat runtime oleh `CommandManager`, jadi selalu tersedia
sebagai `/w2nsmp:skill` dan mengambil label polos `/skill` hanya bila label itu bebas.

---

## 2. Daftar skill

| Kunci | Nama (messages.yml) | Ikon | Slot GUI | Cara naik level | Buff |
|---|---|---|---|---|---|
| `fighting` | Pertarungan | DIAMOND_SWORD | 10 | damage & kill jarak dekat | +0,5% damage melee/level (maks +25%) |
| `defense` | Pertahanan | SHIELD | 11 | menerima damage | −0,4% damage diterima/level (maks −20%) |
| `archery` | Memanah | BOW | 12 | damage & kill lewat proyektil | +0,6% damage proyektil/level (maks +30%) |
| `agility` | Ketangkasan | LEATHER_BOOTS | 13 | menempuh jarak (jalan/lari) | +0,4% kecepatan jalan/level (maks +20%) |
| `mining` | Menambang | GOLDEN_PICKAXE | 14 | menghancurkan batu/ore | efek Haste, buka di Lv. 5, naik tiap 10 level (maks Haste IV) |
| `woodcutting` | Menebang Kayu | GOLDEN_AXE | 15 | menebang batang kayu | +0,4% peluang satu drop tambahan/level (maks 20%) |
| `farming` | Bertani | WHEAT | 16 | memanen tanaman | +0,5% peluang hasil panen tambahan/level (maks 25%) |
| `fishing` | Memancing | FISHING_ROD | 20 | menarik hasil pancingan | +0,5% peluang hasil ganda/level, buka di Lv. 3 (maks 25%) |
| `endurance` | Daya Tahan | COOKED_BEEF | 21 | online (XP per menit) | −0,6% damage lingkungan/level (maks −30%) |
| `vitality` | Vitalitas | GOLDEN_APPLE | 22 | bertahan saat darah rendah | pemulihan pasif +0,04 heart/level (maks 1 heart) tiap 5 detik, hanya bila 10 detik terakhir tidak kena damage |
| `recovery` | Pemulihan | GLISTERING_MELON_SLICE | 23 | darah pulih alami | regen alami diperkuat +0,6%/level (maks +30%) |

Daftar blok per skill (mining/woodcutting/farming) ada di `config.yml` →
`skills.list.<kunci>.blocks` dan bebas ditambah/dikurangi admin. Nama material atau
`DamageCause` yang tidak dikenal versi server **diabaikan** (dicatat di log), bukan
menyebabkan error.

**Pengurangan damage bertumpuk** (`defense` + `endurance`) tetapi dibatasi keras: total
pengurangan tidak pernah lebih dari **90%**, sehingga tidak ada pemain yang kebal.

---

## 3. Kurva level

Level **tidak disimpan**; level dihitung dari XP total memakai kurva:

```
XP untuk naik dari level L ke L+1 = skills.curve.base-xp × L ^ skills.curve.exponent
```

Bawaan `base-xp: 25.0`, `exponent: 1.25`, `max-level: 50`:

| Dari → ke | XP dibutuhkan | XP kumulatif |
|---|---|---|
| Lv 1 → 2 | 25 | 25 |
| Lv 2 → 3 | 59 | 84 |
| Lv 5 → 6 | 187 | 512 |
| Lv 10 → 11 | 445 | 2.202 |
| Lv 20 → 21 | 1.057 | 9.932 |
| Lv 30 → 31 | 1.755 | 24.286 |
| Lv 40 → 41 | 2.515 | 45.972 |
| Lv 49 → 50 | 3.241 | **72.209** |

Konsekuensi yang menguntungkan admin: **mengubah kurva langsung mengubah level semua
pemain tanpa migrasi data**, dan data tidak pernah bisa tidak sinkron dengan config.
`skills.curve.global-multiplier` (mis. `2.0` saat event) melipatkan semua XP yang masuk
tanpa mengubah bentuk kurva.

Perkiraan kasar lama bermain dengan angka XP bawaan (satu skill sampai Lv. 50):
mining ±24.000 blok · fighting ±3.400 kill mob · farming ±14.400 panen ·
fishing ±4.000 tangkapan · endurance ±60 jam online · agility ±206.000 blok ditempuh.
Semua angka itu bisa disetel per skill lewat `skills.list.<kunci>.xp.*`.

---

## 4. Data pemain

- Berkas: `plugins/W2NSMP/skills.yml` — **terpisah** dari `statistics.yml`, `w2nsmp.db`,
  economy, home, dan data fitur lain. Menghapusnya hanya mereset skill ke level 1.
- Yang disimpan hanya `xp` per skill + `name` + `updated` (per UUID).
- Penulisan dilakukan **async** (serialisasi di thread utama, tulis berkas di thread async)
  dan digabung bila beruntun; simpan berkala tiap `skills.save-interval-seconds` (bawaan
  300 detik), saat pemain keluar, dan saat plugin dimatikan.
- Bila berkas gagal dibaca saat startup, plugin **menolak menimpa** berkas itu agar data
  pemain tidak hilang karena satu kesalahan baca.

---

## 5. GUI

`/skill` membuka menu 54 slot: 11 ikon skill (slot 10–16 dan 20–23), item ringkasan
(slot 49), tombol close (slot 53), sisanya filler. Tampilan diatur di
`src/main/resources/gui/skill.yml` (judul, material, slot, suara); seluruh kalimat diambil
dari `messages.yml` bagian `skill`. Slot di `gui/skill.yml` menang atas
`skills.list.<kunci>.slot`; bila dua skill disetel ke slot yang sama, salah satunya
dilewati (dicatat di log) dan menu tetap tampil.

Klik kiri pada ikon skill mengirim detail skill itu ke chat dan menyegarkan menu. Semua
interaksi lain (shift-click, klik kanan, drag, double-click, swap-offhand, drop, ambil dari
creative) dibatalkan, jadi item tidak bisa dikeluarkan dari menu. Menu pemain ditutup
otomatis saat plugin dinonaktifkan (`SkillMenu.closeAll`).

Placeholder yang tersedia di pesan/GUI: `%player% %skill% %key% %level% %max% %next-level%
%xp% %xp-into% %xp-needed% %xp-to-next% %percent% %bar% %buff% %buff-desc% %source%
%unlock% %total-xp% %total-level%` (plus `%best%`, `%best-level%`, `%skills%`, `%file%` di
ringkasan GUI).

---

## 6. Tidak mengandaikan API server ada

Setiap permukaan API yang dipakai fitur ini diperiksa saat startup oleh
`skill/SkillApiProbe.java`. Bila tidak tersedia di versi/fork server, **hanya bagian itu**
yang dimatikan dan alasannya ditulis ke log — plugin tetap jalan, fitur lain tidak
terpengaruh:

| API | Bila tidak ada |
|---|---|
| `EntityDamageEvent.getDamage/setDamage` | buff damage (fighting/defense/archery/endurance) mati |
| `EntityDamageEvent.getCause` | buff endurance mati |
| `LivingEntity.getHealth/setHealth` | buff vitality/recovery mati |
| `Player.setWalkSpeed` | buff agility mati |
| `BlockBreakEvent.getBlock` | XP mining/woodcutting/farming mati |
| `Block.getDrops` | drop tambahan woodcutting/farming mati |
| `PlayerFishEvent` | skill fishing nonaktif (listener tidak dipasang) |
| `PotionEffectType` Haste (lewat refleksi: Registry → `getByKey` → `getByName` → field statis) | buff Haste mining mati |

Baris log yang muncul saat startup: `Skill: semua API buff tersedia (...)` bila lengkap,
atau `Skill: beberapa buff/XP dinonaktifkan otomatis karena API server tidak tersedia:`
diikuti rinciannya. `PlayerMoveEvent.getFrom()` sengaja tidak dipakai (jarak dilacak
sendiri, dengan penjaga teleport: pindah >10 blok atau ganti world mereset akumulasi).

Kecepatan jalan pemain **disimpan sebelum diubah** dan dikembalikan saat skill dimatikan,
pemain keluar, atau plugin dinonaktifkan. Bila plugin lain sudah mengubah kecepatan itu,
W2NSMP tidak menimpanya.

Drop tambahan diambil dari `Block.getDrops()` tanpa konteks tool, jadi tidak menggandakan
drop yang memang tidak akan keluar (mis. ore tanpa pickaxe) — tidak ada duplikasi item.
Kill XP memakai atribusi pukulan terakhir: proyektil dicatat saat mengenai target dan
dikonsumsi dalam 5 detik (dibatasi 4.096 entri, dibersihkan tiap detik).

---

## 7. Referensi config (`config.yml` → `skills:`)

```yaml
skills:
  enabled: true                 # matikan seluruh fitur tanpa menghapus data
  max-level: 50                 # level tertinggi (boleh dinaikkan kapan saja)
  save-interval-seconds: 300    # 0 = simpan hanya saat keluar/stop
  notify-level-up: true         # umumkan kenaikan level di chat
  level-up-sound: "minecraft:entity.player.levelup"   # "none" = mati
  xp-gain-actionbar: false      # tulis XP ke action bar (bisa berisik)
  notify-extra-drop: false      # kabarkan saat buff drop ganda berhasil
  progress-bar-width: 10        # panjang %bar%
  curve: {base-xp: 25.0, exponent: 1.25, global-multiplier: 1.0}
  buffs:
    enabled: true               # matikan semua buff, level & XP tetap jalan
    potion-refresh-seconds: 10  # Haste dipasang ulang tiap X detik
    heal-interval-seconds: 5    # interval pemulihan vitality/recovery
    heal-cap: 20.0              # darah maks yang boleh dipulihkan buff
  worlds: []                    # kosong = semua world
  blacklisted-worlds: []        # menang atas "worlds"
  skip-gamemodes: [CREATIVE, SPECTATOR]
  list:
    <kunci-skill>:
      enabled: true
      icon: DIAMOND_SWORD       # material cadangan bila gui/skill.yml tidak mengisinya
      slot: 10
      xp: {...}                 # multiplier + sumber XP skill itu
      buff: {...}               # unlock-level, per-level, max, dst.
      blocks: [...]             # untuk mining/woodcutting/farming
      # causes: [...]           # khusus endurance: DamageCause yang dikurangi
```

Perintah `/skill` sendiri juga punya saklar di `commands.skill.enabled`.
Semua kunci di atas **benar-benar dibaca kode** (diperiksa otomatis oleh
`tools/verify_feature.py`: 156 kunci config, 56 kunci pesan, 15 titik wiring).

---

## 8. Memastikan fitur benar-benar hidup di server Anda

Fitur ini punya tiga lapis pengaman supaya tidak pernah "hidup tapi tuli":

1. **Watchdog pendaftaran listener.** Dua detik setelah plugin aktif — lalu tiap 60 detik —
   `SkillService.ensureListeners()` memeriksa langsung ke `HandlerList` server (lewat refleksi)
   apakah `SkillGuiListener`/`SkillListener` benar-benar terdaftar pada event yang diharapkan.
   Bila hilang (mis. ada plugin lain yang membersihkan handler, atau reload server yang tidak
   rapi), listener dipasang ulang otomatis dan kejadian itu ditulis sebagai `SEVERE` di log.
   Pendaftaran dijaga agar tidak pernah dobel, jadi XP tidak bisa terhitung ganda.
2. **Tidak ada kegagalan diam-diam.** Setiap handler membungkus isinya dengan
   `catch (Throwable)` dan mencatatnya ke `SkillDiagnostics`; peringatan muncul **sekali** di
   konsol (`Skill: gangguan di <lokasi> -> ...`) dan rinciannya tersimpan untuk `/skill check`.
   Kegagalan API yang sudah dikenal tetap ditangani lewat probe (lihat bagian 6).
3. **XP yang dilewati selalu dijelaskan.** Bila XP tidak bertambah karena mode game
   (`skills.skip-gamemodes`) atau world (`skills.worlds`/`blacklisted-worlds`), pemain
   mendapat pesan sekali per sesi — jadi tidak ada lagi "kok tidak naik?" tanpa keterangan.

Cara tercekat memastikan semuanya jalan di server Anda:

```
/skill check
```

Contoh keluaran untuk pemain:

```
[W2NSMP] Diagnostik skill - Steve
• Fitur: aktif | buff: aktif | skill aktif: 11 | level maks: 50
• Mode game Anda: SURVIVAL -> XP aktif (dilewati: SPECTATOR)
• World Anda: world -> XP aktif (diizinkan: semua world)
• Uji XP (+1 ke Daya Tahan): OK (XP tercatat) | XP 1 | Lv. 1
```

Baris tambahan untuk admin (`w2nsmp.skill.admin`): API server (hasil probe), laporan
pendaftaran listener per event, jumlah kesalahan terakhir, berkas data + jumlah profil, dan
ringkasan setelan tiap skill.

---

## 8b. Memeriksa kesehatan fitur

```bash
bash tools/release.sh 1.2.0          # build + 6 gerbang verifikasi
/w2nsmp debug                        # di server: ringkasan skill, API, dan setelan tiap skill
/w2nsmp reload                       # muat ulang config, messages, gui, dan skill
```

`/w2nsmp debug` menampilkan baris `api:` (hasil probe) dan ringkasan tiap skill (level
maks, kurva, buff aktif) — cara tercepat memastikan buff mana yang benar-benar hidup di
server Anda.

---

## 8c. Masalah yang pernah terjadi dan sudah diperbaiki

**v1.2.0 — listener "tuli" (fatal, sudah diperbaiki di v1.2.1).** Stub anotasi
`org.bukkit.event.EventHandler` yang dipakai untuk kompilasi di sandbox tidak memuat
`@Retention(RUNTIME)`. Java lalu memakai retention bawaan (`CLASS`): class tetap terkompilasi
tanpa error, tetapi saat runtime Bukkit — yang menemukan handler lewat refleksi
`method.isAnnotationPresent(EventHandler.class)` — melihat **nol handler**. Listener
terdaftar, tidak ada error di log, namun: item GUI bisa diambil (klik tidak pernah dibatalkan),
XP tidak pernah bertambah, dan buff tidak pernah dipasang. Fitur lama tidak terpengaruh karena
class-nya tetap bytecode asli dari `W2NSMP-1.0.0.jar`.

Perbaikannya berlapis: stub anotasi kini memakai `@Retention(RUNTIME)` + `@Target(METHOD)`;
`tools/selftest/ListenerAnnotationTest.java` menjalankan refleksi ala Bukkit terhadap setiap
listener dan gagal bila ada handler yang tidak terlihat; `tools/verify_jar.py` memeriksa
bytecode JAR rilis (`RuntimeVisibleAnnotations` wajib ada di setiap class yang merujuk
`@EventHandler`, dan handler untuk tiap event wajib ada); watchdog di dalam plugin memverifikasi
pendaftaran ke `HandlerList` server saat berjalan.

**v1.2.0 — inner class lama ikut terkemas (sudah diperbaiki).** Perhitungan "keluarga class"
di `tools/package_jar.py` salah untuk inner class, sehingga `SkillService$RuntimeState` dan
sebagainya bisa terkemas dari byte lama. Diperbaiki lewat `family_of()` + gerbang byte-per-byte
di `tools/verify_jar.py`.

**v1.2.0 — config lama mematikan skill diam-diam (sudah diperbaiki).** Daftar `blocks` dan
`buff.causes` dibaca tanpa memeriksa apakah kuncinya ada, sehingga server yang naik versi dengan
`config.yml` lama mendapat daftar kosong. Kini dijaga `config.isList()` dan nilai bawaannya ada
di kode (identik dengan `config.yml`, diperiksa otomatis).

---

## 9. Naik versi dari 1.0.0 / 1.1.0

Config server Anda **tidak ditimpa**. Yang terjadi saat upgrade:

- `config.yml` lama (belum punya seksi `skills:`) tetap dipakai; setiap nilai skill diambil
  dari bawaan di kode (`SkillSettings.applyDefaults`), termasuk daftar blok
  mining/woodcutting/farming dan daftar `DamageCause` endurance. Pembacaan daftar dijaga
  `config.isList()`, jadi config lama tidak pernah membuat skill "mati diam-diam".
  `tools/verify_feature.py` memastikan bawaan di kode identik dengan `config.yml` bawaan.
- `messages.yml` lama tetap dipakai; kunci `skill.*` yang belum ada di berkas Anda jatuh ke
  `messages.yml` di dalam JAR (mekanisme `Messages.value()` yang sudah ada sejak 1.0.0),
  jadi pemain tidak pernah melihat kunci mentah.
- `gui/skill.yml` belum ada di server Anda → dibuat otomatis dari JAR saat pertama dipakai.
- `statistics.yml`/`w2nsmp.db`/data lain tidak disentuh; `skills.yml` baru dibuat saat ada
  XP pertama.

Bila ingin melihat/menyetel semua opsi skill, salin seksi `skills:` dari `config.yml` bawaan
(ada di dalam JAR) ke config server Anda, lalu `/w2nsmp reload`.

---

## 10. Tanya-jawab singkat

**Apakah statistik/ekonomi/scoreboard berubah?** Tidak. Tidak ada class fitur lain yang
disunting logikanya, dan `skills.yml` terpisah dari berkas data yang sudah ada.

**Bagaimana kalau server saya versi lama?** Fitur tetap dimuat; buff/XP yang API-nya tidak
ada dimatikan otomatis dan dilaporkan di log. Skill lain tetap jalan.

**Bisakah level maksimum dinaikkan nanti?** Bisa. Ubah `skills.max-level`; level pemain
dihitung ulang dari XP yang tersimpan, tidak ada migrasi.

**Bisakah buff dimatikan untuk turnamen?** Bisa: `skills.buffs.enabled: false` (level & XP
tetap jalan), atau matikan per skill lewat `skills.list.<kunci>.enabled: false`.

**Bagaimana cara memberi hadiah level?** `/skill set <pemain> <skill> <level>` atau
`/skill add <pemain> <skill> <xp>` (butuh `w2nsmp.skill.admin`).
