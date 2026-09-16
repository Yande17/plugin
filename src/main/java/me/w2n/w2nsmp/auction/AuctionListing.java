package me.w2n.w2nsmp.auction;

import java.util.UUID;
import org.bukkit.inventory.ItemStack;

public final class AuctionListing {
   private final int id;
   private final UUID sellerId;
   private String sellerName;
   private final ItemStack item;
   private final long price;
   private final long createdAt;
   private long expiresAt;

   AuctionListing(int id, UUID sellerId, String sellerName, ItemStack item, long price, long createdAt, long expiresAt) {
      super();
      this.id = id;
      this.sellerId = sellerId;
      this.sellerName = sellerName;
      this.item = item;
      this.price = price;
      this.createdAt = createdAt;
      this.expiresAt = expiresAt;
   }

   public int id() {
      return this.id;
   }

   public UUID sellerId() {
      return this.sellerId;
   }

   public String sellerName() {
      return this.sellerName;
   }

   void sellerName(String name) {
      this.sellerName = name;
   }

   public ItemStack item() {
      return this.item.clone();
   }

   public long price() {
      return this.price;
   }

   public long createdAt() {
      return this.createdAt;
   }

   public long expiresAt() {
      return this.expiresAt;
   }

   void expiresAt(long expiresAt) {
      this.expiresAt = expiresAt;
   }

   public boolean isExpired(long now) {
      return this.expiresAt <= now;
   }

   public long remainingMillis(long now) {
      return Math.max(0L, this.expiresAt - now);
   }
}
