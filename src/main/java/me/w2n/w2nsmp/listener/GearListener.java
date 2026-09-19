package me.w2n.w2nsmp.listener;

import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.gear.GearClass;
import me.w2n.w2nsmp.gear.GearPerk;
import me.w2n.w2nsmp.gear.GearService;
import me.w2n.w2nsmp.utility.Items;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Penerapan riwayat & perk gear saat combat (v1.4.0).
 *
 * <p>Alur: pukulan -> cap pemilik pertama + counter pemakaian + perk senjata (bleeding,
 * lifesteal, bonus-damage); damage diterima -> counter armor + perk resistance; kill ->
 * counter kill + pengumuman milestone. Semua nilai dari config ({@code gear.*}); syarat skill
 * yang tidak terpenuhi hanya mengunci perk - pemakaian dasar tetap jalan (item requirement 7).
 */
public final class GearListener implements Listener {
   private final W2NSMP plugin;

   public GearListener(W2NSMP plugin) {
      this.plugin = plugin;
   }

   // ------------------------------------------------------------------ //
   // Pukulan: senjata penyerang + armor korban
   // ------------------------------------------------------------------ //

   @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
   public void onDamage(EntityDamageByEntityEvent event) {
      try {
         this.handleDamage(event);
      } catch (Throwable throwable) {
         this.plugin.debug("Gear: gangguan di handler damage (" + throwable + ").");
      }
   }

   private void handleDamage(EntityDamageByEntityEvent event) {
      GearService gear = this.plugin.gear();
      if (gear == null || !gear.enabled()) {
         return;
      }

      Player attacker = attackerOf(event);
      if (attacker != null && event.getEntity() instanceof LivingEntity victim) {
         this.handleAttack(event, gear, attacker, victim);
      }

      if (event.getEntity() instanceof Player defender) {
         this.handleDefense(event, gear, defender);
      }
   }

   private void handleAttack(EntityDamageByEntityEvent event, GearService gear, Player attacker, LivingEntity victim) {
      ItemStack weapon = attacker.getInventory().getItemInMainHand();
      GearClass gearClass = Items.isEmpty(weapon) ? null : gear.classFor(weapon.getType());
      if (gearClass == null || gearClass.isArmor()) {
         return;
      }

      // v1.5.4 (PHASE 4): enforcement menyala -> serangan dengan senjata yang syaratnya
      // tidak dipenuhi PEMAIN INI dibatalkan penuh (bukan cuma perk terkunci). Counter &
      // riwayat tidak bertambah karena serangan tidak terjadi.
      if (gear.enforceRequirements()) {
         String reason = gear.denyReason(attacker, weapon);
         if (reason != null) {
            event.setCancelled(true);
            gear.sendDeny(attacker, reason);
            return;
         }
      }

      gear.stampFirstOwner(attacker, weapon);
      gear.increment(weapon, gearClass, false);
      // getItemInMainHand mengembalikan SALINAN di CraftBukkit: tulis balik supaya PDC tersimpan.
      attacker.getInventory().setItemInMainHand(weapon);

      if (!gear.meetsRequirement(attacker, weapon, gearClass)) {
         // Enforcement mati (mode lama): perk terkunci; damage dasar bisa dipangkas via config.
         double percent = gear.unmetDamagePercent();
         if (percent < 100.0D) {
            event.setDamage(event.getDamage() * percent / 100.0D);
         }

         return;
      }

      int count = gear.counter(weapon, gearClass);
      long now = System.currentTimeMillis();

      for (GearPerk perk : gearClass.unlocked(count)) {
         switch (perk.kind()) {
            case "bonus-damage" -> event.setDamage(event.getDamage() * (1.0D + perk.value() / 100.0D));
            case "lifesteal" -> {
               if (gear.tryCooldown(attacker.getUniqueId(), gearClass, perk, now)) {
                  this.heal(attacker, event.getFinalDamage() * perk.value() / 100.0D, gear.healCap());
               }
            }
            case "bleeding" -> {
               if (gear.tryCooldown(attacker.getUniqueId(), gearClass, perk, now)) {
                  this.bleed(victim, perk);
               }
            }
            default -> {
            }
         }
      }
   }

