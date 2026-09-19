package me.w2n.w2nsmp.skill;

import java.util.List;
import me.w2n.w2nsmp.W2NSMP;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Penyusun teks skill untuk chat (dipakai {@code /skill} dan GUI {@code /skill}).
 *
 * <p>Semua kalimat diambil dari {@code messages.yml} bagian {@code skill.*}, jadi admin bisa
 * menerjemahkan/mengubah tampilan tanpa menyentuh kode. Placeholder yang tersedia:
 * {@code %player% %skill% %key% %level% %max% %next-level% %xp% %xp-into% %xp-needed%
 * %xp-to-next% %percent% %bar% %buff% %buff-desc% %source% %unlock% %total-xp% %total-level%
 * %buffs% %buff-count% %buffs-unlocked% %next-buff% %next-buff-level%}.
 */
public final class SkillInfo {
   private SkillInfo() {
   }

   public static String[] placeholders(W2NSMP plugin, Player target, SkillType type) {
      SkillService service = plugin.skills();
      if (service == null || type == null) {
         return new String[]{"player", target == null ? "-" : target.getName(), "skill", "-", "key", "-"};
      }

      SkillCurve curve = service.curve();
      SkillSettings settings = service.settings(type);
      double xp = service.xp(target, type);
      int level = curve.levelFor(xp);
      boolean maxed = level >= curve.maxLevel();
      String[] placeholders = new String[]{
         "player",
         target == null ? "-" : target.getName(),
         "skill",
         service.label(type),
         "key",
         type.key(),
         "level",
         Integer.toString(level),
         "max",
         Integer.toString(curve.maxLevel()),
         "next-level",
         Integer.toString(Math.min(curve.maxLevel(), level + 1)),
         "xp",
         SkillService.format(xp),
         "xp-into",
         SkillService.format(curve.xpIntoLevel(xp)),
         "xp-needed",
         SkillService.format(maxed ? 0.0D : curve.required(level)),
         "xp-to-next",
         SkillService.format(curve.xpToNextLevel(xp)),
         "percent",
         SkillService.format(curve.progress(xp) * 100.0D),
         "bar",
         bar(plugin, curve, xp),
         "buff",
         service.buffDisplay(target, type),
         "buff-desc",
         service.buffDescription(type),
         "source",
         source(plugin, type),
         "unlock",
         Integer.toString(settings.buffUnlockLevel()),
         "total-xp",
         SkillService.format(service.totalXp(target)),
         "total-level",
         Integer.toString(service.totalLevel(target)),
         "buffs",
         buffsCompact(plugin, target, type),
         "buff-count",
         Integer.toString(settings.buffCount()),
         "buffs-unlocked",
         Integer.toString(settings.unlockedBuffs(level)),
         "next-buff",
         nextBuffName(service, settings, level),
         "next-buff-level",
         nextBuffLevel(settings, level)
      };
      return placeholders;
   }

   private static String bar(W2NSMP plugin, SkillCurve curve, double xp) {
      String filled = plugin.messages().raw("skill.bar-filled");
      String empty = plugin.messages().raw("skill.bar-empty");
      int width = Math.max(1, Math.min(40, plugin.config().raw().getInt("skills.progress-bar-width", 10)));
      return curve.bar(xp, width, filled, empty);
   }

   private static String source(W2NSMP plugin, SkillType type) {
      String key = "skill.source." + type.key();
      return plugin.messages().has(key) ? plugin.messages().raw(key) : type.key();
   }

   /** Semua buff skill dalam satu baris pendek ("Damage melee: +15%, Peluang critical: +10,3%"). */
   private static String buffsCompact(W2NSMP plugin, Player target, SkillType type) {
      SkillService service = plugin.skills();
      if (service == null || type == null) {
         return "-";
      }

      SkillSettings settings = service.settings(type);
      int level = target == null ? 1 : service.level(target, type);
      String separator = plugin.messages().has("skill.buff-separator") ? plugin.messages().raw("skill.buff-separator") : "&8, &7";
      StringBuilder result = new StringBuilder();

      for (SkillBuff buff : settings.buffs()) {
         if (result.length() > 0) {
            result.append(separator);
         }

         result.append(service.buffName(buff)).append(": ").append(service.buffDisplay(target, type, buff));
      }

      return result.length() == 0 ? "-" : result.toString();
   }

