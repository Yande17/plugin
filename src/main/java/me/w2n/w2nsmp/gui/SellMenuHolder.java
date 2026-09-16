package me.w2n.w2nsmp.gui;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class SellMenuHolder implements InventoryHolder {
   private final UUID owner;
   private final long openedAt = System.currentTimeMillis();
   private Inventory inventory;
   private boolean processing;
   private boolean itemsReturned;

   public SellMenuHolder(UUID owner) {
      super();
      this.owner = owner;
   }

   public UUID owner() {
      return this.owner;
   }

   public boolean isOwner(Player player) {
      return player != null && player.getUniqueId().equals(this.owner);
   }

   public long openedAt() {
      return this.openedAt;
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

   public boolean markItemsReturned() {
      if (this.itemsReturned) {
         return false;
      }

      this.itemsReturned = true;
      return true;
   }

   public boolean itemsReturned() {
      return this.itemsReturned;
   }
}
