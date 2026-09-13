package me.w2n.w2nsmp.gui;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class HomeMenuHolder implements InventoryHolder {
   private final UUID owner;
   private Inventory inventory;
   private boolean processing;

   public HomeMenuHolder(UUID owner) {
      super();
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
}
