package me.w2n.w2nsmp.skill;

import me.w2n.w2nsmp.W2NSMP;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Penyusun teks skill untuk chat (dipakai {@code /skill} dan GUI {@code /skill}).
 *
 * <p>Semua kalimat diambil dari {@code messages.yml} bagian {@code skill.*}, jadi admin bisa
 * menerjemahkan/mengubah tampilan tanpa menyentuh kode. Placeholder yang tersedia:
 * {@code %player% %skill% %key% %level% %max% %next-level% %xp% %xp-into% %xp-needed%
 * %xp-to-next% %percent% %bar% %buff% %buff-desc% %source% %unlock% %total-xp% %total-level%}.
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
         Integer.toString(service.totalLevel(target))
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

      plugin.messages().send(viewer, "skill.detail-buff", placeholders);
      if (plugin.messages().has("skill.detail-buff-desc")) {
         plugin.messages().send(viewer, "skill.detail-buff-desc", placeholders);
      }

      if (plugin.messages().has("skill.detail-source")) {
         plugin.messages().send(viewer, "skill.detail-source", placeholders);
      }

      SkillSettings settings = service.settings(type);
      if (service.level(target, type) < settings.buffUnlockLevel() && plugin.messages().has("skill.detail-next-buff")) {
         plugin.messages().send(viewer, "skill.detail-next-buff", placeholders);
      }
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
