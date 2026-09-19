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
 * Uang & bounty di atas kepala pemain (v1.7.0, PHASE 3).
 *
 * <p>Dua baris display yang independen, keduanya entity {@link TextDisplay} yang menumpang
 * (passenger) pemain:
 * <ul>
 *   <li><b>MONEY</b> - saldo pemain, selalu ada selama fitur aktif dan pemain layak tampil;</li>
 *   <li><b>BOUNTY</b> - harga buruan pemain, HANYA ada bila bounty-nya &gt; 0 (data dari
 *       BountyService existing; tidak ada sistem bounty baru). Tampil di atas baris uang.</li>
 * </ul>
 *
 * <p>Visibilitas per-VIEWER (hasil PHASE 2 FIX, kini untuk dua toggle terpisah): setting
 * "Money Display" dan "Bounty Display" di /setting adalah preferensi LAYAR penonton -
 * OFF berarti viewer itu tidak melihat baris tersebut pada SIAPA PUN; pemain lain dan data
 * (saldo/bounty) sama sekali tidak terpengaruh. Keduanya independen: Money ON + Bounty OFF
 * menampilkan hanya baris uang, dan sebaliknya. Implementasi lewat
 * {@link Player#hideEntity}/{@link Player#showEntity} per entity.
 *
 * <p>Nama pemain tidak pernah disentuh - tidak ada team/suffix/prefix (hasil PHASE 1;
 * team lama tetap dibersihkan tiap start). Display {@code setPersistent(false)} sehingga
 * restart/crash tidak meninggalkan entity yatim; teks hanya dikirim ulang bila berubah;
 * update instan saat saldo/bounty berubah lewat plugin, task berkala menjaring sisanya.
 *
 * <p>Server tanpa API TextDisplay (pra-1.19.4): fitur mati sendiri dengan satu baris log.
 */
public final class NametagService {
   /** Nama team lama versi <= 1.5.x (suffix uang di nama) - kini hanya dibersihkan. */
   public static final String TEAM_NAME = "w2nmoney";

   /** Satu baris display (money atau bounty): entity per owner + cache teks terakhir. */
   private static final class Line {
      final Map<UUID, TextDisplay> displays = new HashMap<>();
      final Map<UUID, String> lastText = new HashMap<>();
   }

   private final W2NSMP plugin;
   private final Line money = new Line();
   private final Line bounty = new Line();
   private BukkitTask task;
   private boolean apiSupported;

   public NametagService(W2NSMP plugin) {
      this.plugin = plugin;
   }

   public void load() {
      this.apiSupported = probeApi();
      if (!this.apiSupported && this.anyEnabled()) {
         this.plugin.getLogger().warning("Money display: server API has no TextDisplay (needs 1.19.4+) - feature disabled.");
      }

      // PHASE 1: sisa team suffix lama (scoreboard.dat) tetap dibersihkan di setiap start.
      this.cleanLegacyTeams();

      if (this.anyEnabled()) {
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
      if (this.anyEnabled() && this.apiSupported) {
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

   /** Kompatibilitas lama: gate config baris uang. */
   public boolean enabled() {
      return this.plugin.config().nametagMoneyEnabled();
   }

   /** v1.7.0: gate baris bounty (config + service bounty existing hidup). */
   public boolean bountyEnabled() {
      return this.plugin.config().nametagBountyEnabled() && this.plugin.bounty() != null && this.plugin.bounty().enabled();
   }

   /** Ada baris apa pun yang aktif? (dipakai listener & task) */
   public boolean anyEnabled() {
      return this.enabled() || this.bountyEnabled();
   }

   public boolean taskRunning() {
      return this.task != null && !this.task.isCancelled();
   }

   /** Jumlah pemain yang sedang membawa display uang. */
   public int activeCount() {
      return this.money.displays.size();
   }

   /** v1.7.0: jumlah pemain yang sedang membawa display bounty. */
   public int bountyCount() {
      return this.bounty.displays.size();
   }

   /** Kompatibilitas debug lama: jumlah scoreboard yang dipindai pembersih team legacy. */
   public int targetCount() {
      return this.legacyTargets().size();
   }

   /**
    * Pasang (atau perbarui) kedua baris display milik OWNER ini. Idempoten. Setting pribadi
    * owner TIDAK dicek di sini - setting hanya memengaruhi apa yang owner LIHAT
    * (lihat {@link #updateViewer(Player)}).
    */
   public boolean apply(Player player) {
      if (player == null || !player.isOnline()) {
         return false;
      }

      if (!this.apiSupported || !this.shouldShow(player)) {
         this.remove(player);
         return false;
      }

      boolean any = false;
      if (this.enabled()) {
         any = this.applyLine(player, false);
      } else {
         this.removeLine(player, this.money);
      }

      if (this.bountyEnabled() && this.bountyValue(player) > 0L) {
         any |= this.applyLine(player, true);
      } else {
         this.removeLine(player, this.bounty);
      }

      return any;
   }

   /** Perbarui teks kedua baris (hanya bila berubah) dan pasang ulang display yang terlepas. */
   public void refresh(Player player) {
      if (player == null || !this.apiSupported || !this.anyEnabled()) {
         return;
      }

      if (!player.isOnline() || !this.shouldShow(player)) {
         this.remove(player);
         return;
      }

      this.apply(player);
   }

   /**
    * Terapkan setting milik VIEWER ke layarnya sendiri, per baris: Money OFF = sembunyikan
    * semua baris uang, Bounty OFF = sembunyikan semua baris bounty (independen). Pemain
    * lain tidak terpengaruh. Dipanggil saat toggle /setting dan saat viewer join.
    */
   public void updateViewer(Player viewer) {
      if (viewer == null || !viewer.isOnline() || !this.apiSupported) {
         return;
      }

      boolean showMoney = this.viewerMoneyEnabled(viewer);
      boolean showBounty = this.viewerBountyEnabled(viewer);

      for (TextDisplay display : this.money.displays.values()) {
         if (display != null && display.isValid()) {
            this.setVisibleFor(viewer, display, showMoney);
         }
      }

      for (TextDisplay display : this.bounty.displays.values()) {
         if (display != null && display.isValid()) {
            this.setVisibleFor(viewer, display, showBounty);
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

   /** Cabut kedua baris display pemain ini (quit, mati, sneak, spectator, fitur mati). */
   public void remove(Player player) {
      if (player != null) {
         this.removeLine(player, this.money);
         this.removeLine(player, this.bounty);
      }
   }

   /** Cabut semua display + bersihkan team suffix legacy dari semua scoreboard. */
   public void removeAll() {
      for (Line line : new Line[]{this.money, this.bounty}) {
         for (TextDisplay display : line.displays.values()) {
            if (display != null) {
               try {
                  display.remove();
               } catch (Throwable ignored) {
               }
            }
         }

         line.displays.clear();
         line.lastText.clear();
      }

      this.cleanLegacyTeams();
   }

   /** Teks uang pemain (dipakai display; tersedia juga untuk debug). */
   public Component text(Player player) {
      return this.plugin.messages().colored(this.moneyText(player));
   }

   public boolean managed(UUID uniqueId) {
      return uniqueId != null && this.money.displays.containsKey(uniqueId);
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

   /** Pasang/perbarui satu baris; kembalikan true bila baris terpasang. */
   private boolean applyLine(Player player, boolean isBounty) {
      Line line = isBounty ? this.bounty : this.money;
      TextDisplay display = line.displays.get(player.getUniqueId());
      if (display != null && display.isValid()) {
         this.updateText(player, display, isBounty);
         this.remount(player, display, isBounty);
         return true;
      }

      return this.spawnLine(player, isBounty);
   }

   private boolean spawnLine(Player player, boolean isBounty) {
      Line line = isBounty ? this.bounty : this.money;

      try {
         TextDisplay display = player.getWorld().spawn(player.getLocation(), TextDisplay.class);
         display.setPersistent(false);
         display.setBillboard(Display.Billboard.CENTER);
         display.setShadowed(true);
         display.setSeeThrough(false);
         float height = (float) this.plugin.config().nametagMoneyHeight();
         if (isBounty) {
            height += (float) this.plugin.config().nametagBountyOffset();
         }

         display.setTransformation(new Transformation(
            new Vector3f(0.0F, height, 0.0F), new AxisAngle4f(), new Vector3f(1.0F, 1.0F, 1.0F), new AxisAngle4f()));

         UUID id = player.getUniqueId();
         line.lastText.remove(id);
         line.displays.put(id, display);
         this.updateText(player, display, isBounty);

         // Entity BARU: terapkan preferensi setiap viewer online (yang OFF untuk baris ini
         // tidak boleh melihatnya). hideEntity per-entity, jadi wajib diulang tiap spawn.
         for (Player viewer : Bukkit.getOnlinePlayers()) {
            boolean show = isBounty ? this.viewerBountyEnabled(viewer) : this.viewerMoneyEnabled(viewer);
            if (!show) {
               this.setVisibleFor(viewer, display, false);
            }
         }

         if (!player.addPassenger(display)) {
            // gagal menumpang (kondisi aneh, mis. pemain sedang mati) - jangan tinggalkan entity
            this.removeLine(player, line);
            return false;
         }

         return true;
      } catch (Throwable throwable) {
         this.plugin.getLogger().warning("Money display: failed to spawn for " + player.getName() + ": " + throwable);
         this.removeLine(player, line);
         return false;
      }
   }

   private void updateText(Player player, TextDisplay display, boolean isBounty) {
      Line line = isBounty ? this.bounty : this.money;
      String text = isBounty ? this.bountyText(player) : this.moneyText(player);
      UUID id = player.getUniqueId();
      if (!text.equals(line.lastText.get(id))) {
         try {
            display.text(this.plugin.messages().colored(text));
            line.lastText.put(id, text);
         } catch (Throwable throwable) {
            this.plugin.debug("Money display: failed to update text of " + player.getName() + " (" + throwable + ").");
         }
      }
   }

   /** Teleport/respawn melepas passenger - naikkan lagi bila display sudah tidak menumpang. */
   private void remount(Player player, TextDisplay display, boolean isBounty) {
      try {
         if (display.getVehicle() == null) {
            if (!display.getWorld().equals(player.getWorld())) {
               // beda dunia: entity lama tidak bisa dipindah dengan aman - buat baru
               this.removeLine(player, isBounty ? this.bounty : this.money);
               this.spawnLine(player, isBounty);
               return;
            }

            display.teleport(player.getLocation());
            player.addPassenger(display);
         }
      } catch (Throwable throwable) {
         this.plugin.debug("Money display: remount failed for " + player.getName() + " (" + throwable + ").");
      }
   }

   private void removeLine(Player player, Line line) {
      UUID id = player.getUniqueId();
      line.lastText.remove(id);
      TextDisplay display = line.displays.remove(id);
      if (display != null) {
         try {
            display.remove();
         } catch (Throwable throwable) {
            this.plugin.debug("Money display: failed to remove entity of " + player.getName() + " (" + throwable + ").");
         }
      }
   }

   private String moneyText(Player player) {
      String balance = this.plugin.economy() != null && this.plugin.economy().isEnabled()
         ? this.plugin.economy().format(this.plugin.economy().balance(player))
         : "-";
      return this.plugin.messages().apply(this.plugin.config().nametagMoneyFormat(), "balance", balance, "player", player.getName());
   }

   private String bountyText(Player player) {
      long value = this.bountyValue(player);
      String formatted = this.plugin.economy() != null && this.plugin.economy().isEnabled()
         ? this.plugin.economy().format(value)
         : Long.toString(value);
      return this.plugin.messages().apply(this.plugin.config().nametagBountyFormat(), "bounty", formatted, "player", player.getName());
   }

   private long bountyValue(Player player) {
      return this.plugin.bounty() == null ? 0L : this.plugin.bounty().bounty(player.getUniqueId());
   }

   // ------------------------------------------------------------------ //
   // Sisi VIEWER
   // ------------------------------------------------------------------ //

   /** Setting Money milik viewer: apakah DIA ingin melihat baris uang di layarnya. */
   private boolean viewerMoneyEnabled(Player viewer) {
      PlayerSettingsService settings = this.plugin.settings();
      return settings == null || settings.nametagMoney(viewer);
   }

   /** Setting Bounty milik viewer: apakah DIA ingin melihat baris bounty di layarnya. */
   private boolean viewerBountyEnabled(Player viewer) {
      PlayerSettingsService settings = this.plugin.settings();
      return settings == null || settings.bountyDisplay(viewer);
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
