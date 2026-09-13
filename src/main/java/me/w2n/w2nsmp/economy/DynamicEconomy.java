package me.w2n.w2nsmp.economy;

import java.io.File;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import me.w2n.w2nsmp.W2NSMP;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitTask;

public final class DynamicEconomy {
   static final int MAX_CATCHUP_STEPS = 2016;
   private final W2NSMP plugin;
   private final DynamicPriceStorage storage;
   private final Map<Material, Double> multipliers = new EnumMap<>(Material.class);
   private final Map<Material, Long> soldSinceUpdate = new EnumMap<>(Material.class);
   private final Map<Material, Double> lastDelta = new EnumMap<>(Material.class);
   private long updatedAt;
   private BukkitTask updateTask;

   public DynamicEconomy(W2NSMP plugin) {
      super();
      this.plugin = plugin;
      this.storage = new DynamicPriceStorage(plugin);
   }

   public void load() {
      DynamicPriceStorage.Snapshot snapshot = this.storage.load();
      this.multipliers.clear();
      this.soldSinceUpdate.clear();
      this.lastDelta.clear();
      this.updatedAt = snapshot.updatedAt();

      for (Entry<String, Double> entry : snapshot.multipliers().entrySet()) {
         Material material = Material.matchMaterial(entry.getKey());
         if (material == null) {
            this.plugin.getLogger().warning("dynamic.yml: material tidak dikenal " + entry.getKey() + " dilewati.");
         } else {
            this.multipliers.put(material, this.clamp(entry.getValue()));
         }
      }

      this.catchUpRecovery();
      this.startTasks();
   }

   public void reload() {
      this.stopTasks();
      if (this.storage.loadFailed()) {
         this.plugin.getLogger().warning("dynamic.yml tidak bisa dibaca; harga dinamis memakai keadaan di memori sampai berkas diperbaiki.");
         this.startTasks();
      } else {
         if (this.isDirty()) {
            this.saveNow();
         }

         this.load();
         this.plugin.debug("Ekonomi dinamis dimuat ulang: " + this.multipliers.size() + " material berpenyesuaian.");
      }
   }

   public void shutdown() {
      this.stopTasks();
      this.saveNow();
   }

