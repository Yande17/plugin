package me.w2n.w2nsmp.gui;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class BountyMenuHolder implements InventoryHolder {
   private final UUID owner;
   private BountyMenuHolder.Mode mode;
   private int page;
   private UUID subject;
   private String subjectName;
   private Inventory inventory;

   public BountyMenuHolder(UUID owner, BountyMenuHolder.Mode mode) {
      super();
      this.owner = owner;
      this.mode = mode;
      this.page = 1;
   }

   public UUID owner() {
      return this.owner;
   }

   public boolean isOwner(Player player) {
      return player != null && this.owner != null && this.owner.equals(player.getUniqueId());
   }

   public BountyMenuHolder.Mode mode() {
      return this.mode;
   }

   public void setMode(BountyMenuHolder.Mode mode) {
      this.mode = mode == null ? BountyMenuHolder.Mode.TOP : mode;
   }

   public int page() {
      return this.page;
   }

   public void setPage(int page) {
      this.page = Math.max(1, page);
   }

   public UUID subject() {
      return this.subject;
   }

   public String subjectName() {
      return this.subjectName;
   }

   public void setSubject(UUID uniqueId, String name) {
      this.subject = uniqueId;
      this.subjectName = name;
   }

   public void setInventory(Inventory inventory) {
      this.inventory = inventory;
   }

   public Inventory getInventory() {
      return this.inventory;
   }

   public enum Mode {
      TOP,
      TARGETS,
      AMOUNT;
   }
}