   private static String nextBuffName(SkillService service, SkillSettings settings, int level) {
      SkillBuff next = settings.nextBuff(level);
      return next == null ? "-" : service.buffName(next);
   }

   private static String nextBuffLevel(SkillSettings settings, int level) {
      SkillBuff next = settings.nextBuff(level);
      return next == null ? "-" : Integer.toString(next.unlockLevel());
   }

   /** Detail satu skill (dipakai {@code /skill <key>} dan klik ikon di GUI). */
   public static void sendDetail(W2NSMP plugin, CommandSender viewer, Player target, SkillType type) {
      SkillService service = plugin.skills();
      if (service == null || type == null) {
         return;
      }

      String[] placeholders = placeholders(plugin, target, type);
      plugin.messages().send(viewer, "skill.detail-header", placeholders);
      plugin.messages().send(viewer, "skill.detail-level", placeholders);
      if (service.curve().isMaxLevel(service.xp(target, type))) {
         plugin.messages().send(viewer, "skill.detail-max", placeholders);
      } else {
         plugin.messages().send(viewer, "skill.detail-progress", placeholders);
      }

      SkillSettings settings = service.settings(type);
      int level = service.level(target, type);
      if (plugin.messages().has("skill.detail-buffs-header") && settings.buffCount() > 1) {
         plugin.messages().send(viewer, "skill.detail-buffs-header",
            with(placeholders, "count", Integer.toString(settings.buffCount()),
               "unlocked", Integer.toString(settings.unlockedBuffs(level))));
      }

      List<String> lines = service.buffLines(target, type);
      if (settings.buffCount() > 1 && plugin.messages().has("skill.detail-buff-line")) {
         for (int index = 0; index < lines.size(); index++) {
            SkillBuff buff = index < settings.buffCount() ? settings.buffs().get(index) : null;
            plugin.messages().send(viewer, "skill.detail-buff-line",
               with(placeholders, buffPlaceholders(service, target, type, buff, lines.get(index))));
         }
      } else {
         plugin.messages().send(viewer, "skill.detail-buff", placeholders);
         if (plugin.messages().has("skill.detail-buff-desc")) {
            plugin.messages().send(viewer, "skill.detail-buff-desc", placeholders);
         }
      }

      if (plugin.messages().has("skill.detail-source")) {
         plugin.messages().send(viewer, "skill.detail-source", placeholders);
      }

      SkillBuff next = settings.nextBuff(level);
      if (next != null && plugin.messages().has("skill.detail-next-buff")) {
         plugin.messages().send(viewer, "skill.detail-next-buff",
            with(placeholders, "unlock", Integer.toString(next.unlockLevel()), "buff", service.buffName(next)));
      }
   }

   /** Penjelasan rinci satu buff di chat (dipakai saat kartu buff di menu progres diklik). */
   public static void sendBuffDetail(W2NSMP plugin, CommandSender viewer, SkillType type, SkillBuff buff) {
      SkillService service = plugin.skills();
      if (service == null || viewer == null || type == null || buff == null) {
         return;
      }

      Player target = viewer instanceof Player player ? player : null;
      String[] placeholders = with(placeholders(plugin, target, type), buffPlaceholders(service, target, type, buff, null));
      plugin.messages().send(viewer, "skill.buff-detail-header", placeholders);
      for (String key : new String[]{"skill.buff-detail-value", "skill.buff-detail-growth", "skill.buff-detail-unlock",
         "skill.buff-detail-desc"}) {
         if (plugin.messages().has(key)) {
            plugin.messages().send(viewer, key, placeholders);
         }
      }
   }

   private static String[] buffPlaceholders(SkillService service, Player target, SkillType type, SkillBuff buff, String line) {
      if (buff == null) {
         return new String[]{"buff", "-", "buff-value", "-", "buff-desc", "", "unlock", "-"};
      }

      SkillSettings settings = service.settings(type);
      int level = target == null ? 1 : service.level(target, type);
      int capLevel = buff.capLevel();
      return new String[]{
         "buff",
         service.buffName(buff),
         "buff-value",
         line == null ? service.buffDisplay(target, type, buff) : line,
         "buff-desc",
         service.buffDescription(buff),
         "buff-kind",
         buff.kind() == null ? "-" : buff.kind().key(),
         "unlock",
         Integer.toString(buff.unlockLevel()),
         "per",
         SkillService.format(buff.perLevel()),
         "max",
         SkillService.format(buff.max()),
         "power",
         SkillService.format(buff.power()),
         "value",
         SkillService.format(buff.value(level)),
         "cap-level",
         capLevel < 0 ? "-" : Integer.toString(Math.min(capLevel, service.maxLevel())),
         "status",
         settings.enabled() && buff.unlocked(level) ? "active" : "locked"
      };
   }

