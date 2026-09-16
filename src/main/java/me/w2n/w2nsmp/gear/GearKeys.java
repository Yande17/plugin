package me.w2n.w2nsmp.gear;

import me.w2n.w2nsmp.W2NSMP;
import org.bukkit.NamespacedKey;

/**
 * Kunci {@code PersistentDataContainer} untuk riwayat gear (v1.4.0).
 *
 * <p>Semua riwayat item (pemilik pertama, jumlah kill, pemakaian) disimpan DI ITEM lewat PDC -
 * bukan display name, bukan pemetaan UUID pemain - sehingga rename anvil, perpindahan chest,
 * jual-beli, dan restart server tidak menghapus datanya. Nama kunci tidak boleh diubah setelah
 * rilis: item lama akan kehilangan riwayat kalau kuncinya berganti.
 */
public final class GearKeys {
   /** UUID pemilik pertama (string). */
   public final NamespacedKey firstOwner;
   /** Nama pemilik pertama saat itu (string, hanya untuk lore - UUID tetap sumber kebenaran). */
   public final NamespacedKey firstOwnerName;
   /** Jumlah kill PEMAIN dengan item ini (int). */
   public final NamespacedKey kills;
   /** Jumlah pemakaian: pukulan/tembakan untuk senjata, damage diterima untuk armor (int). */
   public final NamespacedKey uses;
   /** Skill yang disyaratkan item ini (string kunci skill, mis. "fighting"). */
   public final NamespacedKey requireSkill;
   /** Level skill minimum yang disyaratkan (int; 0 = tanpa syarat). */
   public final NamespacedKey requireLevel;
   /** Jumlah baris lore riwayat yang terakhir ditulis (int) - dipakai untuk menimpa ulang. */
   public final NamespacedKey loreLines;

   public GearKeys(W2NSMP plugin) {
      this.firstOwner = new NamespacedKey(plugin, "gear-first-owner");
      this.firstOwnerName = new NamespacedKey(plugin, "gear-first-owner-name");
      this.kills = new NamespacedKey(plugin, "gear-kills");
      this.uses = new NamespacedKey(plugin, "gear-uses");
      this.requireSkill = new NamespacedKey(plugin, "gear-require-skill");
      this.requireLevel = new NamespacedKey(plugin, "gear-require-level");
      this.loreLines = new NamespacedKey(plugin, "gear-lore-lines");
   }
}
