package me.w2n.w2nsmp.fishing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;

/**
 * Definisi satu ikan custom dari config ({@code fishing.fish.<id>}, v1.4.0).
 *
 * <p>Semua nilai (nama, rarity, lore, ukuran, harga, XP, bobot, syarat biome/cuaca/waktu/level)
 * berasal dari config - menambah ikan baru tidak butuh perubahan kode.
 */
public final class CustomFish {
   private final String id;
   private final String name;
   private final FishRarity rarity;
   private final Material material;
   private final List<String> lore;
   private final double weight;
   private final double minSize;
   private final double maxSize;
   private final long baseValue;
   private final double xp;
   /** Kosong = semua biome. Cocok bila NAMA biome (uppercase) mengandung salah satu token. */
   private final List<String> biomes;
   /** "any" | "rain" | "clear" */
   private final String weather;
   /** "any" | "day" | "night" */
   private final String time;
   private final int minFishingLevel;
   private final int minRodLevel;

   public CustomFish(String id, String name, FishRarity rarity, Material material, List<String> lore,
                     double weight, double minSize, double maxSize, long baseValue, double xp,
                     List<String> biomes, String weather, String time, int minFishingLevel, int minRodLevel) {
      this.id = id;
      this.name = name == null || name.isEmpty() ? id : name;
      this.rarity = rarity == null ? FishRarity.COMMON : rarity;
      this.material = material == null ? Material.COD : material;
      this.lore = lore == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(lore));
      this.weight = Math.max(0.0D, weight);
      this.minSize = Math.max(0.1D, minSize);
      this.maxSize = Math.max(this.minSize, maxSize);
      this.baseValue = Math.max(0L, baseValue);
      this.xp = Math.max(0.0D, xp);
      List<String> upper = new ArrayList<>();
      if (biomes != null) {
         for (String biome : biomes) {
            if (biome != null && !biome.isEmpty()) {
               upper.add(biome.trim().toUpperCase(Locale.ROOT));
            }
         }
      }
      this.biomes = Collections.unmodifiableList(upper);
      this.weather = normalize(weather, "rain", "clear");
      this.time = normalize(time, "day", "night");
      this.minFishingLevel = Math.max(0, minFishingLevel);
      this.minRodLevel = Math.max(0, minRodLevel);
   }

   private static String normalize(String raw, String... allowed) {
      if (raw == null) {
         return "any";
      }

      String needle = raw.trim().toLowerCase(Locale.ROOT);

      for (String option : allowed) {
         if (option.equals(needle)) {
            return option;
         }
      }

      return "any";
   }

   public String id() {
      return this.id;
   }

   public String fishName() {
      return this.name;
   }

   public FishRarity rarity() {
      return this.rarity;
   }

   public Material material() {
      return this.material;
   }

   public List<String> lore() {
      return this.lore;
   }

   /** Bobot relatif dalam undian (0 = tidak pernah muncul). */
   public double weight() {
      return this.weight;
   }

   public double minSize() {
      return this.minSize;
   }

   public double maxSize() {
      return this.maxSize;
   }

   public long baseValue() {
      return this.baseValue;
   }

   public double xp() {
      return this.xp;
   }

   public List<String> biomes() {
      return this.biomes;
   }

   public String weather() {
      return this.weather;
   }

   public String time() {
      return this.time;
   }

   public int minFishingLevel() {
      return this.minFishingLevel;
   }

   public int minRodLevel() {
      return this.minRodLevel;
   }

   /** Cek syarat lingkungan (biome/cuaca/waktu). Nama biome dibandingkan case-insensitive. */
   public boolean matchesEnvironment(String biomeName, boolean storming, long worldTime) {
      if (!this.biomes.isEmpty()) {
         if (biomeName == null) {
            return false;
         }

         String upper = biomeName.toUpperCase(Locale.ROOT);
         boolean found = false;

         for (String token : this.biomes) {
            if (upper.contains(token)) {
               found = true;
               break;
            }
         }

         if (!found) {
            return false;
         }
      }

      if ("rain".equals(this.weather) && !storming) {
         return false;
      }

      if ("clear".equals(this.weather) && storming) {
         return false;
      }

      // Siang Minecraft: 0..12300; malam: sisanya (perkiraan kasar yang cukup untuk gameplay).
      long normalized = ((worldTime % 24000L) + 24000L) % 24000L;
      boolean day = normalized < 12300L;
      if ("day".equals(this.time) && !day) {
         return false;
      }

      return !"night".equals(this.time) || !day;
   }
}
