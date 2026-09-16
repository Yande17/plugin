package me.w2n.w2nsmp.rtp;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.stats.StatType;
import me.w2n.w2nsmp.teleport.TeleportRequest;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class RtpService {
   private static final int HARD_ATTEMPT_LIMIT = 200;
   private final W2NSMP plugin;
   private final Random random = new Random();
   private final Map<UUID, Long> cooldownUntil = new HashMap<>();
   private final Set<UUID> searching = new HashSet<>();

   public RtpService(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   public void request(Player player) {
      if (!this.plugin.config().rtpEnabled()) {
         this.plugin.messages().send(player, "rtp.disabled");
      } else if (this.plugin.combat() != null && this.plugin.config().combatBlockTeleport() && this.plugin.combat().blocksTeleport(player)) {
         this.plugin.messages().send(player, "rtp.teleport-blocked-combat", "seconds", Integer.toString(this.plugin.combat().remainingSeconds(player)));
      } else {
         long remaining = this.cooldownMillis(player);
         if (remaining > 0L) {
            this.plugin.messages().send(player, "rtp.cooldown", "seconds", Long.toString((remaining + 999L) / 1000L));
         } else {
            String worldName = this.plugin.config().rtpWorld();
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
               this.plugin.messages().send(player, "rtp.world-missing", "world", worldName);
            } else if (!this.searching.add(player.getUniqueId())) {
               this.plugin.messages().send(player, "rtp.busy", "world", world.getName());
            } else {
               this.cooldownUntil.put(player.getUniqueId(), System.currentTimeMillis() + Math.max(0, this.plugin.config().rtpCooldownSeconds()) * 1000L);
               this.plugin.messages().send(player, "rtp.searching", "world", world.getName());
               this.plugin.debug("RTP dimulai untuk " + player.getName() + " di dunia " + world.getName() + ".");
               this.attempt(player, world, 1);
            }
         }
      }
   }

   public long cooldownMillis(Player player) {
      Long until = this.cooldownUntil.get(player.getUniqueId());
      if (until == null) {
         return 0L;
      } else {
         long remaining = until - System.currentTimeMillis();
         if (remaining <= 0L) {
            this.cooldownUntil.remove(player.getUniqueId());
            return 0L;
         } else {
            return remaining;
         }
      }
   }

   public void clearState() {
      this.cooldownUntil.clear();
      this.searching.clear();
   }

   public int searchingCount() {
      return this.searching.size();
   }

   private void attempt(Player player, World world, int attempt) {
      if (player.isOnline() && this.plugin.isEnabled()) {
         int maxAttempts = Math.min(200, Math.max(1, this.plugin.config().rtpMaxAttempts()));
         if (attempt > maxAttempts) {
            this.searching.remove(player.getUniqueId());
            this.cooldownUntil.remove(player.getUniqueId());
            this.plugin.messages().send(player, "rtp.no-safe-location", "world", world.getName(), "attempts", Integer.toString(maxAttempts));
            this.plugin.getLogger().info("RTP gagal menemukan lokasi aman untuk " + player.getName() + " setelah " + maxAttempts + " percobaan.");
         } else {
            Location spawn = world.getSpawnLocation();
            double minRadius = Math.max(0, this.plugin.config().rtpMinRadius());
            double maxRadius = Math.max(minRadius, this.plugin.config().rtpMaxRadius());
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double radius = minRadius + this.random.nextDouble() * (maxRadius - minRadius);
            int x = spawn.getBlockX() + (int)Math.round(Math.cos(angle) * radius);
            int z = spawn.getBlockZ() + (int)Math.round(Math.sin(angle) * radius);
            world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> Bukkit.getScheduler().runTask(this.plugin, () -> {
               if (player.isOnline() && this.plugin.isEnabled()) {
                  Location safe = findSafe(world, x, z);
                  if (safe == null) {
                     this.attempt(player, world, attempt + 1);
                  } else {
                     this.searching.remove(player.getUniqueId());
                     this.teleport(player, safe);
                  }
               } else {
                  this.searching.remove(player.getUniqueId());
               }
            }));
         }
      } else {
         this.searching.remove(player.getUniqueId());
      }
   }

   private void teleport(Player player, Location safe) {
      String coords = safe.getBlockX() + ", " + safe.getBlockY() + ", " + safe.getBlockZ();
      this.plugin.debug("RTP: " + player.getName() + " -> " + coords);
      boolean started = this.plugin
         .teleport()
         .start(
            TeleportRequest.builder(player, safe, "rtp")
               .placeholder("world", safe.getWorld() == null ? "?" : safe.getWorld().getName())
               .placeholder("x", Integer.toString(safe.getBlockX()))
               .placeholder("y", Integer.toString(safe.getBlockY()))
               .placeholder("z", Integer.toString(safe.getBlockZ()))
               .placeholder("coords", coords)
               .delaySeconds(this.plugin.config().rtpTeleportDelay())
               .actionBar(this.plugin.config().rtpActionBar())
               .cancelOnMove(this.plugin.config().rtpCancelOnMove())
               .cancelOnDamage(this.plugin.config().rtpCancelOnDamage())
               .cancelOnDeath(this.plugin.config().rtpCancelOnDeath())
               .blockedInCombat(this.plugin.config().combatBlockTeleport())
               .statType(StatType.RTP)
               .build()
         );
      if (!started) {
         this.cooldownUntil.remove(player.getUniqueId());
      }
   }

   static Location findSafe(World world, int x, int z) {
      if (world.getEnvironment() == Environment.NETHER) {
         int top = Math.min(120, world.getMaxHeight() - 3);

         for (int y = top; y > world.getMinHeight() + 1; y--) {
            Location candidate = evaluate(world, x, z, y);
            if (candidate != null) {
               return candidate;
            }
         }

         return null;
      } else {
         return evaluate(world, x, z, world.getHighestBlockAt(x, z).getY());
      }
   }

   private static Location evaluate(World world, int x, int z, int groundY) {
      if (groundY > world.getMinHeight() && groundY < world.getMaxHeight() - 2) {
         Block ground = world.getBlockAt(x, groundY, z);
         Material groundType = ground.getType();
         if (groundType.isSolid() && !isDangerous(groundType)) {
            Block feet = world.getBlockAt(x, groundY + 1, z);
            Block head = world.getBlockAt(x, groundY + 2, z);
            return isPassable(feet) && isPassable(head) ? new Location(world, x + 0.5, groundY + 1.0, z + 0.5) : null;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static boolean isDangerous(Material type) {
      return switch (type) {
         case LAVA, WATER, FIRE, SOUL_FIRE, MAGMA_BLOCK, CACTUS, SWEET_BERRY_BUSH, POWDER_SNOW, CAMPFIRE, SOUL_CAMPFIRE, LAVA_CAULDRON, POINTED_DRIPSTONE, BEDROCK, WITHER_ROSE, SCULK_SHRIEKER, COBWEB, END_PORTAL, NETHER_PORTAL, END_PORTAL_FRAME -> true;
         default -> false;
      };
   }

   private static boolean isPassable(Block block) {
      if (block.isLiquid()) {
         return false;
      }

      Material type = block.getType();
      return type.isAir() || !type.isSolid();
   }
}
