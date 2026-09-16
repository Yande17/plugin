# Audit: Statistik, Leaderboard (/top), dan Scoreboard

Sumber: hasil decompile `W2NSMP-1.0.0.jar` (Vineflower 1.12) → `src/main/java`.
Semua nomor baris mengacu ke file di `src/main/java/me/w2n/w2nsmp/`.

Ringkasan: **3 bug berdampak besar (P0)**, **3 masalah performa (P1)**, **5 penyempurnaan (P2)**.

---

## P0 — Bug yang langsung terlihat pemain

### P0-1. Sidebar mematikan nametag uang (dua fitur plugin saling bertabrakan)
- `scoreboard/ScoreboardService.java:218` membuat scoreboard **baru per pemain**
  (`getNewScoreboard()`), lalu `:225` memasangnya dengan `player.setScoreboard(...)`.
- `nametag/NametagService.java:182,189` mendaftarkan team `w2nmoney` **hanya di main scoreboard**.
- Akibat: begitu `scoreboard.enabled: true` (default), setiap pemain memakai scoreboard pribadi,
  sehingga prefix uang di atas nama **tidak tampil sama sekali**. Team/prefix dari plugin lain
  yang berada di main scoreboard (mis. prefix LuckPerms) juga ikut tidak terlihat oleh pemain
  yang sidebar-nya aktif.
- Perbaikan: satu sumber kebenaran scoreboard — `NametagService` menyuntik team-nya ke
  scoreboard sidebar milik pemain (bila aktif), dan tetap ke main scoreboard bila sidebar mati.
  Perlu juga koordinasi urutan `apply()` (ScoreboardListener memanggil keduanya berurutan).

### P0-2. Playtime tidak bertambah selama pemain online
- `stats/StatisticsService.java:266` `flushPlaytime()` **hanya dipanggil dari `buildTop()`**
  (`:467`). Tidak ada task periodik (bandingkan `highestMoneyTask` yang punya timer, `:130`).
- Akibat:
  - `%playtime%` di sidebar, `/profile`, dan `/top playtime` **tidak naik** selama sesi bermain;
    angka baru berubah saat leaderboard dibangun atau saat pemain logout.
  - Playtime sesi berjalan hilang total bila server crash / `kill -9` (hanya `endSession()`
    di quit/shutdown normal yang menyimpan, `:245`).
- Perbaikan: task flush berkala (`statistics.playtime-flush-seconds`, default 60) + flush
  throttled saat placeholder dibaca, sehingga data selalu segar dan tahan crash.

### P0-3. Kolom `last-seen` selalu 0
- `PlayerStats.lastSeen(...)` tidak pernah diisi waktu saat ini. Pemanggilnya hanya menyalin
  nilai lama: `StatisticsService.java:676` (snapshot), `:718` (baca SQLite),
  `StatsStorage.java:81` (baca YAML).
- Akibat: `last-seen` di `statistics.yml` dan `last_seen` di `w2nsmp.db` selalu `0` — data
  "terakhir dilihat" tidak bisa dipakai untuk apa pun (mis. leaderboard pemain aktif, pembersihan
  data pemain lama).
- Perbaikan: isi `lastSeen(now)` di `onJoin`, `onQuit`, dan saat flush/autosave.

---

## P1 — Performa (berdampak saat pemain banyak)

### P1-4. Leaderboard dibangun ulang di main thread terlalu sering
- `bumpDataVersion()` dipanggil pada **setiap** penambahan statistik (`:100,195,207,233,249,262,276,411,423`).
- Cache leaderboard memakai TTL **1 detik** (`:53`) *dan* langsung dibuang bila `dataVersion`
  berubah (`:435-441`). Karena 1 blok ditambang saja menaikkan `dataVersion`, cache praktis
  tidak pernah bertahan.
- `buildTop()` (`:465-520`) memindai seluruh cache (maks 10.000 pemain) dan memanggil
  `value()` per pemain. Untuk kategori **MONEY** jalurnya
  `money()` → `economy.balance(Bukkit.getOfflinePlayer(uuid))` (`:396`) = **lookup Vault/Essentials
  per pemain offline, sinkron di main thread**.
- `scoreboardPlaceholders()` memanggil `rankOf()` untuk tiap kategori yang dipakai sidebar (`:579`),
  dan sidebar di-refresh tiap 20 tick untuk **setiap** pemain online.
- Akibat: dengan sidebar berisi `%rank-money%` dan ratusan pemain terdaftar, server bisa
  membaca saldo ribuan pemain offline setiap detik → TPS drop.