   /**
    * Peringkat skill ke chat: total semua skill ({@code type} null) atau satu skill tertentu.
    * Barisnya diambil dari messages.yml ({@code skill.top-*}) supaya admin bisa mengubah tampilan.
    */
   public static void sendTop(W2NSMP plugin, CommandSender viewer, SkillType type) {
      SkillService service = plugin.skills();
      if (service == null || viewer == null || !service.enabled()) {
         return;
      }

      int limit = service.topLimit();
      List<SkillTop.Entry> entries = type == null ? service.top().total(limit, 0) : service.top().skill(type, limit, 0);
      String scope = type == null
         ? (plugin.messages().has("skill.top-scope-total") ? plugin.messages().raw("skill.top-scope-total") : "total skill")
         : service.label(type);
      String[] header = {
         "scope", scope,
         "count", Integer.toString(entries.size()),
         "limit", Integer.toString(limit),
         "max", Integer.toString(service.maxLevel()),
         "type", type == null ? "total" : type.key()
      };
      plugin.messages().send(viewer, "skill.top-header", header);
      if (entries.isEmpty()) {
         plugin.messages().send(viewer, "skill.top-empty", header);
         return;
      }

      int rank = 1;

      for (SkillTop.Entry entry : entries) {
         String[] placeholders = {
            "rank", Integer.toString(rank++),
            "player", entry.name(),
            "level", Integer.toString(entry.level()),
            "xp", SkillService.format(entry.xp()),
            "total-level", Integer.toString(entry.totalLevel()),
            "total-xp", SkillService.format(entry.totalXp()),
            "scope", scope
         };
         String key = type == null ? "skill.top-line-total" : "skill.top-line";
         if (plugin.messages().has(key)) {
            plugin.messages().send(viewer, key, placeholders);
         }
      }

      if (viewer instanceof Player requester) {
         int mine = type == null
            ? service.top().rankOfTotal(requester.getUniqueId())
            : service.top().rankOfSkill(requester.getUniqueId(), type);
         SkillSettings settings = type == null ? null : service.settings(type);
         plugin.messages().send(viewer, "skill.top-self",
            "rank", mine <= 0 ? "-" : Integer.toString(mine),
            "scope", scope,
            "level", Integer.toString(type == null ? service.totalLevel(requester) : service.level(requester, type)),
            "xp", SkillService.format(type == null ? service.totalXp(requester) : service.xp(requester, type)),
            "total-level", Integer.toString(service.totalLevel(requester)),
            "total-xp", SkillService.format(service.totalXp(requester)),
            "buff", type == null ? "-" : service.buffDisplay(requester, type),
            "max", Integer.toString(settings == null ? service.maxLevel() : service.maxLevel()));
      }
   }

   /** Gabungkan placeholder dasar dengan pasangan tambahan. */
   private static String[] with(String[] base, String... extra) {
      String[] result = new String[base.length + extra.length];
      System.arraycopy(base, 0, result, 0, base.length);
      System.arraycopy(extra, 0, result, base.length, extra.length);
      return result;
   }

   /** Ringkasan semua skill (dipakai {@code /skill list} dan {@code /skill} di konsol). */
   public static void sendSummary(W2NSMP plugin, CommandSender viewer, Player target) {
      SkillService service = plugin.skills();
      if (service == null || target == null) {
         return;
      }

      plugin
         .messages()
         .send(
            viewer,
            "skill.list-header",
            "player",
            target.getName(),
            "total-level",
            Integer.toString(service.totalLevel(target)),
            "total-xp",
            SkillService.format(service.totalXp(target)),
            "max",
            Integer.toString(service.maxLevel())
         );

      for (SkillType type : SkillType.values()) {
         SkillSettings settings = service.settings(type);
         if (!settings.enabled()) {
            continue;
         }

         plugin.messages().send(viewer, "skill.list-line", placeholders(plugin, target, type));
      }
   }
}
