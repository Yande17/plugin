package me.w2n.w2nsmp.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 11 skill W2NSMP.
 *
 * <p>Nilai {@code key} dipakai di mana-mana: bagian {@code skills.list.<key>} di config.yml,
 * kunci data di {@code skills.yml}, pesan {@code skill.name.<key>}/{@code skill.buff.<key>} di
 * messages.yml, dan argumen perintah {@code /skill <key>}.
 *
 * <p>Nama material ikon sengaja disimpan sebagai <b>string</b> (bukan konstanta
 * {@code Material}): material dibaca dari config lalu dicari dengan
 * {@code Material.matchMaterial}, sehingga nama item yang berubah/hilang di versi Minecraft
 * lain hanya membuat ikon jatuh ke material cadangan - bukan membuat plugin gagal dimuat.
 */
public enum SkillType {
   FIGHTING("fighting", BuffKind.MELEE_DAMAGE, "DIAMOND_SWORD"),
   DEFENSE("defense", BuffKind.DAMAGE_REDUCTION, "SHIELD"),
   ARCHERY("archery", BuffKind.PROJECTILE_DAMAGE, "BOW"),
   AGILITY("agility", BuffKind.WALK_SPEED, "LEATHER_BOOTS"),
   MINING("mining", BuffKind.HASTE, "GOLDEN_PICKAXE"),
   WOODCUTTING("woodcutting", BuffKind.EXTRA_DROP, "GOLDEN_AXE"),
   FARMING("farming", BuffKind.EXTRA_DROP, "WHEAT"),
   FISHING("fishing", BuffKind.EXTRA_CATCH, "FISHING_ROD"),
   ENDURANCE("endurance", BuffKind.ENVIRONMENT_REDUCTION, "COOKED_BEEF"),
   VITALITY("vitality", BuffKind.PASSIVE_HEAL, "GOLDEN_APPLE"),
   RECOVERY("recovery", BuffKind.REGEN_BOOST, "GLISTERING_MELON_SLICE");

   private final String key;
   private final BuffKind buff;
   private final String defaultIcon;

   SkillType(String key, BuffKind buff, String defaultIcon) {
      this.key = key;
      this.buff = buff;
      this.defaultIcon = defaultIcon;
   }

   public String key() {
      return this.key;
   }

   public BuffKind buff() {
      return this.buff;
   }

   /** Nama material ikon bawaan (dicari lewat {@code Material.matchMaterial}). */
   public String defaultIcon() {
      return this.defaultIcon;
   }

   /** Kunci label skill di messages.yml ({@code skill.name.fighting} = "Pertarungan"). */
   public String nameKey() {
      return "skill.name." + this.key;
   }

   /** Kunci penjelasan buff di messages.yml ({@code skill.buff.damage-melee}). */
   public String buffKey() {
      return "skill.buff." + this.buff.key();
   }

   /** Jalur config skill ini, mis. {@code skills.list.fighting}. */
   public String configPath() {
      return "skills.list." + this.key;
   }

   public static SkillType fromKey(String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      String wanted = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '-').replace('_', '-');

      for (SkillType type : values()) {
         if (type.key.equals(wanted) || type.name().toLowerCase(Locale.ROOT).equals(wanted)) {
            return type;
         }
      }

      return null;
   }

   public static List<String> keys() {
      List<String> result = new ArrayList<>(values().length);

      for (SkillType type : values()) {
         result.add(type.key);
      }

      return result;
   }

   /** Jumlah skill (dipakai untuk ukuran array berbasis ordinal). */
   public static int count() {
      return values().length;
   }
}
