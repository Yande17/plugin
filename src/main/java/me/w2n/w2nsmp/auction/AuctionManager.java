package me.w2n.w2nsmp.auction;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.stats.StatType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

public final class AuctionManager {
   private final W2NSMP plugin;
   private final AuctionStorage storage;
   private final Map<Integer, AuctionListing> listings = new LinkedHashMap<>();
   private final Map<UUID, List<ItemStack>> mailbox = new HashMap<>();
   private final Set<Integer> processing = new HashSet<>();
   private int nextId = 1;
   private BukkitTask expiryTask;

   public AuctionManager(W2NSMP plugin) {
      super();
      this.plugin = plugin;
      this.storage = new AuctionStorage(plugin);
      this.startExpiryTask();
   }

   public void load() {
      this.listings.clear();
      this.mailbox.clear();
      AuctionStorage.Snapshot snapshot = this.storage.load();
      this.listings.putAll(snapshot.listings());
      this.mailbox.putAll(snapshot.mailbox());
      this.nextId = Math.max(1, snapshot.nextId());
   }

   public void reload() {
      this.persistNow();
      this.load();
   }

   public boolean persistNow() {
      return this.storage.write(this.storage.serialize(Collections.unmodifiableMap(this.listings), Collections.unmodifiableMap(this.mailbox), this.nextId));
   }

   public File storageFile() {
      return this.storage.file();
   }

   public void shutdown() {
      if (this.expiryTask != null) {
         this.expiryTask.cancel();
         this.expiryTask = null;
      }

      this.persistNow();
   }

   public void clearState() {
      this.listings.clear();
      this.mailbox.clear();
      this.processing.clear();
      this.nextId = 1;
   }

