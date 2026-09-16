# Fitur `/skill` — 11 skill, buff bertahap, menu progres & peringkat (v1.3.0)

Ditambahkan pada rilis `W2NSMP-1.2.0.jar`, diperbaiki di `1.2.1`, dan diperluas di `1.3.0`.
Prinsip yang dipegang: **fitur lama tidak diubah perilakunya**. Skill punya berkas data
sendiri, config sendiri, seksi pesan sendiri, dan GUI sendiri; tidak ada satu pun class fitur
lain yang diganti logikanya (yang disentuh hanya titik pemasangan: `W2NSMP`, `CommandManager`,
`ListenerManager`, `GuiConfigs`).

**Baru di v1.3.0**

| Perubahan | Artinya untuk pemain/admin |
|---|---|
| **Beberapa buff per skill** | Tiap skill punya **3 buff** yang terbuka bertahap (level 1–5, 10–28, 20–40), bukan satu buff saja. Total 33 buff, 16 jenis. |
| **Menu progres skill** | Klik ikon skill di `/skill` → menu berisi level, bar kemajuan, **kartu tiap buff** (yang belum terbuka menampilkan *"buka di Lv. X"*), milestone, dan peringkat Anda. |
| **Keseimbangan ulang** | Nilai buff di level 50 dibuat "ukuran awal game": damage maks +17,5%, pengurangan damage maks −17%, peluang loot/drop ganda < 10%, XP vanilla < +20%. |
| **Peringkat (top) skill** | `/skill top` (total level semua skill) dan `/skill top <skill>` (per skill), plus tombol **Top Skill** di GUI dan kartu peringkat di menu progres. Termasuk pemain offline. |
| **Jenis buff baru** | critical, block, loot mob ganda, XP vanilla ore, peluang XP skill ganda, dan buff potion apa pun (slow falling, fire resistance, absorption, health boost). |
| **Pengumuman buff baru** | Saat naik level ke titik pembukaan buff, chat memberi tahu buff apa yang baru didapat. |

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
| `/skill top` | `peringkat`, `ranking` | `w2nsmp.skill` | Peringkat **total level** semua skill (10 teratas, `skills.top-limit`) + posisi Anda |
| `/skill top <skill>` | | `w2nsmp.skill` | Peringkat satu skill (level skill itu; XP sebagai pemecah seri) |
| `/skill check` | `cek`, `diagnosa` | `w2nsmp.skill` (rincian penuh: `w2nsmp.skill.admin`) | Diagnostik: status fitur, mode game/world Anda, **uji XP +1**, dan (admin) laporan listener, API server, efek potion yang tidak dikenal, data, serta kesalahan terakhir |

Tab-completion aktif untuk nama skill (termasuk setelah `/skill top`), nama pemain online,
dan angka level/XP yang wajar.
`w2nsmp.skill` dan `w2nsmp.skill.admin` sudah menjadi anak dari `w2nsmp.admin`.

Sesuai aturan "anti-timpa command" plugin ini, `/skill` **tidak** dideklarasikan di
`plugin.yml`; command didaftarkan saat runtime oleh `CommandManager`, jadi selalu tersedia
sebagai `/w2nsmp:skill` dan mengambil label polos `/skill` hanya bila label itu bebas.

---

## 2. Daftar skill

