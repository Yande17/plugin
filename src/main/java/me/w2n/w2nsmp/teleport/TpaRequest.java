package me.w2n.w2nsmp.teleport;

import java.util.UUID;

public final class TpaRequest {
   private final UUID senderId;
   private final String senderName;
   private final UUID targetId;
   private final String targetName;
   private final boolean here;
   private final long createdAt;
   private final long expiresAt;

   private TpaRequest(UUID senderId, String senderName, UUID targetId, String targetName, boolean here, long createdAt, long expiresAt) {
      super();
      this.senderId = senderId;
      this.senderName = senderName;
      this.targetId = targetId;
      this.targetName = targetName;
      this.here = here;
      this.createdAt = createdAt;
      this.expiresAt = expiresAt;
   }

   public static TpaRequest create(UUID senderId, String senderName, UUID targetId, String targetName, boolean here, long timeoutMs) {
      long now = System.currentTimeMillis();
      return new TpaRequest(senderId, senderName, targetId, targetName, here, now, now + Math.max(1000L, timeoutMs));
   }

   public UUID senderId() {
      return this.senderId;
   }

   public String senderName() {
      return this.senderName;
   }

   public UUID targetId() {
      return this.targetId;
   }

   public String targetName() {
      return this.targetName;
   }

   public boolean here() {
      return this.here;
   }

   public long createdAt() {
      return this.createdAt;
   }

   public long expiresAt() {
      return this.expiresAt;
   }

   public boolean expired(long now) {
      return now >= this.expiresAt;
   }

   public int remainingSeconds(long now) {
      long remaining = this.expiresAt - now;
      return remaining <= 0L ? 0 : (int)Math.ceil(remaining / 1000.0);
   }

   public UUID moverId() {
      return this.here ? this.targetId : this.senderId;
   }

   public String moverName() {
      return this.here ? this.targetName : this.senderName;
   }

   public UUID anchorId() {
      return this.here ? this.senderId : this.targetId;
   }

   public String anchorName() {
      return this.here ? this.senderName : this.targetName;
   }
}
