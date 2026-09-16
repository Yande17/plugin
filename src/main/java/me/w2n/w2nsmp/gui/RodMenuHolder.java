package me.w2n.w2nsmp.gui;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Pemegang inventory menu rod (/rod, v1.4.0).
 *
 * <p>Menu ini BEKERJA PADA ROD DI TANGAN UTAMA pemilik: setiap klik membaca ulang item di
 * tangan, jadi tidak ada referensi item yang bisa basi/duplikat. Pola
 * {@code beginProcessing}/{@code pending} meniru holder GUI lain untuk menahan klik ganda.
 */
public final class RodMenuHolder implements InventoryHolder {
   private final UUID owner;
   private Inventory inventory;
   private boolean processing;
   private String pending;

   public RodMenuHolder(UUID owner) {
      this.owner = owner;
   }

   public UUID owner() {
      return this.owner;
   }

   public boolean isOwner(Player player) {
      return player != null && player.getUniqueId().equals(this.owner);
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

   public void setInventory(Inventory inventory) {
      this.inventory = inventory;
   }

   @Override
   public Inventory getInventory() {
      return this.inventory;
   }
}
