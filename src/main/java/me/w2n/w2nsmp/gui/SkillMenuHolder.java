package me.w2n.w2nsmp.gui;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Pemegang inventory GUI {@code /skill}.
 *
 * <p>Keberadaan holder inilah yang menandai "inventory ini milik W2NSMP", sehingga
 * {@code SkillGuiListener} hanya membatalkan klik di menu skill dan tidak pernah menyentuh
 * inventory plugin lain atau inventory vanilla.
 */
public final class SkillMenuHolder implements InventoryHolder {
   private final UUID owner;
   private Inventory inventory;
   private boolean processing;
   private String pending;

   public SkillMenuHolder(UUID owner) {
      this.owner = owner;
   }

   public UUID owner() {
      return this.owner;
   }

   public boolean isOwner(Player player) {
      return player != null && player.getUniqueId().equals(this.owner);
   }

   public Inventory getInventory() {
      return this.inventory;
   }

   public void setInventory(Inventory inventory) {
      this.inventory = inventory;
   }

   public boolean beginProcessing() {
      if (this.processing) {
         return false;
      }

      this.processing = true;
      return true;
   }

   public void endProcessing() {
      this.processing = false;
   }

   public void pending(String key) {
      this.pending = key;
   }

   public String pending() {
      return this.pending;
   }
}
