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
 * Uang di atas kepala pemain (v1.6.1, PHASE 2).
 *
 * <p>Setiap pemain (yang menyalakannya lewat /setting) membawa satu entity
 * {@link TextDisplay} sebagai <em>passenger</em>: teks saldo melayang di atas nametag dan
 * ikut bergerak bersama pemain tanpa task per tick. Nama pemain sendiri TIDAK disentuh -
 * tidak ada team, suffix, prefix, atau scoreboard yang diubah (hasil PHASE 1 dipertahankan).
 *
 * <p>Keamanan data & anti-sampah:
 * <ul>
 *   <li>display dibuat {@code setPersistent(false)} - tidak pernah tersimpan ke chunk,
 *       jadi restart/crash tidak meninggalkan entity yatim;</li>
 *   <li>satu pemain = maksimal satu display ({@link #displays}); apply bersifat idempoten
 *       (dipanggil dua kali hanya memperbarui teks);</li>
 *   <li>teks hanya dikirim ulang bila berubah ({@link #lastText}) - tidak membanjiri jaringan;</li>
 *   <li>update saldo instan lewat {@link me.w2n.w2nsmp.economy.EconomyManager} yang memanggil
 *       {@link #refresh(Player)}; task berkala ({@code update-seconds}) hanya menjaring
 *       perubahan dari plugin luar (Vault) dan memasang ulang display yang terlepas.</li>
 * </ul>
 *
 * <p>Bila server tidak punya API TextDisplay (pra-1.19.4), fitur mematikan diri dengan
 * satu baris log - plugin tetap jalan normal.
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
    * Pasang (atau perbarui) display uang pemain. Idempoten dan aman dipanggil kapan saja:
    * bila pemain mematikan setting, sedang menunduk, spectator, atau mati, display justru
    * dicabut.
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

   /** Scoreboard pemain berganti: pastikan scoreboard barunya bebas team suffix lama. */
   public void onScoreboardChanged(Player viewer) {
      if (viewer == null) {
         return;
      }

      ScoreboardService service = this.plugin.scoreboard();
      Scoreboard sidebar = service == null ? null : service.activeScoreboard(viewer);
      this.cleanLegacyTeam(sidebar);
   }

   /** Cabut display uang pemain (quit, mati, toggle OFF, sneak, spectator). */
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
   // Display di atas kepala
   // ------------------------------------------------------------------ //

   /** Apakah display pemain ini boleh terlihat sekarang (setting ON, hidup, tidak sneak/spectator). */
   private boolean shouldShow(Player player) {
      if (player.isDead() || player.isSneaking()) {
         return false;
      }

      try {
         if (player.getGameMode() == GameMode.SPECTATOR) {
            return false;
         }
      } catch (Throwable ignored) {
      }

      PlayerSettingsService settings = this.plugin.settings();
      return settings == null || settings.nametagMoney(player);
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

   private static boolean probeApi() {
      try {
         Class.forName("org.bukkit.entity.TextDisplay");
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
