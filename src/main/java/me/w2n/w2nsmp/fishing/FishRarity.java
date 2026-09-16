package me.w2n.w2nsmp.fishing;

import java.util.Locale;

/**
 * Rarity ikan custom (v1.4.0), dari paling umum ke paling langka.
 *
 * <p>Enum hanya menetapkan URUTAN & warna bawaan; angka penting (gerbang level fishing per
 * rarity, bobot per ikan) seluruhnya dari config.
 */
public enum FishRarity {
   COMMON("common", "&f"),
   UNCOMMON("uncommon", "&a"),
   RARE("rare", "&9"),
   EPIC("epic", "&5"),
   LEGENDARY("legendary", "&6"),
   MYTHIC("mythic", "&d");

   private final String key;
   private final String color;

   private FishRarity(String key, String color) {
      this.key = key;
      this.color = color;
   }

   public String key() {
      return this.key;
   }

   /** Kode warna bawaan (dipakai bila messages tidak menyediakan nama berwarna). */
   public String color() {
      return this.color;
   }

   /** Kunci pesan nama rarity ("fishing.rarity.common" dst). */
   public String messageKey() {
      return "fishing.rarity." + this.key;
   }

   public static FishRarity fromKey(String raw) {
      if (raw == null) {
         return null;
      }

      String needle = raw.trim().toLowerCase(Locale.ROOT);

      for (FishRarity rarity : values()) {
         if (rarity.key.equals(needle)) {
            return rarity;
         }
      }

      return null;
   }
}
