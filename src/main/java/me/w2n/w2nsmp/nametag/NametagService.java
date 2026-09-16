package me.w2n.w2nsmp.nametag;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.player.PlayerSettingsService;
import me.w2n.w2nsmp.scoreboard.ScoreboardService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Baris uang di bawah nama pemain.
 *
 * <p>Catatan penting: W2NSMP memasang <em>scoreboard pribadi</em> untuk setiap pemain yang
 * sidebar statistiknya aktif ({@link ScoreboardService}). Pemain seperti itu tidak lagi melihat
 * scoreboard utama server, jadi team nametag yang hanya didaftarkan di scoreboard utama tidak
 * akan pernah tampil. Karena itu layanan ini menulis ke <b>semua</b> scoreboard yang sedang
 * dipakai pemain: scoreboard utama (untuk pemain tanpa sidebar) dan scoreboard sidebar tiap
 * pemain online.
 *
 * <p>Agar tidak membanjiri jaringan dengan pembaruan team yang isinya sama, teks terakhir yang
 * dikirim untuk setiap (target, entri) disimpan di {@link #suffixCache}; suffix hanya ditulis
 * ulang bila teksnya benar-benar berubah.
 */
public final class NametagService {
   public static final String TEAM_NAME = "w2nmoney";
   private static final String MAIN_TARGET = "main";

   private final W2NSMP plugin;
   private final Set<UUID> managed = new HashSet<>();
   private final Map<String, Map<String, String>> suffixCache = new HashMap<>();
   private BukkitTask task;

   public NametagService(W2NSMP plugin) {
      this.plugin = plugin;
   }

   public void load() {
      if (!this.enabled()) {
         this.removeAll();
      } else {
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
      if (this.enabled()) {
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

   public int activeCount() {
      return this.managed.size();
   }

   /** Jumlah scoreboard yang sedang menerima team nametag (utama + sidebar tiap pemain). */
   public int targetCount() {
      return this.targets().size();
   }

   public boolean apply(Player player) {
      if (player == null || !this.enabled()) {
         return false;
      }

      Scoreboard main = this.mainScoreboard();
      if (main == null) {
         return false;
      }

      PlayerSettingsService settings = this.plugin.settings();
      if (settings != null && !settings.nametagMoney(player)) {
         this.remove(player);
         return false;
      }

      Team ours = this.team(main);
      Team existing = main.getEntryTeam(player.getName());
      if (existing != null && !existing.equals(ours)) {
         if (this.plugin.config().nametagMoneyRespectTeams()) {
            this.managed.remove(player.getUniqueId());
            if (this.plugin.config().debug()) {
               this.plugin.getLogger().info("Nametag: " + player.getName() + " sudah di tim " + existing.getName() + " -> baris uang dilewati.");
            }

            return false;
         }

         existing.removeEntry(player.getName());
      }

      this.managed.add(player.getUniqueId());
      this.publish(player);
      return true;
   }

   public void refresh(Player player) {
      if (player != null && this.enabled()) {
         if (!this.managed.contains(player.getUniqueId())) {
            if (player.isOnline()) {
               this.apply(player);
            }
         } else {
            this.publish(player);
         }
      }
   }

   /**
    * Dipanggil {@link ScoreboardService} saat scoreboard seorang pemain berganti (sidebar
    * dinyalakan/dimatikan, join, quit, reload). Scoreboard yang baru harus langsung berisi
    * baris uang semua pemain yang dikelola, dan cache scoreboard lama dibuang.
    */
   public void onScoreboardChanged(Player viewer) {
      if (viewer == null || !this.enabled()) {
         return;
      }

      String target = viewer.getUniqueId().toString();
      this.suffixCache.remove(target);
      ScoreboardService service = this.plugin.scoreboard();
      Scoreboard sidebar = service == null ? null : service.activeScoreboard(viewer);
      if (sidebar == null) {
         return;
      }

      for (UUID uniqueId : Set.copyOf(this.managed)) {
         Player subject = Bukkit.getPlayer(uniqueId);
         if (subject != null && subject.isOnline()) {
            this.publishTo(target, sidebar, subject);
         }
      }
   }

   public void remove(Player player) {
      if (player != null) {
         this.managed.remove(player.getUniqueId());
         this.suffixCache.remove(player.getUniqueId().toString());
         String name = player.getName();

         for (Entry<String, Scoreboard> target : this.targets().entrySet()) {
            Scoreboard scoreboard = target.getValue();
            if (scoreboard == null) {
               continue;
            }

            Team team = scoreboard.getTeam(TEAM_NAME);
            if (team != null && team.hasEntry(name)) {
               team.removeEntry(name);
            }

            Map<String, String> cache = this.suffixCache.get(target.getKey());
            if (cache != null) {
               cache.remove(name);
            }
         }
      }
   }

   public void removeAll() {
      Set<Scoreboard> boards = new LinkedHashSet<>(this.targets().values());

      for (Scoreboard scoreboard : boards) {
         if (scoreboard == null) {
            continue;
         }

         Team team = scoreboard.getTeam(TEAM_NAME);
         if (team != null) {
            for (String entry : Set.copyOf(team.getEntries())) {
               team.removeEntry(entry);
            }

            team.unregister();
         }
      }

      this.managed.clear();
      this.suffixCache.clear();
   }

   public Component text(Player player) {
      return this.plugin.messages().colored(this.textString(player));
   }

   public boolean managed(UUID uniqueId) {
      return uniqueId != null && this.managed.contains(uniqueId);
   }

   /** Tulis baris uang pemain ke semua scoreboard yang sedang dipakai. */
   private void publish(Player player) {
      for (Entry<String, Scoreboard> target : this.targets().entrySet()) {
         this.publishTo(target.getKey(), target.getValue(), player);
      }
   }

   private void publishTo(String target, Scoreboard scoreboard, Player player) {
      if (scoreboard == null || player == null) {
         return;
      }

      Team team = this.team(scoreboard);
      if (team == null) {
         return;
      }

      String name = player.getName();
      String text = this.textString(player);
      if (!team.hasEntry(name)) {
         team.addEntry(name);
      }

      Map<String, String> cache = this.suffixCache.computeIfAbsent(target, ignored -> new HashMap<>());
      if (!text.equals(cache.get(name))) {
         team.suffix(this.plugin.messages().colored(text));
         cache.put(name, text);
      }
   }

   /**
    * Semua scoreboard yang perlu memuat team nametag: scoreboard utama (dipakai pemain tanpa
    * sidebar) plus scoreboard sidebar milik setiap pemain online.
    */
   private Map<String, Scoreboard> targets() {
      Map<String, Scoreboard> result = new LinkedHashMap<>();
      Scoreboard main = this.mainScoreboard();
      if (main != null) {
         result.put(MAIN_TARGET, main);
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

   private String textString(Player player) {
      String balance = this.plugin.economy() != null && this.plugin.economy().isEnabled()
         ? this.plugin.economy().format(this.plugin.economy().balance(player))
         : "-";
      String prefix = this.plugin.config().nametagMoneyNewLine() ? "\n" : "";
      return prefix + this.plugin.messages().apply(this.plugin.config().nametagMoneyFormat(), "balance", balance, "player", player.getName());
   }

   private Scoreboard mainScoreboard() {
      return Bukkit.getScoreboardManager() == null ? null : Bukkit.getScoreboardManager().getMainScoreboard();
   }

   private Team team(Scoreboard scoreboard) {
      if (scoreboard == null) {
         return null;
      }

      Team team = scoreboard.getTeam(TEAM_NAME);
      if (team == null) {
         team = scoreboard.registerNewTeam(TEAM_NAME);
      }

      team.setCanSeeFriendlyInvisibles(false);
      return team;
   }
}
