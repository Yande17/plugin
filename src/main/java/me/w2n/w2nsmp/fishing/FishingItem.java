package me.w2n.w2nsmp.fishing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;

/**
 * Definisi satu item pancing custom dari config ({@code fishing.items.<id>}, v1.4.0):
 * bahan upgrade rod (Rod Upgrade Crystal, Fishing Core, ...) dan attachment
 * (Lucky Hook, XP Reel, ...). Identitas item disimpan di PDC, bukan nama.
 */
public final class FishingItem {
   private final String id;
   private final String name;
   private final Material material;
   private final List<String> lore;
   /** "upgrade" (bahan upgrade rod) atau "attachment" (dipasang di rod). */
   private final String kind;
   /** Efek attachment: bite-speed | custom-chance | rarity-boost | value | xp | double-catch. */
   private final String effect;
   private final double value;
   /** Peluang (persen) item ini ikut didapat saat menangkap ikan custom (0 = tidak pernah). */
   private final double dropChance;

   public FishingItem(String id, String name, Material material, List<String> lore,
                      String kind, String effect, double value, double dropChance) {
      this.id = id;
      this.name = name == null || name.isEmpty() ? id : name;
      this.material = material == null ? Material.PRISMARINE_SHARD : material;
      this.lore = lore == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(lore));
      this.kind = "attachment".equalsIgnoreCase(kind) ? "attachment" : "upgrade";
      this.effect = effect == null ? "" : effect.trim().toLowerCase(Locale.ROOT);
      this.value = Math.max(0.0D, value);
      this.dropChance = Math.max(0.0D, Math.min(100.0D, dropChance));
   }

   public double dropChance() {
      return this.dropChance;
   }

   public String id() {
      return this.id;
   }

   public String itemName() {
      return this.name;
   }

   public Material material() {
      return this.material;
   }

   public List<String> lore() {
      return this.lore;
   }

   public boolean isAttachment() {
      return "attachment".equals(this.kind);
   }

   public String effect() {
      return this.effect;
   }

   public double value() {
      return this.value;
   }
}
