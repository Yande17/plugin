package me.w2n.w2nsmp.skill;

/**
 * Jenis buff yang diberikan sebuah skill.
 *
 * <p>Setiap jenis punya satu tempat penerapan: sebagian lewat perhitungan event (damage),
 * sebagian lewat tugas berkala (regen), dan satu lewat efek potion yang dipasang dengan
 * refleksi (haste). Semua nilai diambil dari {@code config.yml} bagian {@code skills.list.<key>.buff}.
 */
public enum BuffKind {
   /** Damage serangan jarak dekat (tangan/pedang) ditambah sekian persen. */
   MELEE_DAMAGE("damage-melee"),
   /** Damage proyektil (panah/trident) ditambah sekian persen. */
   PROJECTILE_DAMAGE("damage-projectile"),
   /** Damage yang diterima pemain dikurangi sekian persen. */
   DAMAGE_REDUCTION("damage-reduction"),
   /** Damage dari penyebab lingkungan (jatuh, api, tenggelam, ...) dikurangi sekian persen. */
   ENVIRONMENT_REDUCTION("environment-reduction"),
   /** Kecepatan jalan pemain ditambah sekian persen. */
   WALK_SPEED("walk-speed"),
   /** Efek Haste (kecepatan tambang) tingkat tertentu. */
   HASTE("haste"),
   /** Peluang persen mendapat satu drop tambahan saat menebang/memanen. */
   EXTRA_DROP("extra-drop"),
   /** Peluang persen hasil pancingan ganda. */
   EXTRA_CATCH("extra-catch"),
   /** Pemulihan darah pasif (beberapa heart tiap interval) saat tidak sedang bertarung. */
   PASSIVE_HEAL("passive-heal"),
   /** Regenerasi alami diperkuat sekian persen. */
   REGEN_BOOST("regen-boost");

   private final String key;

   BuffKind(String key) {
      this.key = key;
   }

   /** Kunci yang dipakai pada pesan {@code skill.buff.<key>} di messages.yml. */
   public String key() {
      return this.key;
   }
}
