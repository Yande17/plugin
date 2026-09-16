package me.w2n.w2nsmp.gui;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class SettingsMenuHolder implements InventoryHolder {
   private final UUID owner;
   private Inventory inventory;
   private boolean processing;
   private String pending;
   /** Kategori yang sedang dibuka ("" = halaman utama berisi kartu kategori, v1.4.0). */
   private String category = "";

   public SettingsMenuHolder(UUID owner) {
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

   public void pending(String key) {
      this.pending = key;
   }

   public String pending() {
      return this.pending;
   }

   public String category() {
      return this.category;
   }

   public void category(String category) {
      this.category = category == null ? "" : category;
   }
}