| Kunci | Nama | Ikon | Slot | Cara naik level | Buff 1 (awal) | Buff 2 | Buff 3 |
|---|---|---|---|---|---|---|---|
| `fighting` | Pertarungan | DIAMOND_SWORD | 10 | damage & kill jarak dekat | damage melee +0,30%/lvl (maks +15%) | **Lv. 10** peluang critical +0,25%/lvl (maks 12,5%, damage +50%) | **Lv. 25** peluang loot mob ganda +0,30%/lvl (maks 9%) |
| `defense` | Pertahanan | SHIELD | 11 | menerima damage | pengurangan damage +0,28%/lvl (maks −14%) | **Lv. 15** peluang block +0,20%/lvl (maks 10%, tahan 50% damage) | **Lv. 30** pengurangan damage tingkat 2 +0,15%/lvl (maks −6%) |
| `archery` | Memanah | BOW | 12 | damage & kill lewat proyektil | damage proyektil +0,35%/lvl (maks +17,5%) | **Lv. 12** peluang critical +0,30%/lvl (maks 15%) | **Lv. 28** peluang loot mob ganda +0,25%/lvl (maks 8%) |
| `agility` | Ketangkasan | LEATHER_BOOTS | 13 | menempuh jarak (jalan/lari) | kecepatan jalan +0,25%/lvl (maks +12,5%) | **Lv. 10** pengurangan damage jatuh/menabrak (maks −20%) | **Lv. 25** efek *Slow Falling* |
| `mining` | Menambang | GOLDEN_PICKAXE | 14 | menghancurkan batu/ore | **Lv. 5** Haste I, naik tiap 12 level (maks Haste III) | **Lv. 15** XP vanilla ore +0,40%/lvl (maks +20%) | **Lv. 30** peluang XP skill ganda (maks 8%) |
| `woodcutting` | Menebang Kayu | GOLDEN_AXE | 15 | menebang batang kayu | peluang drop tambahan +0,30%/lvl (maks 15%) | **Lv. 22** drop tambahan tingkat 2 (maks 8%) | **Lv. 32** peluang XP skill ganda (maks 7%) |
| `farming` | Bertani | WHEAT | 16 | memanen tanaman | peluang panen tambahan +0,30%/lvl (maks 15%) | **Lv. 20** panen tambahan tingkat 2 (maks 10%) | **Lv. 35** peluang XP skill ganda (maks 6%) |
| `fishing` | Memancing | FISHING_ROD | 20 | menarik hasil pancingan | peluang tangkapan ganda +0,35%/lvl (maks 17,5%) | **Lv. 25** tangkapan ganda tingkat 2 (maks 10%) | **Lv. 30** peluang XP skill ganda (maks 8%) |
| `endurance` | Daya Tahan | COOKED_BEEF | 21 | online (XP per menit) | pengurangan damage lingkungan +0,40%/lvl (maks −20%) | **Lv. 12** peluang block +0,15%/lvl (maks 7,5%, tahan 40%) | **Lv. 40** efek *Fire Resistance* |
| `vitality` | Vitalitas | GOLDEN_APPLE | 22 | bertahan saat darah rendah | **Lv. 3** heal pasif +0,03 heart/lvl (maks 1,5 heart) tiap 5 detik | **Lv. 15** *Absorption* I, naik ke II di Lv. 35 | **Lv. 28** regen alami diperkuat (maks +10%) |
| `recovery` | Pemulihan | GLISTERING_MELON_SLICE | 23 | darah pulih alami | **Lv. 3** regen alami +0,40%/lvl (maks +20%) | **Lv. 20** heal pasif kecil (maks 1 heart, jeda 12 detik) | **Lv. 40** efek *Health Boost* (+2 heart maks) |

Daftar blok per skill (mining/woodcutting/farming) ada di `config.yml` →
`skills.list.<kunci>.blocks` dan bebas ditambah/dikurangi admin. Nama material atau
`DamageCause` yang tidak dikenal versi server **diabaikan** (dicatat di log), bukan
menyebabkan error.

**Pengurangan damage bertumpuk** (`defense` + `endurance` + `agility`) tetapi dibatasi keras:
total pengurangan tidak pernah lebih dari **90%**, sehingga tidak ada pemain yang kebal.
Peluang block dibatasi 75% dan hanya berlaku untuk damage serangan (pukulan, sapuan, panah,
ledakan makhluk, duri) — damage lingkungan sudah diurusi buff pengurangan lingkungan, jadi
tidak pernah dikurangi dua kali.

---

## 2b. Jenis buff & cara membacanya

16 jenis buff (`skill/BuffKind.java`). Semua dibaca dari config, jadi admin bebas memindah
jenis buff antar skill atau mengganti seluruh susunannya:

