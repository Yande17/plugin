package me.w2n.w2nsmp.bounty;

import java.util.UUID;

public record BountyEntry(UUID uniqueId, String name, long amount) {
   public BountyEntry {
   }

   public String displayName(String fallback) {
      return this.name != null && !this.name.isBlank() ? this.name : fallback;
   }
}
