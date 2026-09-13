package me.w2n.w2nsmp.gui;

import java.util.UUID;
import me.w2n.w2nsmp.stats.TopCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class LeaderboardHolder implements InventoryHolder {
   private final UUID owner;
   private final int size;
   private final int pageSize;
   private Inventory inventory;
   private TopCategory category;
   private int page;
   private boolean categories;
   private boolean processing;

   public LeaderboardHolder(UUID owner, int size, int pageSize) {
      super();
      this.owner = owner;
      this.size = size;
      this.pageSize = pageSize;
   }

   public UUID owner() {
      return this.owner;
   }

   public boolean isOwner(Player player) {
      return player != null && player.getUniqueId().equals(this.owner);
   }

   public int size() {
      return this.size;
   }

   public int pageSize() {
      return this.pageSize;
   }

   public TopCategory category() {
      return this.category;
   }

   public void category(TopCategory category) {
      this.category = category;
   }

   public int page() {
      return this.page;
   }

   public void page(int page) {
      this.page = Math.max(1, page);
   }

   public boolean categories() {
      return this.categories;
   }

   public void categories(boolean categories) {
      this.categories = categories;
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