| Jenis (`kind`) | Efek | Catatan |
|---|---|---|
| `damage-melee` | damage serangan jarak dekat bertambah | dihitung saat `EntityDamageByEntityEvent` |
| `damage-projectile` | damage panah/trident bertambah | sama, untuk serangan proyektil |
| `crit-chance` | peluang pukulan menjadi critical | `power` = tambahan damage saat critical (50 = +50%) |
| `damage-reduction` | damage yang diterima berkurang | boleh dipasang dua tingkat di satu skill |
| `block-chance` | peluang menahan sebagian damage serangan | `power` = persen damage yang ditahan |
| `environment-reduction` | damage lingkungan berkurang | `causes` = daftar `DamageCause` |
| `walk-speed` | kecepatan jalan bertambah | kecepatan asli disimpan & dikembalikan |
| `haste` | efek Haste | `potion: haste`, tingkat naik tiap `amplifier-every-levels` |
| `potion` | efek potion apa pun | `potion: absorption`, `slow_falling`, `fire_resistance`, `health_boost`, ... |
| `extra-drop` | peluang satu hasil tambahan saat menebang/memanen | drop diambil dari `Block.getDrops()` |
| `extra-catch` | peluang hasil pancingan ganda | |
| `mob-loot` | peluang satu drop mob digandakan | drop yang sudah diubah plugin lain tetap dihormati |
| `vanilla-xp` | XP vanilla ore bertambah | hanya untuk block (mining) |
| `double-xp` | peluang XP skill masuk dua kali lipat | murni perhitungan, tidak butuh API |
| `passive-heal` | darah pulih perlahan di luar pertarungan | `delay-seconds` = jeda setelah kena damage |
| `regen-boost` | regenerasi alami diperkuat | dihitung dari darah yang pulih sendiri |

Rumus nilai buff (`skill/SkillBuff.java`):

```
level <  unlock-level  ->  0 (belum terbuka)
level >= unlock-level  ->  min(max, per-level * (level - unlock-level + 1))
tingkat potion         ->  min(amplifier-max, (level - unlock-level) / amplifier-every-levels)
```

Artinya buff yang terbuka di Lv. 25 dengan `per-level: 0.3` dan `max: 9.0` baru mencapai
plafon di Lv. 54 — dengan `max-level: 50` nilainya berhenti di 7,8%. Plafon tetap ada supaya
server yang menaikkan `max-level` tidak tiba-tiba kehilangan keseimbangan.

**Kekuatan buff di Lv. 50 (bawaan)** — sengaja "ukuran awal game":

```
Serangan   damage-melee         15,0%  (fighting)
           damage-projectile    17,5%  (archery)
           crit-chance          10,3%  (fighting) / 11,7% (archery)  -> damage +50%
           mob-loot              7,8%  (fighting) /  5,8% (archery)  -> satu drop digandakan

Bertahan   damage-reduction     17,2%  (defense: 14% + 3,2%)
           block-chance          7,2%  (defense) /  5,9% (endurance) -> tahan 50%/40% damage
           environment-reduction 20%   (endurance) + 20% (agility) = 40% total, plafon 90%
           walk-speed           12,5%  (agility)

Kumpul     extra-drop           20,8%  (woodcutting: 15% + 5,8%) / 21,2% (farming: 15% + 6,2%)
           extra-catch          22,7%  (fishing: 17,5% + 5,2%)
           vanilla-xp           14,4%  (mining, XP vanilla ore)
           double-xp             4,8-6,3%  (mining/woodcutting/farming/fishing)

Potion     haste                 III   (mining: I di Lv.5, II di Lv.17, III di Lv.29)
           slow_falling          I     (agility, Lv.25)
           absorption            I-II  (vitality, Lv.15 -> II di Lv.35)
           fire_resistance       I     (endurance, Lv.40)
           health_boost          I     (recovery, Lv.40)

Pemulihan  regen-boost          19,2%  (recovery) + 5,8% (vitality)
           passive-heal          1,4 heart (vitality) + 0,6 heart (recovery) tiap 5 detik
```

Angka-angka ini **dikunci oleh dua gerbang otomatis**: `tools/verify_feature.py`
membandingkan 33 buff di `SkillSettings.defaultBuffs()` dengan `config.yml` (jenis,
unlock-level, per-level, max, power, potion, amplifier, causes), dan
`tools/selftest/BuffDefaultsTest.java` membaca buff bawaan yang **sudah terkompilasi** lalu
gagal bila ada buff > 20% di Lv. 50, nilai yang tidak monoton, buff milestone yang hilang,
atau total pengurangan damage yang membuat pemain kebal.

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

### 5a. Menu utama `/skill`

