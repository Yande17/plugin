package me.w2n.w2nsmp.gui;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ProfileMenuHolder implements InventoryHolder {
   private final UUID owner;
   private UUID subject;
   private Inventory inventory;
   private boolean processing;

   public ProfileMenuHolder(UUID owner) {
      super();
      this.owner = owner;
   }

   public UUID owner() {
      return this.owner;
   }

   public void subject(UUID uniqueId) {
      this.subject = uniqueId;
   }

   public UUID subject() {
      return this.subject;
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
}
