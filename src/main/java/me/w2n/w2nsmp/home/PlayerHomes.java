package me.w2n.w2nsmp.home;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;

public final class PlayerHomes {
   private final UUID uniqueId;
   private final Map<Integer, Home> homes = new LinkedHashMap<>();
   private String lastName;
   private int unlocked;

   public PlayerHomes(UUID uniqueId, int unlocked) {
      super();
      this.uniqueId = uniqueId;
      this.unlocked = unlocked;
   }

   public UUID uniqueId() {
      return this.uniqueId;
   }

   public String lastName() {
      return this.lastName;
   }

   public void lastName(String lastName) {
      this.lastName = lastName;
   }

   public int unlocked() {
      return this.unlocked;
   }

   public void unlocked(int unlocked) {
      this.unlocked = unlocked;
   }

   public boolean isUnlocked(int slot) {
      return slot >= 0 && slot < this.unlocked;
   }

   public Home homeAt(int slot) {
      return this.homes.get(slot);
   }

   public Map<Integer, Home> homes() {
      return Collections.unmodifiableMap(this.homes);
   }

   public int used() {
      return this.homes.size();
   }

   public void put(int slot, Home home) {
      this.homes.put(slot, home);
   }

   public Home removeAt(int slot) {
      return this.homes.remove(slot);
   }

   public int slotOf(String name) {
      for (Entry<Integer, Home> entry : this.homes.entrySet()) {
         if (entry.getValue().name().equalsIgnoreCase(name)) {
            return entry.getKey();
         }
      }

      return -1;
   }

   public Home byName(String name) {
      int slot = this.slotOf(name);
      return slot < 0 ? null : this.homes.get(slot);
   }

   public int firstEmptyUnlockedSlot() {
      for (int slot = 0; slot < this.unlocked; slot++) {
         if (!this.homes.containsKey(slot)) {
            return slot;
         }
      }

      return -1;
   }
}