Menu 54 slot: 11 ikon skill (slot 10–16 dan 20–23), tombol **Top Skill** (slot 48), item
ringkasan (slot 49), tombol close (slot 53), sisanya filler. Tampilan diatur di
`src/main/resources/gui/skill.yml` (judul, material, slot, suara); seluruh kalimat diambil
dari `messages.yml` bagian `skill`. Slot di `gui/skill.yml` menang atas
`skills.list.<kunci>.slot`; bila dua skill disetel ke slot yang sama, salah satunya
dilewati (dicatat di log) dan menu tetap tampil.

- **Klik ikon skill** → membuka **menu progres** skill itu (v1.3.0; dulu langsung mencetak
  detail ke chat — detail chat kini jadi tombol di dalam menu progres).
- **Klik Top Skill** → menu ditutup lalu peringkat total dicetak ke chat.
- **Klik ringkasan/close** → bunyi klik / keluar.

Semua interaksi lain (shift-click, klik kanan, drag, double-click, swap-offhand, drop, ambil
dari creative) dibatalkan, jadi item tidak bisa dikeluarkan dari menu. Menu pemain ditutup
otomatis saat plugin dinonaktifkan (`SkillMenu.closeAll`, termasuk menu progres).

### 5b. Menu progres skill (baru di v1.3.0)

`gui/SkillProgressMenu.java` + `gui/SkillProgressMenuHolder.java`. Tata letak bawaan
(semuanya bisa diubah di `gui/skill.yml` bagian `progress:`):

```
baris 1        [4] ikon skill: level, bar, XP, XP menuju level berikutnya, cara menaikkan
baris 2   [11] [13] [15]  kartu buff (1-3 buff dipasang di tengah; lebih dari itu berjejer
                          dari slot 10). Buff AKTIF menampilkan nilainya; buff yang BELUM
                          terbuka menampilkan "buka di Lv. X" + penjelasan + pertumbuhannya.
baris 3   [18 .. 26]      bar kemajuan 9 petak kaca (hijau = terisi, abu = belum)
baris 4   [28] menuju Lv. berikutnya   [30] buff berikutnya   [32] level maksimum   [34] peringkat Anda
baris 6   [45] kembali ke /skill       [49] detail ke chat                          [53] close
```

Klik kartu buff mencetak penjelasan buff itu ke chat (jenis, nilai sekarang, pertumbuhan per
level, plafon dan level saat plafon tercapai). Penjagaan kliknya identik dengan menu utama:
pembatalan di prioritas `LOWEST`, tiga jalur penanda "ini menu kami" (holder, pendaftaran
inventory, pendaftaran pemilik), dan kunci anti klik-ganda.

Placeholder yang tersedia di pesan/GUI: `%player% %skill% %key% %level% %max% %next-level%
%xp% %xp-into% %xp-needed% %xp-to-next% %percent% %bar% %buff% %buff-desc% %source%
%unlock% %total-xp% %total-level% %buffs% %buff-count% %buffs-unlocked% %next-buff%
%next-buff-level%` (plus `%best%`, `%best-level%`, `%skills%`, `%file%` di ringkasan GUI;
`%buff% %buff-value% %buff-kind% %per% %max% %power% %value% %cap-level% %amp-next% %status%`
di kartu buff; `%rank% %rank-total% %scope% %count% %limit%` di peringkat).

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
| `EntityDeathEvent.getDrops` | buff loot mob ganda (fighting/archery) mati |
| `BlockBreakEvent.getExpToDrop/setExpToDrop` | buff XP vanilla (mining) mati |
| `PotionEffect` + `addPotionEffect/removePotionEffect` | semua buff potion mati |
| `PotionEffectType` per kunci efek (lewat refleksi: Registry → `getByKey` → `getByName` → field statis) | buff dengan efek itu dilewati; namanya muncul di `/skill check` (admin) |

Baris log yang muncul saat startup: `Skill: semua API buff tersedia (...)` bila lengkap,
atau `Skill: beberapa buff/XP dinonaktifkan otomatis karena API server tidak tersedia:`
diikuti rinciannya. `PlayerMoveEvent.getFrom()` sengaja tidak dipakai (jarak dilacak
sendiri, dengan penjaga teleport: pindah >10 blok atau ganti world mereset akumulasi).

