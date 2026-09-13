package me.w2n.w2nsmp.stats;

import java.util.UUID;

public record LeaderboardEntry(int rank, UUID uniqueId, String name, long value) {
   public LeaderboardEntry {
   }
}
