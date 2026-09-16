package me.w2n.w2nsmp.combat;

import java.util.UUID;
import org.bukkit.entity.Player;

public final class CombatTag {
   private final Player player;
   private final UUID uniqueId;
   private String playerName;
   private long expiresAt;
   private String attackerName;
   private long lastIndicatorAt;

   CombatTag(Player player, long expiresAt) {
      super();
      this.player = player;
      this.uniqueId = player.getUniqueId();
      this.playerName = player.getName();
      this.expiresAt = expiresAt;
   }

   public Player player() {
      return this.player;
   }

   public UUID uniqueId() {
      return this.uniqueId;
   }

   public String playerName() {
      return this.playerName;
   }

   void setPlayerName(String name) {
      this.playerName = name;
   }

   public long expiresAt() {
      return this.expiresAt;
   }

   void setExpiresAt(long expiresAt) {
      this.expiresAt = expiresAt;
   }

   public String attackerName() {
      return this.attackerName;
   }

   void setAttackerName(String attackerName) {
      this.attackerName = attackerName;
   }

   public long lastIndicatorAt() {
      return this.lastIndicatorAt;
   }

   void setLastIndicatorAt(long lastIndicatorAt) {
      this.lastIndicatorAt = lastIndicatorAt;
   }
}
