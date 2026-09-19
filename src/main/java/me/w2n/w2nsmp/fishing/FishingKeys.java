package me.w2n.w2nsmp.fishing;

import me.w2n.w2nsmp.W2NSMP;
import org.bukkit.NamespacedKey;

/**
 * Kunci {@code PersistentDataContainer} untuk custom fishing & rod (v1.4.0).
 *
 * <p>Ikan custom dan item pancing diidentifikasi lewat PDC - bukan nama/lore - sehingga tetap
 * dikenali setelah pindah chest, diperdagangkan, atau server restart, dan tidak pernah
 * tertukar dengan ikan vanilla. Nama kunci tidak boleh diubah setelah rilis.
 */
public final class FishingKeys {
   /** Id ikan custom dari registry (string, mis. "golden_koi"). */
   public final NamespacedKey fishId;
   /** Kunci rarity ikan (string, mis. "legendary"). */
   public final NamespacedKey fishRarity;
   /** Ukuran ikan dalam cm (double). */
   public final NamespacedKey fishSize;
   /** Nilai jual ikan (long). */
   public final NamespacedKey fishValue;
   /** Id item pancing custom (string, mis. "rod_upgrade_crystal" / "lucky_hook"). */
   public final NamespacedKey itemId;
   /** Level rod (int, 1..maks). */
   public final NamespacedKey rodLevel;
   /** XP rod (double). */
   public final NamespacedKey rodXp;
   /** Attachment terpasang di rod (string csv id, mis. "lucky_hook,xp_reel"). */
   public final NamespacedKey rodAttachments;
   /** v1.9.0 (PHASE 5): berat nyata ikan dalam kg (double, desimal). */
   public final NamespacedKey fishWeight;

   public FishingKeys(W2NSMP plugin) {
      this.fishId = new NamespacedKey(plugin, "fish-id");
      this.fishRarity = new NamespacedKey(plugin, "fish-rarity");
      this.fishSize = new NamespacedKey(plugin, "fish-size");
      this.fishValue = new NamespacedKey(plugin, "fish-value");
      this.itemId = new NamespacedKey(plugin, "fishing-item");
      this.rodLevel = new NamespacedKey(plugin, "rod-level");
      this.rodXp = new NamespacedKey(plugin, "rod-xp");
      this.rodAttachments = new NamespacedKey(plugin, "rod-attachments");
      this.fishWeight = new NamespacedKey(plugin, "fish-weight");
   }
}
