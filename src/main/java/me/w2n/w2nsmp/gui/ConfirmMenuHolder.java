package me.w2n.w2nsmp.gui;

import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ConfirmMenuHolder implements InventoryHolder {
   private final UUID owner;
   private final Consumer<Player> onConfirm;
   private final Consumer<Player> onCancel;
   private Inventory inventory;
   private boolean processing;

   public ConfirmMenuHolder(UUID owner, Consumer<Player> onConfirm, Consumer<Player> onCancel) {
      super();
      this.owner = owner;
      this.onConfirm = onConfirm;
      this.onCancel = onCancel;
   }

   public UUID owner() {
      return this.owner;
   }

   public boolean isOwner(Player player) {
      return player != null && player.getUniqueId().equals(this.owner);
   }

   public Consumer<Player> onConfirm() {
      return this.onConfirm;
   }

   public Consumer<Player> onCancel() {
      return this.onCancel;
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
}
