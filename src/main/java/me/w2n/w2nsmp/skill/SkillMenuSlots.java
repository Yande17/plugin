package me.w2n.w2nsmp.skill;

/**
 * Slot bawaan GUI {@code /skill}.
 *
 * <p>Nilai ini hanya cadangan: posisi sebenarnya dibaca dari {@code gui/skill.yml}
 * ({@code slots.<key>}) atau {@code skills.list.<key>.slot} di config.yml. Dipisahkan dari
 * {@code SkillMenu} supaya paket {@code skill} tidak bergantung pada paket {@code gui}.
 *
 * <pre>
 *   baris 2 : 10 11 12 13 14 15 16   (fighting .. farming)
 *   baris 3 : 20 21 22 23            (fishing .. recovery)
 *   baris 6 : 49 info | 53 close
 * </pre>
 */
public final class SkillMenuSlots {
   public static final int SIZE = 54;
   public static final int INFO = 49;
   public static final int CLOSE = 53;

   private SkillMenuSlots() {
   }

   public static int fallback(SkillType type) {
      if (type == null) {
         return INFO;
      }

      return switch (type) {
         case FIGHTING -> 10;
         case DEFENSE -> 11;
         case ARCHERY -> 12;
         case AGILITY -> 13;
         case MINING -> 14;
         case WOODCUTTING -> 15;
         case FARMING -> 16;
         case FISHING -> 20;
         case ENDURANCE -> 21;
         case VITALITY -> 22;
         case RECOVERY -> 23;
      };
   }

   /** Kunci slot di {@code gui/skill.yml} untuk skill ini. */
   public static String slotKey(SkillType type) {
      return "slots." + (type == null ? "info" : type.key());
   }
}
