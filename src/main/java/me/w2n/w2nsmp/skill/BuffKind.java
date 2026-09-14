package me.w2n.w2nsmp.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Jenis buff yang bisa diberikan sebuah skill.
 *
 * <p>Sejak v1.3.0 satu skill boleh punya <b>lebih dari satu</b> buff (lihat {@link SkillBuff}):
 * misalnya fighting memberi damage melee sejak level 1, peluang critical sejak level 10, dan
 * peluang loot mob tambahan sejak level 25. Setiap jenis punya satu tempat penerapan - sebagian
 * lewat perhitungan event (damage, loot, XP vanilla), sebagian lewat tugas berkala (regen, heal,
 * potion), dan sebagian lewat peluang di {@code addXp}.
 *
 * <p>Nilai tiap buff diambil dari {@code config.yml} bagian {@code skills.list.<key>.buffs.<n>};
 * bagian lama {@code skills.list.<key>.buff} tetap dibaca sebagai buff utama supaya config server
 * yang sudah ada tidak perlu ditulis ulang.
 */
public enum BuffKind {
   /** Damage serangan jarak dekat (tangan/pedang) ditambah sekian persen. */
   MELEE_DAMAGE("damage-melee"),
   /** Damage proyektil (panah/trident) ditambah sekian persen. */
   PROJECTILE_DAMAGE("damage-projectile"),
   /** Peluang persen sebuah pukulan menjadi critical (damage dikalikan {@code power}). */
   CRIT_CHANCE("crit-chance"),
   /** Damage yang diterima pemain dikurangi sekian persen. */
   DAMAGE_REDUCTION("damage-reduction"),
   /** Peluang persen menahan sebagian damage satu pukulan (sebesar {@code power} persen). */
   BLOCK_CHANCE("block-chance"),
   /** Damage dari penyebab lingkungan (jatuh, api, tenggelam, ...) dikurangi sekian persen. */
   ENVIRONMENT_REDUCTION("environment-reduction"),
   /** Kecepatan jalan pemain ditambah sekian persen. */
   WALK_SPEED("walk-speed"),
   /** Efek Haste (kecepatan tambang) dengan tingkat yang naik bertahap. */
   HASTE("haste"),
   /** Efek potion apa pun (regen, absorption, night vision, ...) dengan tingkat bertahap. */
   POTION("potion"),
   /** Peluang persen mendapat satu drop tambahan saat menebang/memanen. */
   EXTRA_DROP("extra-drop"),
   /** Peluang persen hasil pancingan ganda. */
   EXTRA_CATCH("extra-catch"),
   /** Peluang persen satu drop mob diduplikasi saat pemain membunuhnya. */
   MOB_LOOT("mob-loot"),
   /** XP vanilla (dari ore / mob) dikalikan sekian persen tambahan. */
   VANILLA_XP("vanilla-xp"),
   /** Peluang persen XP skill yang masuk menjadi dua kali lipat. */
   DOUBLE_XP("double-xp"),
   /** Pemulihan darah pasif (beberapa heart tiap interval) saat tidak sedang bertarung. */
   PASSIVE_HEAL("passive-heal"),
   /** Regenerasi alami diperkuat sekian persen. */
   REGEN_BOOST("regen-boost");

   private final String key;

   BuffKind(String key) {
      this.key = key;
   }

   /** Kunci yang dipakai pada pesan {@code skill.buff.<key>} dan {@code skill.buff-short.<key>}. */
   public String key() {
      return this.key;
   }

   /** Cari jenis buff dari teks config ("MELEE_DAMAGE", "melee-damage", "melee damage", ...). */
   public static BuffKind fromKey(String raw) {
      if (raw == null) {
         return null;
      }

      String cleaned = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
      if (cleaned.isEmpty()) {
         return null;
      }

      for (BuffKind kind : values()) {
         if (kind.name().equals(cleaned) || kind.key.toUpperCase(Locale.ROOT).replace('-', '_').equals(cleaned)) {
            return kind;
         }
      }

      return null;
   }

   /** Semua kunci jenis buff (untuk pesan diagnostik). */
   public static List<String> keys() {
      List<String> result = new ArrayList<>(values().length);

      for (BuffKind kind : values()) {
         result.add(kind.name());
      }

      return result;
   }

   /** Apakah jenis ini diterapkan lewat efek potion (butuh API potion server). */
   public boolean isPotion() {
      return this == POTION || this == HASTE;
   }

   /** Apakah jenis ini berupa peluang (dilempar dadu tiap kejadian), bukan nilai pasif. */
   public boolean isChance() {
      return this == CRIT_CHANCE || this == BLOCK_CHANCE || this == EXTRA_DROP || this == EXTRA_CATCH
         || this == MOB_LOOT || this == DOUBLE_XP;
   }
}
