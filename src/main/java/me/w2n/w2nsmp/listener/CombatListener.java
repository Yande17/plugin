package me.w2n.w2nsmp.listener;

import java.util.List;
import java.util.Locale;
import me.w2n.w2nsmp.W2NSMP;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class CombatListener implements Listener {
   private final W2NSMP plugin;

   public CombatListener(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onDamage(EntityDamageByEntityEvent event) {
      if (this.plugin.config().combatEnabled() && !(event.getFinalDamage() <= 0.0) && event.getEntity() instanceof Player victim) {
         Player attacker = this.attackerOf(event.getDamager());
         if (attacker != null) {
            if (!attacker.getUniqueId().equals(victim.getUniqueId())) {
               if (this.plugin.config().combatTagOnAttack()) {
                  this.plugin.combat().tagPair(attacker, victim);
               } else {
                  this.plugin.combat().tag(victim, attacker);
               }
            }
         } else {
            if (this.plugin.config().combatTagMobs()) {
               this.plugin.combat().tag(victim, null);
            }
         }
      }
   }

   private Player attackerOf(Entity damager) {
      if (damager instanceof Player player) {
         return player;
      } else {
         return damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter ? shooter : null;
      }
   }

   @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
   public void onCommand(PlayerCommandPreprocessEvent event) {
      if (this.plugin.config().combatEnabled()) {
         Player player = event.getPlayer();
         if (this.plugin.combat().isTagged(player) && !this.plugin.combat().bypasses(player)) {
            List<String> blocked = this.plugin.config().combatBlockedCommands();
            if (!blocked.isEmpty()) {
               String label = commandLabel(event.getMessage());
               if (!label.isEmpty() && blocked.contains(label)) {
                  event.setCancelled(true);
                  this.plugin
                     .messages()
                     .send(player, "combat.command-blocked", "command", label, "seconds", Integer.toString(this.plugin.combat().remainingSeconds(player)));
               }
            }
         }
      }
   }

   static String commandLabel(String message) {
      if (message != null && !message.isEmpty()) {
         String text = message.startsWith("/") ? message.substring(1) : message;
         int space = text.indexOf(32);
         if (space >= 0) {
            text = text.substring(0, space);
         }

         int colon = text.indexOf(58);
         if (colon >= 0 && colon + 1 < text.length()) {
            text = text.substring(colon + 1);
         }

         return text.toLowerCase(Locale.ROOT).trim();
      } else {
         return "";
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onDeath(PlayerDeathEvent event) {
      this.plugin.combat().untag(event.getEntity());
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onQuit(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      if (this.plugin.combat().isTagged(player)) {
         int seconds = this.plugin.combat().remainingSeconds(player);
         String name = player.getName();
         boolean punish = this.plugin.config().combatPenaltyEnabled() && this.plugin.config().combatPenaltyKill() && !this.plugin.combat().bypasses(player);
         this.plugin.combat().untag(player);
         if (!punish) {
            this.plugin.debug("Combat: " + name + " keluar saat combat (sisa " + seconds + "s) tanpa hukuman.");
         } else {
            try {
               if (!this.plugin.config().combatPenaltyDropInventory()) {
                  player.getInventory().clear();
               }

               player.setHealth(0.0);
            } catch (RuntimeException exception) {
               this.plugin.getLogger().warning("Gagal menjalankan hukuman combat log untuk " + name + ": " + exception);
               return;
            }

            this.plugin.combat().countPunishment();
            this.plugin
               .getLogger()
               .info(
                  "Combat log: "
                     + name
                     + " keluar saat combat (sisa "
                     + seconds
                     + "s) dan tewas"
                     + (this.plugin.config().combatPenaltyDropInventory() ? " (item jatuh)." : " (item dihapus).")
               );
            if (this.plugin.config().combatPenaltyBroadcast()) {
               this.plugin
                  .getServer()
                  .broadcast(this.plugin.messages().component("combat.logout-broadcast", "player", name, "seconds", Integer.toString(seconds)));
            }
         }
      }
   }
}