   private void startExpiryTask() {
      long period = Math.max(5, this.plugin.config().auctionExpiryCheckSeconds()) * 20L;
      this.expiryTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::sweepExpired, period, period);
   }

   public Collection<Integer> listingIds() {
      return Collections.unmodifiableCollection(new ArrayList<>(this.listings.keySet()));
   }

   public List<AuctionListing> listings() {
      return new ArrayList<>(this.listings.values());
   }

   public AuctionListing listing(int id) {
      return this.listings.get(id);
   }

   public List<AuctionListing> listingsOf(UUID sellerId) {
      List<AuctionListing> result = new ArrayList<>();

      for (AuctionListing listing : this.listings.values()) {
         if (listing.sellerId().equals(sellerId)) {
            result.add(listing);
         }
      }

      return result;
   }

   public int countOf(UUID sellerId) {
      return this.listingsOf(sellerId).size();
   }

   public int listingCount() {
      return this.listings.size();
   }

   public int mailboxCount(UUID uniqueId) {
      List<ItemStack> items = this.mailbox.get(uniqueId);
      if (items != null && !items.isEmpty()) {
         int total = 0;

         for (ItemStack item : items) {
            total += item.getAmount();
         }

         return total;
      } else {
         return 0;
      }
   }

   public List<ItemStack> mailbox(UUID uniqueId) {
      List<ItemStack> stored = this.mailbox.get(uniqueId);
      if (stored != null && !stored.isEmpty()) {
         List<ItemStack> copies = new ArrayList<>(stored.size());

         for (ItemStack item : stored) {
            copies.add(item.clone());
         }

         return copies;
      } else {
         return List.of();
      }
   }

   public int nextListingId() {
      return this.nextId;
   }

   public int mailboxTotal() {
      int total = 0;

      for (UUID uniqueId : new ArrayList<>(this.mailbox.keySet())) {
         total += this.mailboxCount(uniqueId);
      }

      return total;
   }

   public int expiredCount() {
      long now = System.currentTimeMillis();
      int count = 0;

      for (AuctionListing listing : this.listings.values()) {
         if (listing.isExpired(now)) {
            count++;
         }
      }

      return count;
   }

   public long taxFor(long price) {
      int percent = this.plugin.config().auctionTaxPercent();
      return Math.max(0L, price * percent / 100L);
   }

   public long netFor(long price) {
      return Math.max(0L, price - this.taxFor(price));
   }

   public AuctionManager.ListResult list(Player seller, long price) {
      if (!this.plugin.config().auctionEnabled()) {
         return AuctionManager.ListResult.DISABLED;
      }

      if (!this.plugin.economy().isEnabled()) {
         return AuctionManager.ListResult.ECONOMY_DISABLED;
      }

      ItemStack hand = seller.getInventory().getItemInMainHand();
      if (hand != null && !hand.getType().isAir() && hand.getAmount() > 0) {
         Material material = hand.getType();
         if (this.plugin.config().auctionBlacklist().contains(material)) {
            return AuctionManager.ListResult.BLACKLISTED;
         }

         long min = this.plugin.config().auctionMinPrice();
         long max = this.plugin.config().auctionMaxPrice();
         if (price < min) {
            return AuctionManager.ListResult.PRICE_TOO_LOW;
         }

         if (max > 0L && price > max) {
            return AuctionManager.ListResult.PRICE_TOO_HIGH;
         }

         int limit = this.maxListings(seller);
         if (this.countOf(seller.getUniqueId()) >= limit) {
            return AuctionManager.ListResult.LIMIT_REACHED;
         }

         long fee = Math.max(0L, this.plugin.config().auctionListingFee());
         if (fee > 0L) {
            if (!this.plugin.economy().has(seller, fee)) {
               return AuctionManager.ListResult.FEE_FAILED;
            }

            if (!this.plugin.economy().withdraw(seller, fee)) {
               return AuctionManager.ListResult.FEE_FAILED;
            }
         }

         long now = System.currentTimeMillis();
         long durationMillis = Math.max(1L, this.plugin.config().auctionExpirationHours()) * 3600000L;
         int id = this.nextId++;
         AuctionListing listing = new AuctionListing(id, seller.getUniqueId(), seller.getName(), hand.clone(), price, now, now + durationMillis);
         this.listings.put(id, listing);
         if (!this.persistNow()) {
            this.listings.remove(id);
            this.nextId = id;
            if (fee > 0L) {
               this.plugin.economy().deposit(seller, fee);
            }

            return AuctionManager.ListResult.SAVE_FAILED;
         } else {
            int remaining = hand.getAmount() - listing.item().getAmount();
            if (remaining <= 0) {
               seller.getInventory().setItemInMainHand(null);
            } else {
               ItemStack rest = hand.clone();
               rest.setAmount(remaining);
               seller.getInventory().setItemInMainHand(rest);
            }

            this.plugin
               .messages()
               .send(
                  seller,
                  "auction.list-success",
                  "price",
                  this.plugin.economy().format(price),
                  "net",
                  this.plugin.economy().format(this.netFor(price)),
                  "tax",
                  this.plugin.economy().format(this.taxFor(price)),
                  "id",
                  Integer.toString(id),
                  "item",
                  itemName(listing.item()),
                  "amount",
                  Integer.toString(listing.item().getAmount())
               );
            if (this.plugin.stats() != null) {
               this.plugin.stats().add(seller, StatType.AUCTION_LISTED, 1L);
            }

            this.plugin
               .debug("Auction: listing #" + id + " oleh " + seller.getName() + " (" + material + " x" + listing.item().getAmount() + ") harga " + price + ".");
            return AuctionManager.ListResult.SUCCESS;
         }
      } else {
         return AuctionManager.ListResult.NO_ITEM_IN_HAND;
      }
   }

   public boolean beginTransaction(int id) {
      return this.processing.add(id);
   }

   public void endTransaction(int id) {
      this.processing.remove(id);
   }

   public boolean isProcessing(int id) {
      return this.processing.contains(id);
   }

   public int maxListings(Player player) {
      return player.hasPermission("w2nsmp.auction.unlimited") ? Integer.MAX_VALUE : Math.max(1, this.plugin.config().auctionMaxListings());
   }

   public AuctionManager.BuyResult buy(Player buyer, int id) {
      if (!this.plugin.config().auctionEnabled()) {
         return AuctionManager.BuyResult.DISABLED;
      }

      AuctionListing listing = this.listings.get(id);
      if (listing == null) {
         return AuctionManager.BuyResult.NOT_FOUND;
      }

      if (!this.beginTransaction(id)) {
         return AuctionManager.BuyResult.BUSY;
      }

      try {
         if (!this.plugin.economy().isEnabled()) {
            return AuctionManager.BuyResult.ECONOMY_DISABLED;
         }

         if (listing.sellerId().equals(buyer.getUniqueId()) && !this.plugin.config().auctionAllowSelfPurchase()) {
            return AuctionManager.BuyResult.OWN_LISTING;
         }

         long price = listing.price();
         if (!this.plugin.economy().has(buyer, price)) {
            return AuctionManager.BuyResult.INSUFFICIENT_FUNDS;
         }

         ItemStack item = listing.item();
         if (!hasSpace(buyer.getInventory(), item)) {
            return AuctionManager.BuyResult.NO_SPACE;
         }

         if (!this.plugin.economy().withdraw(buyer, price)) {
            return AuctionManager.BuyResult.WITHDRAW_FAILED;
         }

         long net = this.netFor(price);
         OfflinePlayer seller = Bukkit.getOfflinePlayer(listing.sellerId());
         if (net > 0L && !this.plugin.economy().deposit(seller, net)) {
            this.plugin.economy().deposit(buyer, price);
            return AuctionManager.BuyResult.DEPOSIT_FAILED;
         }

         this.listings.remove(id);
         if (!this.persistNow()) {
            this.listings.put(id, listing);
            if (net > 0L) {
               this.plugin.economy().withdraw(seller, net);
            }

            this.plugin.economy().deposit(buyer, price);
            return AuctionManager.BuyResult.SAVE_FAILED;
         } else {
            int mailed = this.giveOrMailbox(buyer, item);
            this.notifySale(buyer, listing, item, net, mailed);
            if (this.plugin.stats() != null) {
               this.plugin.stats().add(buyer, StatType.AUCTION_BOUGHT, 1L);
               this.plugin.stats().add(listing.sellerId(), StatType.AUCTION_SOLD, 1L);
               this.plugin.stats().add(listing.sellerId(), StatType.MONEY_EARNED, net);
               this.plugin.stats().saveAsync();
            }

            return AuctionManager.BuyResult.SUCCESS;
         }
      } finally {
         this.endTransaction(id);
      }
   }

   public AuctionManager.CancelResult cancel(Player seller, int id) {
      AuctionListing listing = this.listings.get(id);
      if (listing == null) {
         return AuctionManager.CancelResult.NOT_FOUND;
      }

      if (!listing.sellerId().equals(seller.getUniqueId())) {
         return AuctionManager.CancelResult.NOT_OWNER;
      }

      if (!this.beginTransaction(id)) {
         return AuctionManager.CancelResult.BUSY;
      }

      try {
         this.listings.remove(id);
         if (!this.persistNow()) {
            this.listings.put(id, listing);
            return AuctionManager.CancelResult.SAVE_FAILED;
         }

         int mailed = this.giveOrMailbox(seller, listing.item());
         if (mailed > 0) {
            this.plugin.messages().send(seller, "auction.cancel-mailed", "amount", Integer.toString(mailed), "id", Integer.toString(id));
         } else {
            this.plugin.messages().send(seller, "auction.cancel-success", "id", Integer.toString(id));
         }

         this.plugin.debug("Auction: listing #" + id + " dibatalkan oleh " + seller.getName() + ".");
         return AuctionManager.CancelResult.SUCCESS;
      } finally {
         this.endTransaction(id);
      }
   }

   public int sweepExpired() {
      long now = System.currentTimeMillis();
      List<AuctionListing> expired = new ArrayList<>();

      for (AuctionListing listing : this.listings.values()) {
         if (listing.isExpired(now)) {
            expired.add(listing);
         }
      }

      if (expired.isEmpty()) {
         return 0;
      }

      for (AuctionListing listing : expired) {
         this.returnToSeller(listing);
      }

      this.persistNow();
      this.plugin.getLogger().info("Auction: " + expired.size() + " listing kedaluwarsa dipindahkan ke kotak penjual.");
      return expired.size();
   }

   public int forceExpireAll() {
      List<AuctionListing> all = new ArrayList<>(this.listings.values());
      if (all.isEmpty()) {
         return 0;
      }

      for (AuctionListing listing : all) {
         this.returnToSeller(listing);
      }

      this.persistNow();
      this.plugin.getLogger().info("Auction: " + all.size() + " listing dikembalikan ke penjual (mode paksa).");
      return all.size();
   }

   private void returnToSeller(AuctionListing listing) {
      this.listings.remove(listing.id());
      this.addToMailbox(listing.sellerId(), listing.item());
      Player online = Bukkit.getPlayer(listing.sellerId());
      if (online != null && online.isOnline()) {
         this.plugin
            .messages()
            .send(
               online,
               "auction.expired",
               "id",
               Integer.toString(listing.id()),
               "item",
               itemName(listing.item()),
               "amount",
               Integer.toString(listing.item().getAmount())
            );
      }
   }

   public AuctionManager.CollectResult collect(Player player) {
      List<ItemStack> items = this.mailbox.get(player.getUniqueId());
      if (items != null && !items.isEmpty()) {
         int given = 0;
         List<ItemStack> remaining = new ArrayList<>();

         for (ItemStack item : items) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack[]{item});
            if (leftover.isEmpty()) {
               given += item.getAmount();
            } else {
               for (ItemStack rest : leftover.values()) {
                  given += item.getAmount() - rest.getAmount();
                  remaining.add(rest);
               }
            }
         }

         if (remaining.isEmpty()) {
            this.mailbox.remove(player.getUniqueId());
         } else {
            this.mailbox.put(player.getUniqueId(), remaining);
         }

         this.persistNow();
         if (given == 0) {
            return AuctionManager.CollectResult.PARTIAL;
         } else {
            return remaining.isEmpty() ? AuctionManager.CollectResult.COLLECTED : AuctionManager.CollectResult.PARTIAL;
         }
      } else {
         return AuctionManager.CollectResult.NOTHING;
      }
   }

   public AuctionListing forceRemove(int id) {
      AuctionListing listing = this.listings.remove(id);
      if (listing == null) {
         return null;
      }

      this.addToMailbox(listing.sellerId(), listing.item());
      this.persistNow();
      return listing;
   }

   public static boolean hasSpace(PlayerInventory inventory, ItemStack item) {
      if (item != null && !item.getType().isAir()) {
         int remaining = item.getAmount();
         int maxStack = Math.max(1, item.getMaxStackSize());

         for (ItemStack existing : inventory.getStorageContents()) {
            if (existing == null || existing.getType().isAir()) {
               remaining -= maxStack;
               if (remaining <= 0) {
                  return true;
               }
            } else if (existing.isSimilar(item) && existing.getAmount() < maxStack) {
               remaining -= maxStack - existing.getAmount();
               if (remaining <= 0) {
                  return true;
               }
            }
         }

         return remaining <= 0;
      } else {
         return true;
      }
   }

   private int giveOrMailbox(Player player, ItemStack item) {
      Map<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack[]{item});
      if (leftover.isEmpty()) {
         return 0;
      }

      int mailed = 0;

      for (ItemStack rest : leftover.values()) {
         mailed += rest.getAmount();
         this.addToMailbox(player.getUniqueId(), rest);
      }

      this.plugin
         .getLogger()
         .warning("Auction: " + mailed + " item untuk " + player.getName() + " tidak muat di inventory dan dimasukkan ke kotak (/ah collect).");
      return mailed;
   }

   private void addToMailbox(UUID uniqueId, ItemStack item) {
      List<ItemStack> items = this.mailbox.computeIfAbsent(uniqueId, key -> new ArrayList<>());
      int limit = Math.max(1, this.plugin.config().auctionMailboxLimit());
      if (items.size() < limit) {
         items.add(item.clone());
      } else {
         Player online = Bukkit.getPlayer(uniqueId);
         if (online != null && online.isOnline()) {
            online.getWorld().dropItemNaturally(online.getLocation(), item);
            this.plugin.messages().send(online, "auction.mailbox-full-drop");
         } else {
            this.plugin
               .getLogger()
               .warning("Kotak auction penuh untuk " + uniqueId + " dan pemain tidak online; item tetap disimpan di kotak (melebihi batas).");
            items.add(item.clone());
         }
      }
   }

   public static String itemName(ItemStack item) {
      return item == null ? "?" : item.getType().name();
   }

   private void notifySale(Player buyer, AuctionListing listing, ItemStack item, long net, int mailed) {
      this.plugin
         .messages()
         .send(
            buyer,
            "auction.buy-success",
            "item",
            itemName(item),
            "amount",
            Integer.toString(item.getAmount()),
            "price",
            this.plugin.economy().format(listing.price()),
            "seller",
            listing.sellerName(),
            "id",
            Integer.toString(listing.id())
         );
      if (mailed > 0) {
         this.plugin.messages().send(buyer, "auction.buy-mailed", "amount", Integer.toString(mailed));
      }

      Player seller = Bukkit.getPlayer(listing.sellerId());
      if (seller != null && seller.isOnline()) {
         this.plugin
            .messages()
            .send(
               seller,
               "auction.sold-notify",
               "item",
               itemName(item),
               "amount",
               Integer.toString(item.getAmount()),
               "price",
               this.plugin.economy().format(listing.price()),
               "net",
               this.plugin.economy().format(net),
               "tax",
               this.plugin.economy().format(listing.price() - net),
               "buyer",
               buyer.getName()
            );
      }

      this.plugin
         .getLogger()
         .info(
            "Auction: "
               + buyer.getName()
               + " membeli listing #"
               + listing.id()
               + " ("
               + itemName(item)
               + ") dari "
               + listing.sellerName()
               + " seharga "
               + listing.price()
               + " (bersih "
               + net
               + ")."
         );
   }

   public enum BuyResult {
      SUCCESS,
      DISABLED,
      NOT_FOUND,
      OWN_LISTING,
      ECONOMY_DISABLED,
      INSUFFICIENT_FUNDS,
      NO_SPACE,
      BUSY,
      WITHDRAW_FAILED,
      DEPOSIT_FAILED,
      SAVE_FAILED;
   }

   public enum CancelResult {
      SUCCESS,
      NOT_FOUND,
      NOT_OWNER,
      BUSY,
      SAVE_FAILED;
   }

   public enum CollectResult {
      NOTHING,
      COLLECTED,
      PARTIAL;
   }

   public enum ListResult {
      SUCCESS,
      DISABLED,
      ECONOMY_DISABLED,
      NO_ITEM_IN_HAND,
      BLACKLISTED,
      PRICE_TOO_LOW,
      PRICE_TOO_HIGH,
      LIMIT_REACHED,
      FEE_FAILED,
      SAVE_FAILED;
   }
}
