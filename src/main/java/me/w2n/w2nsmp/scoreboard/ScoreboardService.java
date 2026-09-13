package me.w2n.w2nsmp.scoreboard;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import me.w2n.w2nsmp.W2NSMP;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class ScoreboardService {
   private static final char[] INVISIBLE_CODES = "abcdef1234567890".toCharArray();
   private static final int MAX_LINES = 15;
   private final W2NSMP plugin;
   private final ScoreboardPreferences preferences;
   private final Map<UUID, ScoreboardService.Sidebar> sidebars = new HashMap<>();
   private final List<ScoreboardLine> lines = new ArrayList<>();
   private final Set<String> neededTokens = new HashSet<>();
   private String configuredTitle = "";
   private BukkitTask task;
   private boolean updating;

   public ScoreboardService(W2NSMP plugin) {
      super();
      this.plugin = plugin;
      this.preferences = new ScoreboardPreferences(plugin);
   }

   public void load() {
      this.preferences.load();
      this.configuredTitle = this.plugin.config().scoreboardTitle();
      this.lines.clear();
      int index = 0;

      for (ScoreboardLine parsed : this.plugin.config().scoreboardLineObjects()) {
         if (parsed != null) {
            ScoreboardLine line = parsed.texts().isEmpty() ? parsed.withTexts(this.textsFor(parsed.key())) : parsed;
            if (index + line.texts().size() > 15) {
               this.plugin.getLogger().warning("Scoreboard: baris '" + line.key() + "' dilewati karena sidebar sudah penuh (15 baris maksimal).");
            } else {
               this.lines.add(line);
               index += line.texts().size();
            }
         }
      }

      this.neededTokens.clear();
      collectTokens(this.configuredTitle, this.neededTokens);

      for (ScoreboardLine line : this.lines) {
         for (String text : line.texts()) {
            collectTokens(text, this.neededTokens);
         }
      }

      this.plugin
         .getLogger()
         .info(
            "Scoreboard: sidebar "
               + (this.enabled() ? "aktif" : "nonaktif")
               + " dengan "
               + this.lineCount()
               + " baris ("
               + this.lines.stream().filter(linex -> linex.playerToggle()).count()
               + " bisa diatur pemain)"
               + (this.preferences.hiddenCount() > 0 ? " (" + this.preferences.hiddenCount() + " pemain mematikannya)." : ".")
               + (this.preferences.hiddenLinePlayers() > 0 ? " (" + this.preferences.hiddenLinePlayers() + " pemain menyembunyikan baris)." : ".")
         );
   }

   public void reload() {
      this.preferences.saveNow();
      this.hideAll();
      this.stopTasks();
      this.load();
      this.startTasks();
      if (this.enabled()) {
         for (Player player : Bukkit.getOnlinePlayers()) {
            this.apply(player);
         }
      }
   }

   public void startTasks() {
      this.stopTasks();
      if (this.enabled()) {
         long ticks = this.plugin.config().scoreboardUpdateTicks();
         this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::updateAll, ticks, ticks);
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
      this.hideAll();
      if (this.preferences.isDirty()) {
         this.preferences.saveNow();
      }
   }

   public boolean enabled() {
      return this.plugin.config().scoreboardEnabled() && !this.configuredLines().isEmpty();
   }

   private List<String> textsFor(String key) {
      String messageKey = "scoreboard.lines." + key;
      if (this.plugin.messages().hasList(messageKey)) {
         List<String> fromMessages = this.plugin.messages().rawList(messageKey);
         if (!fromMessages.isEmpty()) {
            return fromMessages;
         }
      }

      return ScoreboardLine.defaultTexts(key);
   }

   public List<ScoreboardLine> configuredLines() {
      return this.lines.stream().filter(ScoreboardLine::enabled).toList();
   }

   public List<ScoreboardLine> lines() {
      return List.copyOf(this.lines);
   }

   public boolean isLineVisible(UUID uniqueId, String key) {
      for (ScoreboardLine line : this.lines) {
         if (line.key().equalsIgnoreCase(key)) {
            return line.enabled() && !this.preferences.isLineHidden(uniqueId, line.key());
         }
      }

      return false;
   }

   public boolean setLineVisible(Player player, String key, boolean visible) {
      if (player != null && key != null) {
         ScoreboardLine line = this.lines.stream().filter(candidate -> candidate.key().equalsIgnoreCase(key)).findFirst().orElse(null);
         if (line != null && line.enabled() && line.playerToggle()) {
            boolean changed = this.preferences.setLineHidden(player.getUniqueId(), line.key(), !visible);
            if (changed) {
               this.apply(player);
            }

            return changed;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public int hiddenLinePlayers() {
      return this.preferences.hiddenLinePlayers();
   }

   public boolean visibleFor(Player player) {
      if (player == null || !this.enabled()) {
         return false;
      } else {
         return !this.plugin.config().scoreboardPersonalToggle() ? true : !this.preferences.isHidden(player.getUniqueId());
      }
   }

   public boolean isHidden(UUID uniqueId) {
      return this.preferences.isHidden(uniqueId);
   }

   public int hiddenCount() {
      return this.preferences.hiddenCount();
   }

   public File file() {
      return this.preferences.file();
   }

   public boolean taskRunning() {
      return this.task != null && !this.task.isCancelled();
   }

   public int lineCount() {
      int total = 0;

      for (ScoreboardLine line : this.lines) {
         if (line.enabled()) {
            total += line.texts().size();
         }
      }

      return total;
   }

   public void apply(Player player) {
      if (player != null) {
         if (!this.visibleFor(player)) {
            this.remove(player);
         } else {
            ScoreboardService.Sidebar sidebar = this.sidebars
               .computeIfAbsent(player.getUniqueId(), id -> new ScoreboardService.Sidebar(Bukkit.getScoreboardManager().getNewScoreboard()));
            if (!sidebar.built) {
               this.build(player, sidebar);
            }

            sidebar.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            this.update(player, sidebar);
            player.setScoreboard(sidebar.scoreboard);
            this.syncNametag(player);
         }
      }
   }

   /**
    * Scoreboard sidebar yang sedang dipakai pemain, atau {@code null} bila sidebar tidak aktif
    * untuk pemain itu. Dipakai {@code NametagService}: pemain dengan sidebar aktif tidak lagi
    * melihat scoreboard utama server, jadi team nametag uang harus ditulis ke scoreboard ini.
    */
   public Scoreboard activeScoreboard(Player player) {
      if (player == null || !this.enabled()) {
         return null;
      }

      ScoreboardService.Sidebar sidebar = this.sidebars.get(player.getUniqueId());
      return sidebar != null && sidebar.built ? sidebar.scoreboard : null;
   }

   /** Sinkronkan ulang baris uang setelah scoreboard pemain berganti. */
   private void syncNametag(Player player) {
      if (this.plugin.nametag() != null && this.plugin.nametag().enabled()) {
         this.plugin.nametag().onScoreboardChanged(player);
      }
   }

   public void remove(Player player) {
      if (player != null) {
         ScoreboardService.Sidebar sidebar = this.sidebars.remove(player.getUniqueId());
         if (sidebar != null) {
            if (player.isOnline()) {
               player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
               this.syncNametag(player);
            }
         }
      }
   }

   public void hideAll() {
      for (Player player : Bukkit.getOnlinePlayers()) {
         this.remove(player);
      }

      this.sidebars.clear();
   }

   public void updateAll() {
      if (this.enabled() && !this.updating) {
         this.updating = true;

         try {
            for (Player player : Bukkit.getOnlinePlayers()) {
               if (this.sidebars.containsKey(player.getUniqueId())) {
                  this.update(player, this.sidebars.get(player.getUniqueId()));
               } else if (this.visibleFor(player)) {
                  this.apply(player);
               }
            }
         } finally {
            this.updating = false;
         }
      }
   }

   private static void collectTokens(String text, Set<String> target) {
      if (text != null) {
         int start = text.indexOf(37);

         while (start >= 0) {
            int end = text.indexOf(37, start + 1);
            if (end < 0) {
               return;
            }

            if (end > start + 1) {
               target.add(text.substring(start + 1, end));
            }

            start = text.indexOf(37, end + 1);
         }
      }
   }

   public void update(Player player) {
      if (player != null) {
         ScoreboardService.Sidebar sidebar = this.sidebars.get(player.getUniqueId());
         if (sidebar == null) {
            this.apply(player);
         } else {
            this.update(player, sidebar);
         }
      }
   }

   public boolean setVisible(Player player, boolean visible) {
      if (player == null) {
         return false;
      }

      boolean changed = this.preferences.setHidden(player.getUniqueId(), !visible);
      if (changed) {
         if (visible) {
            this.apply(player);
         } else {
            this.remove(player);
         }
      }

      return changed;
   }

   public int activeCount() {
      return this.sidebars.size();
   }

   public List<String> renderedLines(Player player) {
      ScoreboardService.Sidebar sidebar = player == null ? null : this.sidebars.get(player.getUniqueId());
      if (sidebar == null) {
         return List.of();
      }

      List<String> result = new ArrayList<>(sidebar.rendered.size());

      for (String line : sidebar.rendered) {
         if (line != null) {
            result.add(line);
         }
      }

      return List.copyOf(result);
   }

   private void build(Player player, ScoreboardService.Sidebar sidebar) {
      Scoreboard scoreboard = sidebar.scoreboard;
      String name = "w2n" + player.getUniqueId().toString().replace("-", "").substring(0, 10);
      sidebar.title = this.titleFor(player);
      sidebar.objective = scoreboard.registerNewObjective(name, Criteria.DUMMY, this.plugin.messages().colored(sidebar.title));
      sidebar.objective.setDisplaySlot(DisplaySlot.SIDEBAR);

      for (int index = 0; index < 15; index++) {
         String entry = invisibleEntry(index);
         Team team = scoreboard.registerNewTeam(teamName(index));
         team.addEntry(entry);
         sidebar.entries.add(entry);
         sidebar.teams.add(team);
         sidebar.rendered.add(null);
      }

      sidebar.built = true;
      this.update(player, sidebar);
   }

   static String invisibleEntry(int index) {
      char code = INVISIBLE_CODES[index % INVISIBLE_CODES.length];
      int repeat = index / INVISIBLE_CODES.length;
      StringBuilder builder = new StringBuilder("§").append(code).append("§r");
      builder.append("§r".repeat(repeat));
      return builder.toString();
   }

   private void update(Player player, ScoreboardService.Sidebar sidebar) {
      if (sidebar != null && sidebar.built) {
         String[] placeholders = this.plugin.stats() == null
            ? new String[]{"player", player.getName()}
            : this.plugin.stats().scoreboardPlaceholders(player, this.neededTokens);
         String title = this.titleFor(player, placeholders);
         if (!title.equals(sidebar.title)) {
            sidebar.title = title;
            sidebar.objective.displayName(this.plugin.messages().colored(title));
         }

         List<String> rows = this.renderedRows(player, placeholders);
         if (rows.isEmpty()) {
            if (sidebar.rendered.stream().anyMatch(Objects::nonNull)) {
               sidebar.rendered.replaceAll(ignored -> null);

               for (Team team : sidebar.teams) {
                  team.prefix(Component.empty());
               }

               for (String entry : sidebar.entries) {
                  sidebar.scoreboard.resetScores(entry);
               }
            } else if (sidebar.objective.getDisplaySlot() == DisplaySlot.SIDEBAR) {
               sidebar.objective.setDisplaySlot(null);
            }
         } else {
            if (sidebar.objective.getDisplaySlot() != DisplaySlot.SIDEBAR) {
               sidebar.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            }

            for (int index = 0; index < sidebar.entries.size(); index++) {
               Team team = sidebar.teams.get(index);
               String entry = sidebar.entries.get(index);
               Score score = sidebar.objective.getScore(entry);
               if (index < rows.size()) {
                  String rendered = rows.get(index);
                  if (!rendered.equals(sidebar.rendered.get(index))) {
                     sidebar.rendered.set(index, rendered);
                     team.prefix(this.plugin.messages().colored(rendered));
                  }

                  if (!score.isScoreSet()) {
                     score.setScore(15 - index);
                  }
               } else if (sidebar.rendered.get(index) != null || score.isScoreSet()) {
                  sidebar.rendered.set(index, null);
                  team.prefix(Component.empty());
                  sidebar.scoreboard.resetScores(entry);
               }
            }
         }
      }
   }

   private List<String> renderedRows(Player player, String[] placeholders) {
      List<String> rows = new ArrayList<>(15);

      for (ScoreboardLine line : this.lines) {
         if (line.enabled() && (!line.playerToggle() || !this.preferences.isLineHidden(player.getUniqueId(), line.key()))) {
            for (String raw : line.texts()) {
               if (rows.size() >= 15) {
                  return rows;
               }

               rows.add(this.plugin.messages().apply(raw, placeholders));
            }
         }
      }

      return rows;
   }

   private String titleFor(Player player) {
      return this.plugin
         .messages()
         .apply(
            this.plugin.config().scoreboardTitle(),
            "player",
            player.getName(),
            "world",
            player.getWorld().getName(),
            "online",
            Integer.toString(Bukkit.getOnlinePlayers().size())
         );
   }

   /**
    * Judul sidebar dengan seluruh placeholder (bukan hanya player/world/online), jadi judul
    * seperti "&6%money%" atau "Rank: &e%rank-money%" ikut terisi.
    */
   private String titleFor(Player player, String[] placeholders) {
      if (placeholders == null || placeholders.length < 2) {
         return this.titleFor(player);
      }

      return this.plugin.messages().apply(this.plugin.config().scoreboardTitle(), placeholders);
   }

   private static String teamName(int index) {
      return "w2nline" + index;
   }

   private static final class Sidebar {
      private final Scoreboard scoreboard;
      private final List<Team> teams = new ArrayList<>();
      private final List<String> entries = new ArrayList<>();
      private final List<String> rendered = new ArrayList<>();
      private Objective objective;
      private String title = "";
      private boolean built;

      private Sidebar(Scoreboard scoreboard) {
         super();
         this.scoreboard = scoreboard;
      }
   }
}