Kecepatan jalan pemain **disimpan sebelum diubah** dan dikembalikan saat skill dimatikan,
pemain keluar, atau plugin dinonaktifkan. Bila plugin lain sudah mengubah kecepatan itu,
W2NSMP tidak menimpanya.

Drop tambahan diambil dari `Block.getDrops()` tanpa konteks tool, jadi tidak menggandakan
drop yang memang tidak akan keluar (mis. ore tanpa pickaxe) — tidak ada duplikasi item.
Loot mob ganda memakai daftar drop `EntityDeathEvent` apa adanya (satu entri disalin),
sehingga plugin loot lain tetap dihormati.

Efek potion **dicatat per pemain**: kunci efek yang dipasang skill disimpan, jadi saat level
turun, skill dimatikan, config berubah, atau `/w2nsmp reload` dijalankan, efek yang tidak
lagi berhak dimiliki pemain **dihapus** — tidak ada buff yang nyangkut. Semua efek dipasang
ulang tiap `skills.buffs.potion-refresh-seconds` detik.
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
  notify-extra-drop: false      # kabarkan saat buff drop/loot ganda berhasil
  notify-crit: true             # tulis serangan critical ke action bar
  top-limit: 10                 # jumlah baris /skill top (1..50)
  progress-bar-width: 10        # panjang %bar%
  curve: {base-xp: 25.0, exponent: 1.25, global-multiplier: 1.0}
  buffs:
    enabled: true               # matikan semua buff, level & XP tetap jalan
    potion-refresh-seconds: 10  # efek potion dipasang ulang tiap X detik
    heal-interval-seconds: 5    # interval pemulihan pasif (vitality/recovery)
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
      blocks: [...]             # untuk mining/woodcutting/farming
      buffs:                    # v1.3.0: SATU SKILL BOLEH PUNYA BEBERAPA BUFF
        1:
          kind: damage-melee    # lihat tabel jenis buff di §2b
          unlock-level: 1       # level saat buff mulai aktif
          per-level: 0.3        # pertambahan nilai tiap level
          max: 15.0             # plafon nilai (0 = tanpa plafon)
        2:
          kind: crit-chance
          unlock-level: 10
          per-level: 0.25
          max: 12.5
          power: 50.0           # khusus crit/block: persen damage tambahan/yang ditahan
        3:
          kind: potion
          unlock-level: 25
          potion: slow_falling  # nama efek potion (kind haste/potion)
          amplifier-every-levels: 25   # tingkat naik tiap X level
          amplifier-max: 0             # tingkat tertinggi (0 = tingkat I)
          # delay-seconds: 10          # khusus passive-heal: jeda setelah kena damage
          # causes: [FALL, FIRE]       # khusus environment-reduction
