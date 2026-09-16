package me.w2n.w2nsmp.gear;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;

/**
 * Satu kelas gear dari config (v1.4.0): daftar material + counter yang dilacak + milestone perk.
 *
 * <p>Seluruhnya dimuat dari {@code gear.classes.<nama>} - menambah kelas baru (mis. trident)
 * cukup lewat config tanpa mengubah kode.
 */
public final class GearClass {
   private final String name;
   private final Set<Material> materials;
   /** "kills" atau "uses" - counter mana yang membuka milestone. */
   private final String counter;
   /** "weapon" (perk menyerang) atau "armor" (perk bertahan). */
   private final String slot;
   private final String requireSkill;
   private final int requireLevel;
   private final List<GearPerk> perks;

   public GearClass(String name, Set<Material> materials, String counter, String slot,
                    String requireSkill, int requireLevel, List<GearPerk> perks) {
      this.name = name == null ? "?" : name;
      this.materials = materials == null ? new LinkedHashSet<>() : materials;
      this.counter = "uses".equalsIgnoreCase(counter) ? "uses" : "kills";
      this.slot = "armor".equalsIgnoreCase(slot) ? "armor" : "weapon";
      this.requireSkill = requireSkill == null ? "" : requireSkill.toLowerCase(Locale.ROOT);
      this.requireLevel = Math.max(0, requireLevel);
      List<GearPerk> sorted = perks == null ? new ArrayList<>() : new ArrayList<>(perks);
      sorted.sort((a, b) -> Integer.compare(a.threshold(), b.threshold()));
      this.perks = Collections.unmodifiableList(sorted);
   }

   public String name() {
      return this.name;
   }

   public boolean matches(Material material) {
      return material != null && this.materials.contains(material);
   }

   public String counter() {
      return this.counter;
   }

   public boolean isArmor() {
      return "armor".equals(this.slot);
   }

   public String requireSkill() {
      return this.requireSkill;
   }

   public int requireLevel() {
      return this.requireLevel;
   }

   public List<GearPerk> perks() {
      return this.perks;
   }

   /** Perk yang sudah terbuka pada nilai counter tertentu (urut naik). */
   public List<GearPerk> unlocked(int count) {
      List<GearPerk> result = new ArrayList<>();

      for (GearPerk perk : this.perks) {
         if (perk.threshold() <= count) {
            result.add(perk);
         }
      }

      return result;
   }

   /** Milestone berikutnya yang belum tercapai (null = semuanya terbuka). */
   public GearPerk next(int count) {
      for (GearPerk perk : this.perks) {
         if (perk.threshold() > count) {
            return perk;
         }
      }

      return null;
   }

   public Set<Material> materials() {
      return this.materials;
   }
}