   private void startTasks() {
      this.stopTasks();
      if (this.enabled() && this.intervalSeconds() > 0) {
         long ticks = this.intervalSeconds() * 20L;
         this.updateTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::applyUpdate, ticks, ticks);
      }
   }

   private void stopTasks() {
      if (this.updateTask != null) {
         this.updateTask.cancel();
         this.updateTask = null;
      }
   }

   public boolean taskRunning() {
      return this.updateTask != null;
   }

   public boolean enabled() {
      return this.plugin.config().dynamicEconomyEnabled();
   }

   public int intervalSeconds() {
      return this.plugin.config().dynamicUpdateIntervalSeconds();
   }

   public double minMultiplier() {
      return this.plugin.config().dynamicMinMultiplier();
   }

   public double maxMultiplier() {
      return this.plugin.config().dynamicMaxMultiplier();
   }

   public double impactStrength() {
      return this.plugin.config().dynamicSellImpactStrength();
   }

   public double recoveryRate() {
      return this.plugin.config().dynamicRecoveryRate();
   }

   public long updatedAt() {
      return this.updatedAt;
   }

   public File file() {
      return this.storage.file();
   }

   public boolean loadFailed() {
      return this.storage.loadFailed();
   }

   public boolean isDirty() {
      return this.storage.isDirty();
   }

   public void saveNow() {
      this.storage.saveNow(this.snapshot());
   }

   private DynamicPriceStorage.Snapshot snapshot() {
      Map<String, Double> values = new LinkedHashMap<>();

      for (Entry<Material, Double> entry : this.multipliers.entrySet()) {
         values.put(entry.getKey().name(), entry.getValue());
      }

      return new DynamicPriceStorage.Snapshot(values, this.updatedAt);
   }

   public double multiplier(Material material) {
      if (material != null && this.enabled()) {
         Double value = this.multipliers.get(material);
         return value == null ? 1.0 : value;
      } else {
         return 1.0;
      }
   }

   public int price(Material material, int basePrice) {
      if (basePrice <= 0) {
         return 0;
      }

      if (!this.enabled()) {
         return basePrice;
      }

      long value = Math.round(basePrice * this.multiplier(material));
      return (int)Math.max(1L, Math.min(2147483647L, value));
   }

   public int currentPrice(Material material) {
      return this.price(material, this.plugin.sell().prices().price(material));
   }

   public DynamicEconomy.Trend trend(Material material) {
      Double delta = this.lastDelta.get(material);
      if (delta != null && !(Math.abs(delta) < 1.0E-9)) {
         return delta < 0.0 ? DynamicEconomy.Trend.FALLING : DynamicEconomy.Trend.RISING;
      } else {
         return DynamicEconomy.Trend.STABLE;
      }
   }

   public long soldSinceUpdate(Material material) {
      Long value = this.soldSinceUpdate.get(material);
      return value == null ? 0L : value;
   }

   public int tracked() {
      int count = 0;

      for (double value : this.multipliers.values()) {
         if (Math.abs(value - 1.0) > 1.0E-9) {
            count++;
         }
      }

      return count;
   }

   public Map<String, Double> mostMoved(int limit) {
      Map<String, Double> sorted = new LinkedHashMap<>();
      this.multipliers
         .entrySet()
         .stream()
         .sorted((first, second) -> Double.compare(Math.abs(second.getValue() - 1.0), Math.abs(first.getValue() - 1.0)))
         .limit(Math.max(1, limit))
         .forEach(entry -> sorted.put(entry.getKey().name(), entry.getValue()));
      return sorted;
   }

   public void recordSale(Material material, int units) {
      if (this.enabled() && material != null && units > 0) {
         double current = this.multiplier(material);
         double delta = -this.impactStrength() * units;
         double next = this.clamp(current + delta);
         this.soldSinceUpdate.merge(material, (long)units, Long::sum);
         if (!(Math.abs(next - current) < 1.0E-12)) {
            this.multipliers.put(material, next);
            this.lastDelta.put(material, next - current);
            this.storage.saveAsync(this.snapshot());
         }
      }
   }

   public void applyUpdate() {
      if (this.enabled()) {
         boolean changed = false;

         for (Entry<Material, Double> entry : new LinkedHashMap<>(this.multipliers).entrySet()) {
            double current = entry.getValue();
            double recovered = this.recover(current);
            if (Math.abs(recovered - current) > 1.0E-12) {
               this.multipliers.put(entry.getKey(), recovered);
               this.lastDelta.put(entry.getKey(), recovered - current);
               changed = true;
            } else {
               this.lastDelta.remove(entry.getKey());
            }

            if (Math.abs(this.multipliers.get(entry.getKey()) - 1.0) < 1.0E-6) {
               this.multipliers.remove(entry.getKey());
               changed = true;
            }
         }

         this.soldSinceUpdate.clear();
         this.updatedAt = System.currentTimeMillis();
         if (changed) {
            this.storage.saveAsync(this.snapshot());
         } else {
            this.storage.saveAsync(this.snapshot());
         }
      }
   }

   public void setMultiplier(Material material, double value) {
      if (material != null) {
         double clamped = this.clamp(value);
         double previous = this.multiplier(material);
         if (Math.abs(clamped - 1.0) < 1.0E-9) {
            this.multipliers.remove(material);
         } else {
            this.multipliers.put(material, clamped);
         }

         this.lastDelta.put(material, clamped - previous);
         this.storage.saveAsync(this.snapshot());
      }
   }

   public int reset() {
      int size = this.multipliers.size();
      this.multipliers.clear();
      this.soldSinceUpdate.clear();
      this.lastDelta.clear();
      this.updatedAt = System.currentTimeMillis();
      this.storage.saveAsync(this.snapshot());
      return size;
   }

   private void catchUpRecovery() {
      if (this.enabled() && !this.multipliers.isEmpty() && this.updatedAt > 0L) {
         int interval = Math.max(1, this.intervalSeconds());
         long elapsedSeconds = Math.max(0L, (System.currentTimeMillis() - this.updatedAt) / 1000L);
         int steps = (int)Math.min(2016L, elapsedSeconds / interval);
         if (steps > 0) {
            for (Entry<Material, Double> entry : new LinkedHashMap<>(this.multipliers).entrySet()) {
               double value = entry.getValue();

               for (int step = 0; step < steps; step++) {
                  value = this.recover(value);
               }

               if (Math.abs(value - 1.0) < 1.0E-6) {
                  this.multipliers.remove(entry.getKey());
               } else {
                  this.multipliers.put(entry.getKey(), value);
               }
            }

            this.plugin.debug("Ekonomi dinamis: " + steps + " siklus pemulihan diterapkan setelah restart.");
         }

         this.updatedAt = System.currentTimeMillis();
      } else {
         this.updatedAt = System.currentTimeMillis();
      }
   }

   private double recover(double value) {
      if (Math.abs(value - 1.0) < 1.0E-9) {
         return 1.0;
      }

      double next = value + (1.0 - value) * this.recoveryRate();
      return this.clamp(next);
   }

   private double clamp(double value) {
      return !Double.isFinite(value) ? 1.0 : Math.max(this.minMultiplier(), Math.min(this.maxMultiplier(), value));
   }

   public enum Trend {
      RISING,
      FALLING,
      STABLE;
   }
}