```

Nomor buff (`1:`, `2:`, `3:`, ...) hanya penentu urutan tampil; boleh loncat atau lebih dari
tiga. Bagian lama `buff:` (satu buff, config v1.2.x) **tetap dibaca** dan dipakai untuk buff
nomor 1 bila bagian `buffs:` tidak ada, jadi server yang naik versi tidak kehilangan setelan.
Bila keduanya ada, `buffs:` yang dipakai (dicatat di log).

Perintah `/skill` sendiri juga punya saklar di `commands.skill.enabled`.
Semua kunci di atas **benar-benar dibaca kode** (diperiksa otomatis oleh
`tools/verify_feature.py`: kunci config, kunci pesan, titik wiring, dan kesamaan 33 buff
bawaan antara kode dan `config.yml`).

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

## 8c. Riwayat perubahan

**v1.3.0 — multi-buff, menu progres, keseimbangan ulang, peringkat skill.**
- `skill/SkillBuff.java` (baru): satu buff = jenis + level pembukaan + pertumbuhan + plafon
  + kekuatan khusus + kunci potion + tingkat + jeda + daftar penyebab. `SkillSettings`
  sekarang menyimpan **daftar** buff; accessor lama (`buffValue`, `hasteAmplifier`,
  `healAmount`, `tracksCause`, `causes`) tetap ada dan menghitung dari buff utama, jadi
  pemanggil lama tidak berubah perilaku.
- `skill/SkillTop.java` (baru): peringkat total & per skill dari data `skills.yml` yang sudah
  ada (termasuk pemain offline), diurut level lalu XP; posisi pemain tersedia untuk GUI.
  Fitur `/top` lama (statistik) **tidak disentuh**.
- `gui/SkillProgressMenu.java` + holder (baru): menu progres yang dijelaskan di §5b.
- 6 jenis buff baru: `crit-chance`, `block-chance`, `mob-loot`, `vanilla-xp`, `double-xp`,
  `potion` (efek apa pun). `SkillApiProbe` kini bisa mencari efek potion berdasarkan kunci
  (bukan hanya Haste) dan memeriksa `EntityDeathEvent.getDrops` + `BlockBreakEvent.getExpToDrop`.
- Semua angka buff bawaan diturunkan agar level 50 terasa seperti progresi awal game
  (§2b), dikunci oleh `verify_feature.py` + `BuffDefaultsTest`.
- Perbaikan kecil yang ikut terbawa: heal pasif kini menjumlahkan **semua** skill yang punya
  `passive-heal` (dulu hanya vitality), dan nama efek penyebab damage dinormalisasi ke huruf
  besar sehingga `causes` dari config selalu cocok dengan `DamageCause.name()`.

**Masalah yang pernah terjadi dan sudah diperbaiki**

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

## 9. Naik versi dari 1.0.0 / 1.1.0 / 1.2.x

Config server Anda **tidak ditimpa**. Yang terjadi saat upgrade ke 1.3.0:

- `config.yml` lama (belum punya seksi `skills:`) tetap dipakai; setiap nilai skill diambil
  dari bawaan di kode (`SkillSettings.applyDefaults`), termasuk daftar blok
  mining/woodcutting/farming dan daftar `DamageCause` endurance. Pembacaan daftar dijaga
  `config.isList()`, jadi config lama tidak pernah membuat skill "mati diam-diam".
  `tools/verify_feature.py` memastikan bawaan di kode identik dengan `config.yml` bawaan.
- `messages.yml` lama tetap dipakai; kunci `skill.*` yang belum ada di berkas Anda jatuh ke
  `messages.yml` di dalam JAR (mekanisme `Messages.value()` yang sudah ada sejak 1.0.0),
  jadi pemain tidak pernah melihat kunci mentah.
- `gui/skill.yml` belum ada di server Anda → dibuat otomatis dari JAR saat pertama dipakai.
  Bila berkas itu **sudah ada** (dari 1.2.x) maka tidak ditimpa dan bagian `progress:`/`slots.top`
  tidak disisipkan otomatis (mekanisme `saveResource` memang tidak menimpa). Tidak masalah:
  setiap kunci menu progres punya nilai bawaan di kode, jadi menu tetap tampil normal. Untuk
  mendapat berkas GUI baru yang lengkap, hapus `plugins/W2NSMP/gui/skill.yml` lalu restart
  (kustomisasi tampilan Anda di berkas itu akan hilang).
- Config 1.2.x yang punya `skills.list.<kunci>.buff:` tetap jalan: angka lamanya dipakai untuk
  buff nomor 1, dan buff milestone bawaan 1.3.0 ikut aktif. Bila ingin menyusun ulang, salin
  seksi `buffs:` dari `config.yml` bawaan JAR.
- `skills.yml` (data XP/level pemain) **tidak berubah formatnya** — tidak ada migrasi.
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

**Bisakah satu skill punya lebih dari tiga buff?** Bisa. Tambahkan `4:`, `5:`, dst. di
`skills.list.<kunci>.buffs`. Menu progres menyusun kartunya otomatis (sampai 7 kartu).

**Bisakah buff-nya dibuat lebih kuat untuk server survival lama?** Bisa, dan itu memang
gunanya `per-level`/`max`: naikkan `max` (mis. `damage-melee` ke 50.0) atau naikkan
`skills.max-level`. Bawaan sengaja kecil karena level 50 diniatkan sebagai progresi awal game.

**Peringkat skill memakai data apa?** `plugins/W2NSMP/skills.yml` yang sudah ada — tidak ada
berkas baru, tidak ada tugas berkala baru. Pemain offline ikut terhitung; namanya memakai
nama terakhir yang tersimpan (atau nama sekarang bila sedang online).

**Bagaimana cara memberi hadiah level?** `/skill set <pemain> <skill> <level>` atau
`/skill add <pemain> <skill> <xp>` (butuh `w2nsmp.skill.admin`).
