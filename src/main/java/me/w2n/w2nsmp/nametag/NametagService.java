package me.w2n.w2nsmp.nametag;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.player.PlayerSettingsService;
import me.w2n.w2nsmp.scoreboard.ScoreboardService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Uang di atas kepala pemain (v1.6.2, PHASE 2 FIX - visibilitas per-VIEWER).
 *
 * <p>Dua peran yang dipisahkan tegas:
 * <ul>
 *   <li><b>Owner</b> - setiap pemain online membawa satu entity {@link TextDisplay} berisi
 *       saldonya sendiri (passenger; ikut bergerak mulus tanpa task per tick). Display owner
 *       TIDAK dikontrol oleh setting owner - selalu ada selama fitur aktif dan pemain layak
 *       tampil (hidup, tidak sneak, bukan spectator).</li>
 *   <li><b>Viewer</b> - setting "Money Display" di /setting adalah preferensi LAYAR penonton:
 *       OFF berarti viewer itu tidak melihat display uang SIAPA PUN (termasuk miliknya);
 *       pemain lain sama sekali tidak terpengaruh. Diimplementasikan lewat
 *       {@link Player#hideEntity}/{@link Player#showEntity} yang memang per-pemain.</li>
 * </ul>
 *
 * <p>Nama pemain tidak pernah disentuh - tidak ada team, suffix, prefix, atau scoreboard
 * yang diubah (hasil PHASE 1 dipertahankan; sisa team lama tetap dibersihkan tiap start).
 *
 * <p>Keamanan data & anti-sampah:
 * <ul>
 *   <li>display {@code setPersistent(false)} - tidak pernah tersimpan ke chunk, restart/crash
 *       tidak meninggalkan entity yatim;</li>
 *   <li>satu owner = maksimal satu display ({@link #displays}); apply idempoten;</li>
 *   <li>teks hanya dikirim ulang bila berubah ({@link #lastText});</li>
 *   <li>update saldo instan lewat EconomyManager -> {@link #refresh(Player)}; task berkala
 *       ({@code update-seconds}) menjaring perubahan plugin luar + memasang ulang display
 *       yang terlepas.</li>
 * </ul>
 *
 * <p>Server tanpa API TextDisplay (pra-1.19.4): fitur mati sendiri dengan satu baris log.
 */
public final class NametagService {
   /** Nama team lama versi <= 1.5.x (suffix uang di nama) - kini hanya dibersihkan. */
   public static final String TEAM_NAME = "w2nmoney";

   private final W2NSMP plugin;
   private final Map<UUID, TextDisplay> displays = new HashMap<>();
   private final Map<UUID, String> lastText = new HashMap<>();
   private BukkitTask task;
   private boolean apiSupported;

   public NametagService(W2NSMP plugin) {
      this.plugin = plugin;
   }

   public void load() {
      this.apiSupported = probeApi();
      if (!this.apiSupported && this.enabled()) {
         this.plugin.getLogger().warning("Money display: server API has no TextDisplay (needs 1.19.4+) - feature disabled.");
      }

      // PHASE 1: sisa team suffix lama (scoreboard.dat) tetap dibersihkan di setiap start.
      this.cleanLegacyTeams();

      if (this.enabled()) {
         for (Player player : Bukkit.getOnlinePlayers()) {
            this.apply(player);
         }
      }
   }

   public void reload() {
      this.stopTasks();
      this.removeAll();
      this.load();
      this.startTasks();
   }

   public void startTasks() {
      this.stopTasks();
      if (this.enabled() && this.apiSupported) {
         int seconds = this.plugin.config().nametagMoneyUpdateSeconds();
         if (seconds > 0) {
            long ticks = seconds * 20L;
            this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
               for (Player player : Bukkit.getOnlinePlayers()) {
                  this.refresh(player);
               }
            }, ticks, ticks);
         }
      }
   }

   public void stopTasks() {
      if (this.task != null) {
         this.task.cancel();
         this.task = null;
      }
   }

   public void shutdown() {
      this.stopTasks();
      this.removeAll();
   }

   public boolean enabled() {
      return this.plugin.config().nametagMoneyEnabled();
   }

   public boolean taskRunning() {
      return this.task != null && !this.task.isCancelled();
   }

   /** Jumlah pemain yang sedang membawa display uang. */
   public int activeCount() {
      return this.displays.size();
   }

   /** Kompatibilitas debug lama: jumlah scoreboard yang dipindai pembersih team legacy. */
   public int targetCount() {
      return this.legacyTargets().size();
   }

   /**
    * Pasang (atau perbarui) display uang milik OWNER ini. Idempoten. Setting pribadi owner
    * TIDAK dicek di sini - setting hanya memengaruhi apa yang owner LIHAT, bukan display
    * miliknya (lihat {@link #updateViewer(Player)}).
    */
   public boolean apply(Player player) {
      if (player == null || !player.isOnline()) {
         return false;
      }

      if (!this.enabled() || !this.apiSupported || !this.shouldShow(player)) {
         this.remove(player);
         return false;
      }

      UUID id = player.getUniqueId();
      TextDisplay display = this.displays.get(id);
      if (display != null && display.isValid()) {
         this.updateText(player, display);
         this.remount(player, display);
         return true;
      }

      return this.spawnDisplay(player);
   }

   /** Perbarui teks (hanya bila berubah) dan pasang ulang display yang terlepas. */
   public void refresh(Player player) {
      if (player == null || !this.enabled() || !this.apiSupported) {
         return;
      }

      TextDisplay display = this.displays.get(player.getUniqueId());
      if (display == null || !display.isValid()) {
         if (player.isOnline() && this.shouldShow(player)) {
            this.apply(player);
         }
      } else if (!this.shouldShow(player)) {
         this.remove(player);
      } else {
         this.updateText(player, display);
         this.remount(player, display);
      }
   }

   /**
    * Terapkan setting Money milik VIEWER ke layarnya sendiri: OFF = sembunyikan semua
    * display uang (termasuk miliknya), ON = tampilkan semua. Pemain lain tidak terpengaruh.
    * Dipanggil saat toggle /setting dan saat viewer join.
    */
   public void updateViewer(Player viewer) {
      if (viewer == null || !viewer.isOnline() || !this.apiSupported) {
         return;
      }

      boolean show = this.viewerEnabled(viewer);

      for (TextDisplay display : this.displays.values()) {
         if (display != null && display.isValid()) {
            this.setVisibleFor(viewer, display, show);
         }
      }
   }

   /** Scoreboard pemain berganti: pastikan scoreboard barunya bebas team suffix lama. */
   public void onScoreboardChanged(Player viewer) {
      if (viewer == null) {
         return;
      }

      ScoreboardService service = this.plugin.scoreboard();
      Scoreboard sidebar = service == null ? null : service.activeScoreboard(viewer);
      this.cleanLegacyTeam(sidebar);
   }

   /** Cabut display uang milik pemain ini (quit, mati, sneak, spectator, fitur mati). */
   public void remove(Player player) {
      if (player == null) {
         return;
      }

      UUID id = player.getUniqueId();
      this.lastText.remove(id);
      TextDisplay display = this.displays.remove(id);
      if (display != null) {
         try {
            display.remove();
         } catch (Throwable throwable) {
            this.plugin.debug("Money display: failed to remove entity of " + player.getName() + " (" + throwable + ").");
         }
      }
   }

   /** Cabut semua display + bersihkan team suffix legacy dari semua scoreboard. */
   public void removeAll() {
      for (TextDisplay display : this.displays.values()) {
         if (display != null) {
            try {
               display.remove();
            } catch (Throwable ignored) {
            }
         }
      }

      this.displays.clear();
      this.lastText.clear();
      this.cleanLegacyTeams();
   }

   /** Teks uang pemain (dipakai display; tersedia juga untuk debug). */
   public Component text(Player player) {
      return this.plugin.messages().colored(this.textString(player));
   }

   public boolean managed(UUID uniqueId) {
      return uniqueId != null && this.displays.containsKey(uniqueId);
   }

   // ------------------------------------------------------------------ //
   // Display di atas kepala (sisi OWNER)
   // ------------------------------------------------------------------ //

   /** Apakah display OWNER ini layak ada sekarang (hidup, tidak sneak, bukan spectator). */
   private boolean shouldShow(Player player) {
      if (player.isDead() || player.isSneaking()) {
         return false;
      }

      try {
         return player.getGameMode() != GameMode.SPECTATOR;
      } catch (Throwable ignored) {
         return true;
      }
   }

   private boolean spawnDisplay(Player player) {
      try {
         TextDisplay display = player.getWorld().spawn(player.getLocation(), TextDisplay.class);
         display.setPersistent(false);
         display.setBillboard(Display.Billboard.CENTER);
         display.setShadowed(true);
         display.setSeeThrough(false);
         float height = (float) this.plugin.config().nametagMoneyHeight();
         display.setTransformation(new Transformation(
            new Vector3f(0.0F, height, 0.0F), new AxisAngle4f(), new Vector3f(1.0F, 1.0F, 1.0F), new AxisAngle4f()));

         UUID id = player.getUniqueId();
         this.lastText.remove(id);
         this.displays.put(id, display);
         this.updateText(player, display);

         // Entity BARU: terapkan preferensi setiap viewer online (yang OFF tidak boleh
         // melihatnya). hideEntity bersifat per-entity, jadi wajib diulang tiap respawn.
         for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!this.viewerEnabled(viewer)) {
               this.setVisibleFor(viewer, display, false);
            }
         }

         if (!player.addPassenger(display)) {
            // gagal menumpang (kondisi aneh, mis. pemain sedang mati) - jangan tinggalkan entity
            this.remove(player);
            return false;
         }

         return true;
      } catch (Throwable throwable) {
         this.plugin.getLogger().warning("Money display: failed to spawn for " + player.getName() + ": " + throwable);
         this.remove(player);
         return false;
      }
   }

   private void updateText(Player player, TextDisplay display) {
      String text = this.textString(player);
      UUID id = player.getUniqueId();
      if (!text.equals(this.lastText.get(id))) {
         try {
            display.text(this.plugin.messages().colored(text));
            this.lastText.put(id, text);
         } catch (Throwable throwable) {
            this.plugin.debug("Money display: failed to update text of " + player.getName() + " (" + throwable + ").");
         }
      }
   }

   /** Teleport/respawn melepas passenger - naikkan lagi bila display sudah tidak menumpang. */
   private void remount(Player player, TextDisplay display) {
      try {
         if (display.getVehicle() == null) {
            if (!display.getWorld().equals(player.getWorld())) {
               // beda dunia: entity lama tidak bisa dipindah dengan aman - buat baru
               this.remove(player);
               this.spawnDisplay(player);
               return;
            }

            display.teleport(player.getLocation());
            player.addPassenger(display);
         }
      } catch (Throwable throwable) {
         this.plugin.debug("Money display: remount failed for " + player.getName() + " (" + throwable + ").");
      }
   }

   private String textString(Player player) {
      String balance = this.plugin.economy() != null && this.plugin.economy().isEnabled()
         ? this.plugin.economy().format(this.plugin.economy().balance(player))
         : "-";
      return this.plugin.messages().apply(this.plugin.config().nametagMoneyFormat(), "balance", balance, "player", player.getName());
   }

   // ------------------------------------------------------------------ //
   // Sisi VIEWER
   // ------------------------------------------------------------------ //

   /** Setting Money milik viewer: apakah DIA ingin melihat display uang di layarnya. */
   private boolean viewerEnabled(Player viewer) {
      PlayerSettingsService settings = this.plugin.settings();
      return settings == null || settings.nametagMoney(viewer);
   }

   /** hide/showEntity hanya memengaruhi layar viewer ini - inti visibilitas per-viewer. */
   private void setVisibleFor(Player viewer, TextDisplay display, boolean visible) {
      try {
         if (visible) {
            viewer.showEntity(this.plugin, display);
         } else {
            viewer.hideEntity(this.plugin, display);
         }
      } catch (Throwable throwable) {
         this.plugin.debug("Money display: hide/show failed for viewer " + viewer.getName() + " (" + throwable + ").");
      }
   }

   private static boolean probeApi() {
      try {
         Class.forName("org.bukkit.entity.TextDisplay");
         Player.class.getMethod("hideEntity", org.bukkit.plugin.Plugin.class, org.bukkit.entity.Entity.class);
         return true;
      } catch (Throwable throwable) {
         return false;
      }
   }

   // ------------------------------------------------------------------ //
   // Pembersih team suffix lama (PHASE 1)
   // ------------------------------------------------------------------ //

   private void cleanLegacyTeams() {
      for (Scoreboard scoreboard : new LinkedHashSet<>(this.legacyTargets().values())) {
         this.cleanLegacyTeam(scoreboard);
      }
   }

   private void cleanLegacyTeam(Scoreboard scoreboard) {
      if (scoreboard == null) {
         return;
      }

      try {
         Team team = scoreboard.getTeam(TEAM_NAME);
         if (team != null) {
            for (String entry : Set.copyOf(team.getEntries())) {
               team.removeEntry(entry);
            }

            team.unregister();
         }
      } catch (Throwable throwable) {
         this.plugin.debug("Money display: failed to clean legacy team (" + throwable + ").");
      }
   }

   private Map<String, Scoreboard> legacyTargets() {
      Map<String, Scoreboard> result = new LinkedHashMap<>();
      Scoreboard main = Bukkit.getScoreboardManager() == null ? null : Bukkit.getScoreboardManager().getMainScoreboard();
      if (main != null) {
         result.put("main", main);
      }

      ScoreboardService service = this.plugin.scoreboard();
      if (service != null) {
         for (Player viewer : Bukkit.getOnlinePlayers()) {
            Scoreboard sidebar = service.activeScoreboard(viewer);
            if (sidebar != null) {
               result.put(viewer.getUniqueId().toString(), sidebar);
            }
         }
      }

      return result;
   }
}
