package me.w2n.w2nsmp.nametag;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.scoreboard.ScoreboardService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Layanan tampilan uang pemain (v1.6.0, PHASE 1).
 *
 * <p><b>PHASE 1: uang DIHAPUS dari nama pemain.</b> Mekanisme lama (team suffix
 * {@code w2nmoney} yang menempelkan " | $10,000" di baris nama) sudah tidak dipakai lagi -
 * nama pemain kembali bersih ("PlayerName" saja). Karena team scoreboard utama tersimpan di
 * {@code scoreboard.dat} server, layanan ini tetap MEMBERSIHKAN team lama saat startup,
 * reload, dan shutdown supaya tidak ada sisa suffix dari versi sebelumnya.
 *
 * <p>Kerangka publik kelas (apply/remove/refresh/dll.) sengaja dipertahankan: pemanggil lama
 * (EconomyManager, ScoreboardService, SettingsMenu, ScoreboardListener) tetap kompatibel, dan
 * PHASE 2 akan mengisi kembali kerangka ini dengan tampilan uang DI ATAS KEPALA pemain.
 * Rank/prefix dari plugin lain tidak pernah disentuh - kami hanya menghapus team milik kami
 * sendiri ({@link #TEAM_NAME}).
 */
public final class NametagService {
   /** Nama team lama yang dipakai versi <= 1.5.x untuk suffix uang - kini hanya dibersihkan. */
   public static final String TEAM_NAME = "w2nmoney";

   private final W2NSMP plugin;

   public NametagService(W2NSMP plugin) {
      this.plugin = plugin;
   }

   /** Startup: bersihkan team suffix lama dari semua scoreboard (utama + sidebar). */
   public void load() {
      this.removeAll();
   }

   public void reload() {
      this.removeAll();
   }

   /** PHASE 1: tidak ada task berkala - tidak ada lagi yang perlu disegarkan. */
   public void startTasks() {
   }

   public void stopTasks() {
   }

   public void shutdown() {
      this.removeAll();
   }

   /** Gate config lama (dipakai /setting untuk status Locked). */
   public boolean enabled() {
      return this.plugin.config().nametagMoneyEnabled();
   }

   public boolean taskRunning() {
      return false;
   }

   public int activeCount() {
      return 0;
   }

   /** Jumlah scoreboard yang dipindai saat pembersihan team lama. */
   public int targetCount() {
      return this.targets().size();
   }

   /** PHASE 1: tidak ada yang dipasang ke nama - selalu false (nama pemain bersih). */
   public boolean apply(Player player) {
      return false;
   }

   /** PHASE 1: tidak ada tampilan di nama yang perlu disegarkan. */
   public void refresh(Player player) {
   }

   /** Scoreboard pemain berganti (sidebar on/off): pastikan scoreboard baru bebas team lama. */
   public void onScoreboardChanged(Player viewer) {
      if (viewer == null) {
         return;
      }

      ScoreboardService service = this.plugin.scoreboard();
      Scoreboard sidebar = service == null ? null : service.activeScoreboard(viewer);
      this.cleanTeam(sidebar);
   }

   /** Hapus entri pemain ini dari team lama di semua scoreboard. */
   public void remove(Player player) {
      if (player == null) {
         return;
      }

      String name = player.getName();

      for (Scoreboard scoreboard : this.targets().values()) {
         if (scoreboard == null) {
            continue;
         }

         try {
            Team team = scoreboard.getTeam(TEAM_NAME);
            if (team != null && team.hasEntry(name)) {
               team.removeEntry(name);
            }
         } catch (Throwable ignored) {
         }
      }
   }

   /** Bersihkan team suffix lama dari SEMUA scoreboard (termasuk sisa di scoreboard.dat). */
   public void removeAll() {
      Set<Scoreboard> boards = new LinkedHashSet<>(this.targets().values());

      for (Scoreboard scoreboard : boards) {
         this.cleanTeam(scoreboard);
      }
   }

   /** Kompatibilitas API lama: teks uang pemain (tidak lagi ditampilkan di nama). */
   public Component text(Player player) {
      String balance = this.plugin.economy() != null && this.plugin.economy().isEnabled()
         ? this.plugin.economy().format(this.plugin.economy().balance(player))
         : "-";
      return this.plugin.messages().colored(balance);
   }

   /** PHASE 1: tidak ada pemain yang dikelola di nama. */
   public boolean managed(UUID uniqueId) {
      return false;
   }

   // ------------------------------------------------------------------ //

   /** Cabut semua entri lalu unregister team lama pada satu scoreboard. */
   private void cleanTeam(Scoreboard scoreboard) {
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
         this.plugin.debug("Nametag: gagal membersihkan team lama (" + throwable + ").");
      }
   }

   /** Scoreboard utama + scoreboard sidebar setiap pemain online. */
   private Map<String, Scoreboard> targets() {
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