- Perbaikan: cache berbasis TTL yang **tidak** di-invalidasi per-increment
  (`leaderboard.cache-seconds`, default 15), pembangunan leaderboard dipindah ke thread async
  (snapshot data → sortir → simpan hasil), dan saldo untuk kategori money diambil dari snapshot
  berkala, bukan per-request.

### P1-5. Batas 10.000 pemain memakai urutan `HashMap` (nondeterministik)
- `:469-475`: iterasi `cache.entrySet()` lalu `break` ketika `ids.size() >= 10000`.
- Akibat: bila data pemain > 10.000, **siapa yang masuk leaderboard bergantung pada urutan hash**
  — pemain peringkat atas bisa hilang tanpa peringatan, dan hasilnya berubah-ubah antar restart.
- `:480`: `ids.contains(...)` di dalam loop pemain online → O(n·m).
- Perbaikan: `LinkedHashSet` + seleksi top-N lewat `PriorityQueue` (tidak perlu memuat semua).

### P1-6. Autosave berat dan boros
- `snapshot()` (`:689`) **selalu** membuat string YAML (`storage.serializeYaml(copy)`) walaupun
  mode penyimpanan SQLITE, dan melakukan deep copy seluruh cache **di main thread**.
- `persistSqlite()` (`:743`) melakukan `DELETE FROM w2nsmp_stats` lalu INSERT ulang **semua**
  baris setiap kali menyimpan (tiap 5 menit).
- Perbaikan: serialize YAML hanya bila mode YAML; pindahkan pembuatan snapshot ke async;
  pakai `INSERT OR REPLACE` per baris yang berubah (tanpa `DELETE` massal).

---

## P2 — Penyempurnaan UX & kualitas data

### P2-7. `/top chat` tidak punya halaman
`command/TopCommand.java:117-140` menampilkan header `halaman %page%/%pages%`
(`messages.yml:262`) tetapi selalu halaman 1 dan tidak menerima argumen halaman.
Perbaikan: `/top <kategori> chat [halaman]` + tab-complete + tombol klik halaman berikutnya.

### P2-8. Judul sidebar hanya mendukung 3 placeholder
`ScoreboardService.titleFor()` (`:436`) hanya mengganti `player`, `world`, `online`, padahal
`neededTokens` sudah mengumpulkan token dari judul (`:61`). Judul seperti `&a%money%` tidak terisi.
Perbaikan: judul memakai set placeholder penuh.

### P2-9. `%rank-*%` menampilkan `0` untuk pemain yang belum masuk peringkat
`rankOf()` mengembalikan `0` bila tidak ditemukan (`:521-529`).
Perbaikan: placeholder `unranked` (mis. `-`) yang bisa dikonfigurasi di `messages.yml`.

### P2-10. `findUniqueId()` memakai API blocking yang deprecated
`:344` `Bukkit.getOfflinePlayer(String)` — lookup nama→UUID bisa memicu I/O di main thread.
Perbaikan: `getOfflinePlayerIfCached(...)` + jalur async bila perlu.

### P2-11. Cakupan statistik & periode
- `StatType` (23 kategori) belum melacak: jarak tempuh, ikan ditangkap, item di-craft, makanan
  dimakan, kematian oleh pemain tertentu (untuk K/D), dsb.
- `StatsListener.onEntityDeath` tidak memfilter gamemode Creative/Spectator dan tidak
  membedakan mob hasil spawner → statistik `mobs` mudah di-farm.
- Tidak ada leaderboard periodik (mingguan/bulanan) → kategori seperti `money-earned`
  selalu didominasi pemain lama.

---

## Paket pengerjaan yang diusulkan

| Paket | Isi | File yang disentuh | Risiko |
|---|---|---|---|
| **A. Bug kritis** | P0-1, P0-2, P0-3 + P2-8, P2-9 | `ScoreboardService`, `NametagService`, `StatisticsService`, `ScoreboardListener`, `ConfigManager`, `messages.yml`, `config.yml` | Rendah — perilaku lama tetap, hanya memperbaiki yang rusak |
| **B. Performa leaderboard** | P1-4, P1-5, P1-6, P2-10 | `StatisticsService`, `StatsStorage`, `ConfigManager`, `config.yml` | Sedang — menyentuh jalur simpan/baca data (data lama tetap terbaca) |
| **C. UX /top** | P2-7 (+ tombol klik, tab-complete) | `TopCommand`, `messages.yml` | Rendah |
| **D. Fitur statistik baru** | P2-11: StatType baru, filter gamemode/spawner, leaderboard mingguan | `StatType`, `TopCategory`, listener baru, `StatsStorage`, `config.yml`, `messages.yml` | Sedang — menambah skema data (backward compatible) |

Catatan: semua paket tetap backward compatible dengan `statistics.yml` / `w2nsmp.db` yang sudah ada.
