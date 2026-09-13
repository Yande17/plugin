package me.w2n.w2nsmp.teleport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import me.w2n.w2nsmp.W2NSMP;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class TeleportService {
   private static final double MOVE_TOLERANCE = 0.5;
   private final W2NSMP plugin;
   private final Map<UUID, TeleportService.Pending> pending = new HashMap<>();

   public TeleportService(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   public boolean start(TeleportRequest request) {
      Player player = request.player();
      if (this.plugin.combat() != null && request.blockedInCombat() && this.plugin.combat().blocksTeleport(player)) {
         this.plugin
            .messages()
            .send(
               player,
               request.key("teleport-blocked-combat"),
               request.placeholderArray("seconds", Integer.toString(this.plugin.combat().remainingSeconds(player)))
            );
         return false;
      }

      this.cancelSilently(player);
      if (request.delaySeconds() <= 0) {
         this.teleport(request);
         return true;
      }

      TeleportService.Pending state = new TeleportService.Pending(player, request, player.getLocation().clone(), request.delaySeconds());
      state.task = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> this.tick(state), 20L, 20L);
      this.pending.put(player.getUniqueId(), state);
      if (this.countdownVisible(player)) {
         this.plugin.messages().send(player, request.key("teleport-countdown"), request.placeholderArray("seconds", Integer.toString(request.delaySeconds())));
         this.actionBar(request, "teleport-actionbar", Integer.toString(request.delaySeconds()));
      }

      return true;
   }

   private void tick(TeleportService.Pending state) {
      Player player = state.player;
      if (!player.isOnline()) {
         this.stop(state);
      } else {
         state.remaining--;
         if (state.remaining <= 0) {
            this.stop(state);
            this.teleport(state.request);
         } else {
            if (this.countdownVisible(player)) {
               this.actionBar(state.request, "teleport-actionbar", Integer.toString(state.remaining));
            }
         }
      }
   }

   public void cancel(Player player, String reason) {
      TeleportService.Pending state = this.pending.remove(player.getUniqueId());
      if (state != null) {
         if (state.task != null) {
            state.task.cancel();
         }

         if (reason != null) {
            this.plugin.messages().send(player, state.request.key("teleport-cancelled-" + reason), state.request.placeholderArray());
            if (state.request.actionBar() && this.countdownVisible(player)) {
               player.sendActionBar(this.plugin.messages().component(state.request.key("teleport-cancelled-actionbar"), state.request.placeholderArray()));
            }
         }
      }
   }

   private boolean countdownVisible(Player player) {
      return this.plugin.settings() == null || this.plugin.settings().teleportCountdown(player);
   }

   public boolean isPending(Player player) {
      return this.pending.containsKey(player.getUniqueId());
   }

   public TeleportRequest pendingRequest(Player player) {
      TeleportService.Pending state = this.pending.get(player.getUniqueId());
      return state == null ? null : state.request;
   }

   public boolean movedTooFar(Player player, Location to) {
      TeleportService.Pending state = this.pending.get(player.getUniqueId());
      if (state != null && to != null) {
         return state.origin.getWorld() != null && to.getWorld() != null && state.origin.getWorld().equals(to.getWorld())
            ? state.origin.distanceSquared(to) > 0.25
            : true;
      } else {
         return false;
      }
   }

   public void cancelAll() {
      for (TeleportService.Pending state : new ArrayList<>(this.pending.values())) {
         if (state.task != null) {
            state.task.cancel();
         }
      }

      this.pending.clear();
   }

   public int pendingCount() {
      return this.pending.size();
   }

   private void cancelSilently(Player player) {
      TeleportService.Pending state = this.pending.remove(player.getUniqueId());
      if (state != null && state.task != null) {
         state.task.cancel();
      }
   }

   private void stop(TeleportService.Pending state) {
      if (state.task != null) {
         state.task.cancel();
      }

      this.pending.remove(state.player.getUniqueId(), state);
   }

   private void teleport(TeleportRequest request) {
      Player player = request.player();
      if (player.isOnline()) {
         request.player().teleportAsync(request.target()).thenAccept(success -> Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (player.isOnline()) {
               if (Boolean.TRUE.equals(success)) {
                  if (request.statType() != null && this.plugin.stats() != null) {
                     this.plugin.stats().add(player, request.statType(), 1L);
                  }

                  this.plugin.messages().send(player, request.key("teleport-success"), request.placeholderArray());
               } else {
                  this.plugin.messages().send(player, request.key("teleport-failed"), request.placeholderArray());
               }
            }
         }));
      }
   }

   private void actionBar(TeleportRequest request, String suffix, String seconds) {
      if (request.actionBar() && request.player().isOnline()) {
         request.player().sendActionBar(this.plugin.messages().component(request.key(suffix), request.placeholderArray("seconds", seconds)));
      }
   }

   private static final class Pending {
      private final Player player;
      private final TeleportRequest request;
      private final Location origin;
      private int remaining;
      private BukkitTask task;

      private Pending(Player player, TeleportRequest request, Location origin, int remaining) {
         super();
         this.player = player;
         this.request = request;
         this.origin = origin;
         this.remaining = remaining;
      }
   }
}
