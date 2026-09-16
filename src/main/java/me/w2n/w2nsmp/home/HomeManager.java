package me.w2n.w2nsmp.home;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.stats.StatType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class HomeManager {
   private static final int MAX_NAME_LENGTH = 16;
   private final W2NSMP plugin;
   private final HomeStorage storage;
   private final Map<UUID, PlayerHomes> cache = new ConcurrentHashMap<>();
   private final AtomicLong writeVersion = new AtomicLong();
   private final AtomicLong writtenVersion = new AtomicLong();
   private final Object writeLock = new Object();
   private int freeSlots = 3;
   private int maxSlots = 8;

   public HomeManager(W2NSMP plugin) {
      super();
      this.plugin = plugin;
      this.storage = new HomeStorage(plugin);
   }

   public void load() {
      this.cache.clear();
      this.cache.putAll(this.storage.load());
      this.applyConfigLimits();
   }

   public void reload() {
      this.saveNow();
      this.load();
   }

   public void saveNow() {
      long version = this.writeVersion.incrementAndGet();
      synchronized (this.writeLock) {
         if (this.storage.write(this.storage.serialize(Collections.unmodifiableMap(this.cache)))) {
            this.writtenVersion.set(version);
         }
      }
   }

   public void saveAsync() {
      long version = this.writeVersion.incrementAndGet();
      String content = this.storage.serialize(Collections.unmodifiableMap(this.cache));
      Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
         synchronized (this.writeLock) {
            if (version >= this.writtenVersion.get()) {
               if (this.storage.write(content)) {
                  this.writtenVersion.set(version);
               }
            }
         }
      });
   }

   private void applyConfigLimits() {
      this.freeSlots = Math.max(1, this.plugin.config().homeFreeSlots());
      this.maxSlots = Math.max(this.freeSlots, this.plugin.config().homeMaxSlots());

      for (PlayerHomes data : this.cache.values()) {
         if (data.unlocked() < this.freeSlots) {
            data.unlocked(this.freeSlots);
         }

         if (data.unlocked() > this.maxSlots) {
            data.unlocked(this.maxSlots);
         }
      }
   }

   public int freeSlots() {
      return this.freeSlots;
   }

   public int maxSlots() {
      return this.maxSlots;
   }

   public PlayerHomes data(Player player) {
      return this.dataFor(player.getUniqueId(), player.getName());
   }

   public PlayerHomes dataFor(UUID uniqueId, String lastName) {
      PlayerHomes data = this.cache.computeIfAbsent(uniqueId, id -> new PlayerHomes(id, this.freeSlots));
      if (lastName != null && !lastName.isBlank() && !lastName.equals(data.lastName())) {
         data.lastName(lastName);
      }

      if (data.unlocked() < this.freeSlots) {
         data.unlocked(this.freeSlots);
      }

      return data;
   }

   public boolean adminUnlock(UUID uniqueId, String lastName, int slot) {
      if (slot >= 0 && slot < this.maxSlots) {
         PlayerHomes data = this.dataFor(uniqueId, lastName);
         if (data.isUnlocked(slot)) {
            return false;
         }

         data.unlocked(Math.max(data.unlocked(), slot + 1));
         this.saveAsync();
         return true;
      } else {
         return false;
      }
   }

   public boolean adminLock(UUID uniqueId, int slot) {
      if (slot >= this.freeSlots && slot < this.maxSlots) {
         PlayerHomes data = this.dataIfPresent(uniqueId);
         if (data != null && data.isUnlocked(slot) && data.homeAt(slot) == null) {
            for (int index = slot; index < this.maxSlots; index++) {
               if (data.homeAt(index) != null) {
                  return false;
               }
            }

            data.unlocked(slot);
            this.saveAsync();
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public PlayerHomes dataIfPresent(UUID uniqueId) {
      return this.cache.get(uniqueId);
   }

   public boolean isUnlocked(UUID uniqueId, int slot) {
      PlayerHomes data = this.cache.get(uniqueId);
      int unlocked = data == null ? this.freeSlots : data.unlocked();
      return slot >= 0 && slot < unlocked;
   }

   public Home homeAt(UUID uniqueId, int slot) {
      PlayerHomes data = this.cache.get(uniqueId);
      return data == null ? null : data.homeAt(slot);
   }

   public Home byName(UUID uniqueId, String name) {
      PlayerHomes data = this.cache.get(uniqueId);
      return data == null ? null : data.byName(name);
   }

   public Map<Integer, Home> homes(UUID uniqueId) {
      PlayerHomes data = this.cache.get(uniqueId);
      return data == null ? Map.of() : data.homes();
   }

   private void recordHomes(Player player, PlayerHomes data) {
      if (this.plugin.stats() != null) {
         this.plugin.stats().set(player, StatType.HOMES, data.homes().size());
      }
   }

   public int priceFor(int slot) {
      return this.plugin.config().homePrice(slot + 1);
   }

   public boolean isPurchasable(int slot) {
      return slot >= 0 && slot < this.maxSlots && this.priceFor(slot) > 0;
   }

   public HomeManager.PurchaseResult purchase(Player player, int slot) {
      if (slot >= 0 && slot < this.maxSlots) {
         PlayerHomes data = this.data(player);
         if (data.isUnlocked(slot)) {
            return HomeManager.PurchaseResult.ALREADY_UNLOCKED;
         }

         int price = this.priceFor(slot);
         if (price <= 0) {
            return HomeManager.PurchaseResult.NOT_PURCHASABLE;
         }

         if (!this.plugin.economy().isEnabled()) {
            return HomeManager.PurchaseResult.ECONOMY_DISABLED;
         }

         if (!this.plugin.economy().has(player, price)) {
            return HomeManager.PurchaseResult.INSUFFICIENT_FUNDS;
         }

         if (!this.plugin.economy().withdraw(player, price)) {
            return HomeManager.PurchaseResult.FAILED;
         }

         data.unlocked(Math.max(data.unlocked(), slot + 1));
         this.saveAsync();
         return HomeManager.PurchaseResult.SUCCESS;
      } else {
         return HomeManager.PurchaseResult.NOT_PURCHASABLE;
      }
   }

   public boolean unlock(Player player, int slot, boolean persist) {
      if (slot >= 0 && slot < this.maxSlots) {
         PlayerHomes data = this.data(player);
         if (data.isUnlocked(slot)) {
            return false;
         }

         data.unlocked(Math.max(data.unlocked(), slot + 1));
         if (persist) {
            this.saveAsync();
         }

         return true;
      } else {
         return false;
      }
   }

   public boolean lock(Player player, int slot) {
      if (slot < this.freeSlots) {
         return false;
      }

      PlayerHomes data = this.data(player);
      if (data.isUnlocked(slot) && data.homeAt(slot) == null) {
         for (int index = slot; index < this.maxSlots; index++) {
            if (data.homeAt(index) != null) {
               return false;
            }
         }

         data.unlocked(slot);
         this.saveAsync();
         return true;
      } else {
         return false;
      }
   }

   public HomeManager.SetHomeResult setHome(Player player, String name, Location location) {
      if (!isValidName(name)) {
         return HomeManager.SetHomeResult.INVALID_NAME;
      }

      PlayerHomes data = this.data(player);
      Home home = Home.of(name, location);
      int existing = data.slotOf(name);
      if (existing >= 0) {
         data.put(existing, home);
         this.recordHomes(player, data);
         this.saveAsync();
         return HomeManager.SetHomeResult.OVERWRITTEN;
      }

      int slot = data.firstEmptyUnlockedSlot();
      if (slot < 0) {
         return HomeManager.SetHomeResult.NO_FREE_SLOT;
      }

      data.put(slot, home);
      this.recordHomes(player, data);
      this.saveAsync();
      return HomeManager.SetHomeResult.CREATED;
   }

   public HomeManager.SetHomeResult setHomeAt(Player player, int slot, String name, Location location) {
      if (isValidName(name) && this.isUnlocked(player.getUniqueId(), slot)) {
         PlayerHomes data = this.data(player);
         data.put(slot, Home.of(name, location));
         this.recordHomes(player, data);
         this.saveAsync();
         return HomeManager.SetHomeResult.CREATED;
      } else {
         return HomeManager.SetHomeResult.INVALID_NAME;
      }
   }

   public Home deleteHome(Player player, String name) {
      PlayerHomes data = this.data(player);
      int slot = data.slotOf(name);
      if (slot < 0) {
         return null;
      }

      Home removed = data.removeAt(slot);
      this.recordHomes(player, data);
      this.saveAsync();
      return removed;
   }

   public Home deleteAt(Player player, int slot) {
      PlayerHomes data = this.data(player);
      Home removed = data.removeAt(slot);
      if (removed != null) {
         this.recordHomes(player, data);
         this.saveAsync();
      }

      return removed;
   }

   public String suggestName(Player player, int slot) {
      PlayerHomes data = this.data(player);
      String base = "home" + (slot + 1);
      String candidate = base;

      for (int counter = 2; data.byName(candidate) != null; counter++) {
         candidate = base + "-" + counter;
      }

      return candidate;
   }

   public static boolean isValidName(String name) {
      return name != null && name.length() <= 16 && name.matches("[A-Za-z0-9_-]+");
   }

   public enum PurchaseResult {
      SUCCESS,
      ALREADY_UNLOCKED,
      NOT_PURCHASABLE,
      ECONOMY_DISABLED,
      INSUFFICIENT_FUNDS,
      FAILED;
   }

   public enum SetHomeResult {
      CREATED,
      OVERWRITTEN,
      NO_FREE_SLOT,
      INVALID_NAME;
   }
}
