package me.w2n.w2nsmp.teleport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.stats.StatType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class TpaService {
   private final W2NSMP plugin;
   private final Map<TpaService.Key, TpaRequest> requests = new HashMap<>();
   private final Map<UUID, Long> cooldowns = new HashMap<>();
   private BukkitTask expiryTask;

   public TpaService(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   public void load() {
      this.startTasks();
   }

   public void reload() {
      this.stopTasks();
      int dropped = this.requests.size();
      this.requests.clear();
      this.cooldowns.clear();
      this.startTasks();
      if (dropped > 0) {
         this.plugin.getLogger().info("Reload: " + dropped + " permintaan teleport menunggu dibuang (pemain bisa mengirim ulang dengan aturan baru).");
      }
   }

   public void shutdown() {
      this.stopTasks();
      this.requests.clear();
      this.cooldowns.clear();
   }

   private void startTasks() {
      this.stopTasks();
      this.expiryTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::expire, 20L, 20L);
   }

   private void stopTasks() {
      if (this.expiryTask != null) {
         this.expiryTask.cancel();
         this.expiryTask = null;
      }
   }

   public boolean taskRunning() {
      return this.expiryTask != null;
   }

   public boolean enabled() {
      return this.plugin.config().tpaEnabled();
   }

   public TpaService.Result request(Player sender, Player target, boolean here) {
      if (!this.enabled()) {
         this.plugin.messages().send(sender, "tpa.disabled");
         return TpaService.Result.DISABLED;
      }

      if (sender.getUniqueId().equals(target.getUniqueId())) {
         this.plugin.messages().send(sender, "tpa.self");
         return TpaService.Result.SELF;
      }

      int cooldown = this.cooldownRemaining(sender);
      if (cooldown > 0) {
         this.plugin.messages().send(sender, "tpa.cooldown", "seconds", Integer.toString(cooldown));
         return TpaService.Result.COOLDOWN;
      }

      if (this.plugin.config().tpaBlockInCombat() && this.plugin.combat() != null && this.plugin.combat().isTagged(sender)) {
         this.plugin.messages().send(sender, "tpa.combat-blocked", "seconds", Integer.toString(this.plugin.combat().remainingSeconds(sender)));
         return TpaService.Result.COMBAT_BLOCKED;
      }

      if (this.plugin.teleport().isPending(target)) {
         this.plugin.messages().send(sender, "tpa.target-busy", "target", target.getName());
         return TpaService.Result.TARGET_BUSY;
      }

      TpaService.Key key = new TpaService.Key(target.getUniqueId(), sender.getUniqueId());
      boolean replacing = this.requests.containsKey(key);
      if (!replacing && this.outgoingCount(sender.getUniqueId()) >= this.plugin.config().tpaMaxOutgoing()) {
         this.plugin.messages().send(sender, "tpa.outgoing-limit", "count", Integer.toString(this.plugin.config().tpaMaxOutgoing()));
         return TpaService.Result.OUTGOING_LIMIT;
      }

      if (!replacing && this.incomingCount(target.getUniqueId()) >= this.plugin.config().tpaMaxIncoming()) {
         this.plugin.messages().send(sender, "tpa.incoming-limit", "target", target.getName());
         return TpaService.Result.INCOMING_LIMIT;
      }

      int timeout = this.plugin.config().tpaRequestTimeoutSeconds();
      this.requests.put(key, TpaRequest.create(sender.getUniqueId(), sender.getName(), target.getUniqueId(), target.getName(), here, timeout * 1000L));
      this.cooldowns.put(sender.getUniqueId(), System.currentTimeMillis() + this.plugin.config().tpaCooldownSeconds() * 1000L);
      this.plugin.stats().add(sender, StatType.TPA_SENT, 1L);
      this.plugin.messages().send(sender, here ? "tpa.sent-here" : "tpa.sent", "target", target.getName(), "seconds", Integer.toString(timeout));
      if (this.plugin.settings() == null || this.plugin.settings().tpaNotifications(target)) {
         this.plugin.messages().send(target, here ? "tpa.received-here" : "tpa.received", "player", sender.getName(), "seconds", Integer.toString(timeout));
      }

      this.plugin.guiSounds().play(target, this.plugin.guiConfigs().tpa(), "notify");
      return TpaService.Result.OK;
   }

   public TpaService.Result accept(Player accepter, UUID requester) {
      if (!this.enabled()) {
         this.plugin.messages().send(accepter, "tpa.disabled");
         return TpaService.Result.DISABLED;
      }

      TpaRequest request = this.takeIncoming(accepter, requester, "tpa.no-request", "tpa.no-request-from");
      if (request == null) {
         return TpaService.Result.NO_REQUEST;
      }

      Player mover = Bukkit.getPlayer(request.moverId());
      Player anchor = Bukkit.getPlayer(request.anchorId());
      if (mover != null && anchor != null && mover.isOnline() && anchor.isOnline()) {
         Location destination = anchor.getLocation().clone();
         int delay = this.plugin.config().tpaTeleportDelay();
         this.plugin
            .messages()
            .send(accepter, "tpa.accepted-target", "player", request.senderName(), "target", request.senderName(), "seconds", Integer.toString(delay));
         Player sender = Bukkit.getPlayer(request.senderId());
         if (sender != null && sender.isOnline()) {
            this.plugin
               .messages()
               .send(sender, "tpa.accepted-sender", "player", accepter.getName(), "target", accepter.getName(), "seconds", Integer.toString(delay));
         }

         boolean accepted = this.plugin
            .teleport()
            .start(
               TeleportRequest.builder(mover, destination, "tpa")
                  .placeholder("player", anchor.getName())
                  .placeholder("target", anchor.getName())
                  .delaySeconds(delay)
                  .actionBar(this.plugin.config().tpaActionBar())
                  .cancelOnMove(this.plugin.config().tpaCancelOnMove())
                  .cancelOnDamage(this.plugin.config().tpaCancelOnDamage())
                  .cancelOnDeath(this.plugin.config().tpaCancelOnDeath())
                  .blockedInCombat(this.plugin.config().tpaBlockInCombat())
                  .statType(StatType.TPA_ACCEPTED)
                  .build()
            );
         this.plugin.guiSounds().play(accepter, this.plugin.guiConfigs().tpa(), "success");
         return accepted ? TpaService.Result.OK : TpaService.Result.COMBAT_BLOCKED;
      } else {
         this.plugin.messages().send(accepter, "tpa.target-offline", "player", request.senderName(), "target", request.senderName());
         return TpaService.Result.TARGET_OFFLINE;
      }
   }

   public TpaService.Result deny(Player accepter, UUID requester) {
      TpaRequest request = this.takeIncoming(accepter, requester, "tpa.no-request", "tpa.no-request-from");
      if (request == null) {
         return TpaService.Result.NO_REQUEST;
      }

      this.plugin.messages().send(accepter, "tpa.denied-target", "player", request.senderName(), "target", request.senderName());
      Player sender = Bukkit.getPlayer(request.senderId());
      if (sender != null && sender.isOnline()) {
         this.plugin.messages().send(sender, "tpa.denied-sender", "player", accepter.getName(), "target", accepter.getName());
      }

      this.plugin.guiSounds().play(accepter, this.plugin.guiConfigs().tpa(), "error");
      return TpaService.Result.OK;
   }

   public int cancelOutgoing(Player sender, UUID targetId) {
      List<TpaRequest> removed = new ArrayList<>();

      for (Entry<TpaService.Key, TpaRequest> entry : new ArrayList<>(this.requests.entrySet())) {
         if (entry.getKey().senderId().equals(sender.getUniqueId()) && (targetId == null || entry.getKey().targetId().equals(targetId))) {
            this.requests.remove(entry.getKey());
            removed.add(entry.getValue());
         }
      }

      for (TpaRequest request : removed) {
         this.plugin.messages().send(sender, "tpa.cancelled", "player", request.targetName(), "target", request.targetName());
         Player target = Bukkit.getPlayer(request.targetId());
         if (target != null && target.isOnline()) {
            this.plugin.messages().send(target, "tpa.cancelled-other", "player", sender.getName(), "target", sender.getName());
         }
      }

      if (removed.isEmpty()) {
         this.plugin.messages().send(sender, "tpa.no-outgoing");
      }

      return removed.size();
   }

   private void expire() {
      if (!this.enabled()) {
         if (!this.requests.isEmpty()) {
            this.requests.clear();
         }
      } else {
         long now = System.currentTimeMillis();

         for (Entry<TpaService.Key, TpaRequest> entry : new ArrayList<>(this.requests.entrySet())) {
            TpaRequest request = entry.getValue();
            if (request.expired(now)) {
               this.requests.remove(entry.getKey());
               Player sender = Bukkit.getPlayer(request.senderId());
               if (sender != null && sender.isOnline()) {
                  this.plugin.messages().send(sender, "tpa.timeout-sender", "player", request.targetName(), "target", request.targetName());
               }

               Player target = Bukkit.getPlayer(request.targetId());
               if (target != null && target.isOnline()) {
                  this.plugin
                     .messages()
                     .send(target, "tpa.timeout-target", "player", request.senderName(), "seconds", Integer.toString(request.remainingSeconds(now)));
               }
            }
         }
      }
   }

   public int cancelFor(Player player) {
      UUID uniqueId = player.getUniqueId();
      int removed = 0;

      for (Entry<TpaService.Key, TpaRequest> entry : new ArrayList<>(this.requests.entrySet())) {
         TpaRequest request = entry.getValue();
         if (entry.getKey().senderId().equals(uniqueId) || entry.getKey().targetId().equals(uniqueId)) {
            this.requests.remove(entry.getKey());
            removed++;
            UUID otherId = entry.getKey().senderId().equals(uniqueId) ? entry.getKey().targetId() : entry.getKey().senderId();
            Player other = Bukkit.getPlayer(otherId);
            if (other != null && other.isOnline()) {
               this.plugin.messages().send(other, "tpa.cancelled-other", "player", player.getName(), "target", player.getName());
            }
         }
      }

      this.cooldowns.remove(uniqueId);
      return removed;
   }

   public List<TpaRequest> incoming(UUID targetId) {
      List<TpaRequest> list = new ArrayList<>();
      long now = System.currentTimeMillis();

      for (Entry<TpaService.Key, TpaRequest> entry : this.requests.entrySet()) {
         if (entry.getKey().targetId().equals(targetId) && !entry.getValue().expired(now)) {
            list.add(entry.getValue());
         }
      }

      list.sort((first, second) -> Long.compare(second.createdAt(), first.createdAt()));
      return list;
   }

   public List<TpaRequest> outgoing(UUID senderId) {
      List<TpaRequest> list = new ArrayList<>();
      long now = System.currentTimeMillis();

      for (Entry<TpaService.Key, TpaRequest> entry : this.requests.entrySet()) {
         if (entry.getKey().senderId().equals(senderId) && !entry.getValue().expired(now)) {
            list.add(entry.getValue());
         }
      }

      list.sort((first, second) -> Long.compare(second.createdAt(), first.createdAt()));
      return list;
   }

   public int incomingCount(UUID targetId) {
      return this.incoming(targetId).size();
   }

   public int outgoingCount(UUID senderId) {
      return this.outgoing(senderId).size();
   }

   public int requestCount() {
      return this.requests.size();
   }

   public int cooldownRemaining(Player sender) {
      Long until = this.cooldowns.get(sender.getUniqueId());
      if (until == null) {
         return 0;
      }

      long remaining = until - System.currentTimeMillis();
      return remaining <= 0L ? 0 : (int)Math.ceil(remaining / 1000.0);
   }

   private TpaRequest takeIncoming(Player accepter, UUID requester, String emptyKey, String emptyOtherKey) {
      List<TpaRequest> list = this.incoming(accepter.getUniqueId());
      TpaRequest chosen = null;

      for (TpaRequest request : list) {
         if (requester == null || request.senderId().equals(requester)) {
            chosen = request;
            break;
         }
      }

      if (chosen == null) {
         if (requester == null) {
            this.plugin.messages().send(accepter, emptyKey);
         } else {
            Player other = Bukkit.getPlayer(requester);
            this.plugin.messages().send(accepter, emptyOtherKey, "player", other == null ? requester.toString() : other.getName());
         }

         return null;
      } else {
         this.requests.remove(new TpaService.Key(chosen.targetId(), chosen.senderId()));
         return chosen;
      }
   }

   private record Key(UUID targetId, UUID senderId) {
      private Key {
      }
   }

   public enum Result {
      OK,
      DISABLED,
      SELF,
      TARGET_OFFLINE,
      COOLDOWN,
      OUTGOING_LIMIT,
      INCOMING_LIMIT,
      COMBAT_BLOCKED,
      TARGET_BUSY,
      NO_REQUEST;
   }
}
