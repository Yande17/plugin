package me.w2n.w2nsmp.stats;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class PlayerStats {
   // Penghitung versi data. Dipakai StatisticsService untuk menulis hanya baris yang berubah
   // (mode SQLITE), sehingga autosave tidak perlu menulis ulang seluruh database.
   private static final AtomicLong STAMP_COUNTER = new AtomicLong();

   private final UUID uniqueId;
   private final Map<StatType, Long> values = new EnumMap<>(StatType.class);
   private String name;
   private long firstSeen;
   private long lastSeen;
   private long stamp;

   public PlayerStats(UUID uniqueId) {
      super();
      this.uniqueId = uniqueId;
   }

   public UUID uniqueId() {
      return this.uniqueId;
   }

   public String name() {
      return this.name;
   }

   public void name(String name) {
      if (name != null && !name.isBlank() && !name.equals(this.name)) {
         this.name = name;
         this.touch();
      }
   }

   public long firstSeen() {
      return this.firstSeen;
   }

   public void firstSeen(long firstSeen) {
      long value = Math.max(0L, firstSeen);
      if (value != this.firstSeen) {
         this.firstSeen = value;
         this.touch();
      }
   }

   public long lastSeen() {
      return this.lastSeen;
   }

   public void lastSeen(long lastSeen) {
      long value = Math.max(0L, lastSeen);
      if (value != this.lastSeen) {
         this.lastSeen = value;
         this.touch();
      }
   }

   /**
    * Versi data baris ini. Nilai 0 berarti "belum pernah berubah sejak dimuat".
    * Dipakai untuk menentukan baris mana yang perlu ditulis ulang ke w2nsmp.db.
    */
   public long stamp() {
      return this.stamp;
   }

   public void stamp(long stamp) {
      this.stamp = stamp;
   }

   private void touch() {
      this.stamp = STAMP_COUNTER.incrementAndGet();
   }

   public long value(StatType type) {
      return type == null ? 0L : this.values.getOrDefault(type, 0L);
   }

   public long add(StatType type, long amount) {
      if (type != null && amount > 0L) {
         long next = this.value(type) + amount;
         this.values.put(type, next);
         this.touch();
         return next;
      } else {
         return this.value(type);
      }
   }

   public void value(StatType type, long value) {
      if (type != null) {
         Long previous = this.values.get(type);
         if (value <= 0L) {
            if (previous != null) {
               this.values.remove(type);
               this.touch();
            }
         } else {
            this.values.put(type, value);
            if (previous == null || previous.longValue() != value) {
               this.touch();
            }
         }
      }
   }

   public Map<StatType, Long> values() {
      return Collections.unmodifiableMap(new EnumMap<>(this.values));
   }

   public boolean isEmpty() {
      return this.values.isEmpty() && this.firstSeen <= 0L && this.lastSeen <= 0L;
   }
}
