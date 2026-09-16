package me.w2n.w2nsmp.gui;

import java.util.List;
import java.util.UUID;
import me.w2n.w2nsmp.auction.AuctionListing;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class AuctionMenuHolder implements InventoryHolder {
   private final UUID owner;
   private final int page;
   private final int pageSize;
   private final UUID sellerFilter;
   private List<Integer> listingIds = List.of();
   private Inventory inventory;
   private boolean processing;

   public AuctionMenuHolder(UUID owner, int page, int pageSize, UUID sellerFilter) {
      super();
      this.owner = owner;
      this.page = page;
      this.pageSize = pageSize;
      this.sellerFilter = sellerFilter;
   }

   public UUID owner() {
      return this.owner;
   }

   public int page() {
      return this.page;
   }

   public int pageSize() {
      return this.pageSize;
   }

   public UUID sellerFilter() {
      return this.sellerFilter;
   }

   public List<Integer> listingIds() {
      return this.listingIds;
   }

   public void listingIds(List<Integer> listingIds) {
      this.listingIds = List.copyOf(listingIds);
   }

   public int listingIdAt(int slotIndex) {
      return slotIndex >= 0 && slotIndex < this.listingIds.size() ? this.listingIds.get(slotIndex) : -1;
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

   public static String sellerOf(AuctionListing listing) {
      return listing.sellerName();
   }
}
