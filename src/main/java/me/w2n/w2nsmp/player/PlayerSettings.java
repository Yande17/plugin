package me.w2n.w2nsmp.player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerSettings {
   public static final String KEY_NAMETAG_MONEY = "nametag-money";
   public static final String KEY_SOUNDS = "sounds";
   public static final String KEY_NOTIFICATIONS = "notifications";
   public static final String KEY_NOTIFY_BOUNTY = "notify-bounty";
   public static final String KEY_NOTIFY_AUCTION = "notify-auction";
   public static final String KEY_NOTIFY_TPA = "notify-tpa";
   public static final String KEY_TELEPORT_COUNTDOWN = "teleport-countdown";
   private final UUID uniqueId;
   private final Map<String, Boolean> values = new LinkedHashMap<>();

   public PlayerSettings(UUID uniqueId) {
      super();
      this.uniqueId = uniqueId;
   }

   public UUID uniqueId() {
      return this.uniqueId;
   }

   public boolean get(String key, boolean defaultValue) {
      return this.values.getOrDefault(key, defaultValue);
   }

   public boolean set(String key, boolean value) {
      Boolean previous = this.values.put(key, value);
      return previous == null || previous != value;
   }

   public boolean isEmpty() {
      return this.values.isEmpty();
   }

   public Map<String, Boolean> values() {
      return Map.copyOf(this.values);
   }

   public void put(String key, boolean value) {
      this.values.put(key, value);
   }
}
