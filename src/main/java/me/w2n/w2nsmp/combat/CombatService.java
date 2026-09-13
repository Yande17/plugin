package me.w2n.w2nsmp.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import me.w2n.w2nsmp.W2NSMP;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class CombatService {
   public static final String BYPASS_PERMISSION = "w2nsmp.combat.bypass";
   private final W2NSMP plugin;
   private final Map<UUID, CombatTag> tags = new HashMap<>();
   private BukkitTask task;
   private int punishedCount;

   public CombatService(W2NSMP plugin) {
      super();
      this.plugin = plugin;
      this.task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
   }

   public void tagPair(Player attacker, Player victim) {
      this.apply(attacker, victim);
      this.apply(victim, attacker);
   }

   public void tag(Player victim, Player attacker) {
      this.apply(victim, attacker);
   }

   private void apply(Player victim, Player attacker) {
      if (victim != null && this.enabled() && !this.bypasses(victim)) {
         long now = System.currentTimeMillis();
         int seconds = this.durationSeconds();
         CombatTag existing = this.tags.get(victim.getUniqueId());
         CombatTag tag = existing != null ? existing : new CombatTag(victim, now + seconds * 1000L);
         tag.setPlayerName(victim.getName());
         tag.setExpiresAt(now + seconds * 1000L);
         tag.setAttackerName(attacker == null ? null : attacker.getName());
         this.tags.put(victim.getUniqueId(), tag);
         if (existing != null) {
            this.sendIndicator(tag, now);
         } else {
            if (attacker == null) {
               this.plugin.messages().send(victim, "combat.tagged-generic", "seconds", Integer.toString(seconds));
            } else {
               this.plugin.messages().send(victim, "combat.tagged", "player", attacker.getName(), "seconds", Integer.toString(seconds));
            }

            this.plugin.debug("Combat: " + victim.getName() + " tertag" + (attacker == null ? " (non-pemain)" : " oleh " + attacker.getName()) + ".");
            if (this.plugin.config().combatCancelTeleportOnTag() && this.plugin.teleport() != null && this.plugin.teleport().isPending(victim)) {
               this.plugin.teleport().cancel(victim, "combat");
            }

            this.sendIndicator(tag, now);
         }
      }
   }

   public boolean enabled() {
      return this.plugin.config().combatEnabled();
   }

   public boolean bypasses(Player player) {
      return player != null && player.hasPermission("w2nsmp.combat.bypass");
   }

   public boolean isTagged(Player player) {
      return player != null && this.isTagged(player.getUniqueId());
   }

   public boolean isTagged(UUID uniqueId) {
      CombatTag tag = this.tags.get(uniqueId);
      if (tag == null) {
         return false;
      } else if (tag.expiresAt() <= System.currentTimeMillis()) {
         this.tags.remove(uniqueId);
         return false;
      } else {
         return true;
      }
   }

   public long remainingMillis(Player player) {
      return !this.isTagged(player) ? 0L : Math.max(0L, this.tags.get(player.getUniqueId()).expiresAt() - System.currentTimeMillis());
   }

   public int remainingSeconds(Player player) {
      return (int)((this.remainingMillis(player) + 999L) / 1000L);
   }

   public String attackerName(Player player) {
      return !this.isTagged(player) ? null : this.tags.get(player.getUniqueId()).attackerName();
   }

   public boolean blocksTeleport(Player player) {
      return this.plugin.config().combatBlockTeleport() && this.isTagged(player) && !this.bypasses(player);
   }

   public int taggedCount() {
      return this.tags.size();
   }

   public int punishedCount() {
      return this.punishedCount;
   }

   public void countPunishment() {
      this.punishedCount++;
   }

   public boolean untag(Player player) {
      return player != null && this.tags.remove(player.getUniqueId()) != null;
   }

   public void clearState() {
      this.tags.clear();
   }

   public void shutdown() {
      if (this.task != null) {
         this.task.cancel();
         this.task = null;
      }

      this.tags.clear();
   }

   private void tick() {
      if (!this.enabled()) {
         if (!this.tags.isEmpty()) {
            this.tags.clear();
            this.plugin.debug("Combat: fitur dimatikan di config, seluruh tag dibersihkan.");
         }
      } else {
         long now = System.currentTimeMillis();

         for (CombatTag tag : new ArrayList<>(this.tags.values())) {
            if (tag.expiresAt() <= now) {
               this.tags.remove(tag.uniqueId());
               Player player = tag.player();
               if (player.isOnline()) {
                  this.plugin.messages().send(player, "combat.ended");
               }

               this.plugin.debug("Combat: tag " + tag.playerName() + " berakhir.");
            } else {
               this.sendIndicator(tag, now);
            }
         }
      }
   }

   private void sendIndicator(CombatTag tag, long now) {
      if (this.plugin.config().combatActionBar()) {
         Player player = tag.player();
         if (player.isOnline()) {
            tag.setLastIndicatorAt(now);
            player.sendActionBar(this.plugin.messages().component("combat.actionbar", "seconds", Long.toString(this.secondsLeft(tag, now))));
         }
      }
   }

   private long secondsLeft(CombatTag tag, long now) {
      return Math.max(0L, (tag.expiresAt() - now + 999L) / 1000L);
   }

   private int durationSeconds() {
      return Math.max(1, this.plugin.config().combatDurationSeconds());
   }
}