   private void handleDefense(EntityDamageByEntityEvent event, GearService gear, Player defender) {
      ItemStack[] armor;
      try {
         armor = defender.getInventory().getArmorContents();
      } catch (Throwable throwable) {
         return;
      }

      if (armor == null) {
         return;
      }

      double reductionPercent = 0.0D;
      boolean changed = false;

      for (ItemStack piece : armor) {
         GearClass gearClass = Items.isEmpty(piece) ? null : gear.classFor(piece.getType());
         if (gearClass == null || !gearClass.isArmor()) {
            continue;
         }

         gear.stampFirstOwner(defender, piece);
         gear.increment(piece, gearClass, false);
         changed = true;

         if (!gear.meetsRequirement(defender, piece, gearClass)) {
            continue;
         }

         int count = gear.counter(piece, gearClass);
         for (GearPerk perk : gearClass.unlocked(count)) {
            if ("resistance".equals(perk.kind())) {
               reductionPercent += perk.value();
            }
         }
      }

      if (changed) {
         // getArmorContents mengembalikan SALINAN: tulis balik supaya counter/lore tersimpan.
         try {
            defender.getInventory().setArmorContents(armor);
         } catch (Throwable ignored) {
         }
      }

      // Plafon 80% supaya set armor tidak pernah kebal penuh (tidak OP).
      reductionPercent = Math.min(80.0D, reductionPercent);
      if (reductionPercent > 0.0D) {
         event.setDamage(event.getDamage() * (1.0D - reductionPercent / 100.0D));
      }
   }

   // ------------------------------------------------------------------ //
   // Kill: counter kill senjata + pengumuman milestone
   // ------------------------------------------------------------------ //

   @EventHandler(priority = EventPriority.MONITOR)
   public void onDeath(EntityDeathEvent event) {
      try {
         this.handleDeath(event);
      } catch (Throwable throwable) {
         this.plugin.debug("Gear: gangguan di handler kill (" + throwable + ").");
      }
   }

   private void handleDeath(EntityDeathEvent event) {
      GearService gear = this.plugin.gear();
      if (gear == null || !gear.enabled()) {
         return;
      }

      LivingEntity entity = event.getEntity();
      Player killer = entity == null ? null : entity.getKiller();
      if (killer == null) {
         return;
      }

      ItemStack weapon = killer.getInventory().getItemInMainHand();
      GearClass gearClass = Items.isEmpty(weapon) ? null : gear.classFor(weapon.getType());
      if (gearClass == null || gearClass.isArmor()) {
         return;
      }

      gear.stampFirstOwner(killer, weapon);

      // Hanya kill PEMAIN yang dihitung untuk riwayat "Player Kills"; counter pemakaian
      // sudah bertambah lewat pukulan. Salinan item selalu ditulis balik supaya cap
      // pemilik pertama tidak hilang.
      GearPerk crossed = entity instanceof Player ? gear.increment(weapon, gearClass, true) : null;
      killer.getInventory().setItemInMainHand(weapon);

      if (crossed != null && gear.notifyMilestone()) {
         this.plugin.messages().send(killer, "gear.milestone",
            "perk", gear.perkLabel(crossed),
            "threshold", Integer.toString(crossed.threshold()));
      }
   }

   // ------------------------------------------------------------------ //

   /** Damage berkala perk bleeding: dijadwalkan di main thread, berhenti bila korban mati. */
   private void bleed(LivingEntity victim, GearPerk perk) {
      long intervalTicks = perk.intervalSeconds() * 20L;

      for (int tick = 1; tick <= perk.ticks(); tick++) {
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            try {
               if (victim.getHealth() > 0.0D) {
                  victim.damage(perk.value());
               }
            } catch (Throwable ignored) {
            }
         }, intervalTicks * tick);
      }
   }

   private void heal(Player player, double amount, double cap) {
      if (amount <= 0.0D) {
         return;
      }

      try {
         double health = player.getHealth();
         if (health <= 0.0D || health >= cap) {
            return;
         }

         player.setHealth(Math.min(cap, health + amount));
      } catch (Throwable ignored) {
      }
   }

   private static Player attackerOf(EntityDamageByEntityEvent event) {
      if (event.getDamager() instanceof Player player) {
         return player;
      }

      if (event.getDamager() instanceof Projectile projectile) {
         try {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
               return player;
            }
         } catch (Throwable ignored) {
         }
      }

      return null;
   }
}
