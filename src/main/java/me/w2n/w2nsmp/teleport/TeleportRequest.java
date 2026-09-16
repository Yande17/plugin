package me.w2n.w2nsmp.teleport;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import me.w2n.w2nsmp.stats.StatType;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class TeleportRequest {
   private final Player player;
   private final Location target;
   private final String keyPrefix;
   private final Map<String, String> placeholders;
   private final int delaySeconds;
   private final boolean actionBar;
   private final boolean cancelOnMove;
   private final boolean cancelOnDamage;
   private final boolean cancelOnDeath;
   private final boolean blockedInCombat;
   private final StatType statType;

   private TeleportRequest(TeleportRequest.Builder builder) {
      super();
      this.player = builder.player;
      this.target = builder.target;
      this.keyPrefix = builder.keyPrefix;
      this.placeholders = Collections.unmodifiableMap(new LinkedHashMap<>(builder.placeholders));
      this.delaySeconds = Math.max(0, builder.delaySeconds);
      this.actionBar = builder.actionBar;
      this.cancelOnMove = builder.cancelOnMove;
      this.cancelOnDamage = builder.cancelOnDamage;
      this.cancelOnDeath = builder.cancelOnDeath;
      this.blockedInCombat = builder.blockedInCombat;
      this.statType = builder.statType;
   }

   public static TeleportRequest.Builder builder(Player player, Location target, String keyPrefix) {
      return new TeleportRequest.Builder(player, target, keyPrefix);
   }

   public Player player() {
      return this.player;
   }

   public Location target() {
      return this.target;
   }

   public String keyPrefix() {
      return this.keyPrefix;
   }

   public int delaySeconds() {
      return this.delaySeconds;
   }

   public boolean actionBar() {
      return this.actionBar;
   }

   public boolean cancelOnMove() {
      return this.cancelOnMove;
   }

   public boolean cancelOnDamage() {
      return this.cancelOnDamage;
   }

   public boolean cancelOnDeath() {
      return this.cancelOnDeath;
   }

   public StatType statType() {
      return this.statType;
   }

   public boolean blockedInCombat() {
      return this.blockedInCombat;
   }

   public String key(String suffix) {
      return this.keyPrefix + "." + suffix;
   }

   public String[] placeholderArray() {
      String[] array = new String[this.placeholders.size() * 2];
      int index = 0;

      for (Entry<String, String> entry : this.placeholders.entrySet()) {
         array[index++] = entry.getKey();
         array[index++] = entry.getValue();
      }

      return array;
   }

   public String[] placeholderArray(String extraKey, String extraValue) {
      String[] base = this.placeholderArray();
      String[] combined = new String[base.length + 2];
      System.arraycopy(base, 0, combined, 0, base.length);
      combined[base.length] = extraKey;
      combined[base.length + 1] = extraValue;
      return combined;
   }

   public static final class Builder {
      private final Player player;
      private final Location target;
      private final String keyPrefix;
      private final Map<String, String> placeholders = new LinkedHashMap<>();
      private int delaySeconds;
      private boolean actionBar = true;
      private boolean cancelOnMove = true;
      private boolean cancelOnDamage = true;
      private boolean cancelOnDeath = true;
      private boolean blockedInCombat = true;
      private StatType statType;

      private Builder(Player player, Location target, String keyPrefix) {
         super();
         this.player = player;
         this.target = target;
         this.keyPrefix = keyPrefix;
      }

      public TeleportRequest.Builder placeholder(String key, String value) {
         this.placeholders.put(key, value);
         return this;
      }

      public TeleportRequest.Builder delaySeconds(int seconds) {
         this.delaySeconds = seconds;
         return this;
      }

      public TeleportRequest.Builder actionBar(boolean enabled) {
         this.actionBar = enabled;
         return this;
      }

      public TeleportRequest.Builder cancelOnMove(boolean enabled) {
         this.cancelOnMove = enabled;
         return this;
      }

      public TeleportRequest.Builder cancelOnDamage(boolean enabled) {
         this.cancelOnDamage = enabled;
         return this;
      }

      public TeleportRequest.Builder cancelOnDeath(boolean enabled) {
         this.cancelOnDeath = enabled;
         return this;
      }

      public TeleportRequest.Builder blockedInCombat(boolean enabled) {
         this.blockedInCombat = enabled;
         return this;
      }

      public TeleportRequest.Builder statType(StatType type) {
         this.statType = type;
         return this;
      }

      public TeleportRequest build() {
         return new TeleportRequest(this);
      }
   }
}
