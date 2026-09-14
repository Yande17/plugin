package me.w2n.w2nsmp.listener;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.skill.BuffKind;
import me.w2n.w2nsmp.skill.SkillService;
import me.w2n.w2nsmp.skill.SkillSettings;
import me.w2n.w2nsmp.skill.SkillType;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Sumber XP dan penerapan buff skill dari event permainan.
 *
 * <p>Semua handler memakai {@code EventPriority.MONITOR} + {@code ignoreCancelled = true}:
 * plugin ini membaca keadaan <b>sesudah</b> plugin lain memutuskan (mis. proteksi wilayah
 * membatalkan block break), jadi tidak ada fitur lain yang perilakunya berubah. Buff damage
 * tetap berpengaruh karena nilai damage baru dipakai server setelah seluruh handler selesai.
 *
 * <p>Setiap pemanggilan API dijaga {@code try/catch} dan ketersediaan API diperiksa lewat
 * {@link me.w2n.w2nsmp.skill.SkillApiProbe}, sehingga versi server yang berbeda tidak pernah
 * membuat listener ini melempar exception ke server.
 */
public final class SkillListener implements Listener {
   private static final SkillType[] BLOCK_SKILLS = new SkillType[]{SkillType.MINING, SkillType.WOODCUTTING, SkillType.FARMING};
   private final W2NSMP plugin;

   public SkillListener(W2NSMP plugin) {
      this.plugin = plugin;
   }

   /** Catat kegagalan handler sekali per lokasi (WARNING di konsol, rincian di /skill check). */
   private void note(Throwable throwable, String where) {
      SkillService service = this.plugin.skills();
      if (service != null) {
         service.diagnostics().noteError(where, throwable);
      } else {
         this.plugin.getLogger().warning("Skill: gangguan di " + where + " -> " + throwable);
      }
   }

   private SkillService service() {
      SkillService service = this.plugin.skills();
      return service != null && service.enabled() ? service : null;
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onJoin(PlayerJoinEvent event) {
      // Seluruh isi handler dibungkus: satu API yang hilang di sebuah versi server tidak
      // boleh membuat handler berhenti di tengah (mis. klik/XP batal diproses) dan tidak
      // boleh pula hilang tanpa jejak - kegagalannya dicatat untuk /skill check.
      try {
         this.handleJoin(event);
      } catch (Throwable throwable) {
         this.note(throwable, "player-join");
      }
   }

   private void handleJoin(PlayerJoinEvent event) {
      if (this.plugin.skills() != null) {
         this.plugin.skills().onJoin(event.getPlayer());
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onQuit(PlayerQuitEvent event) {
      // Seluruh isi handler dibungkus: satu API yang hilang di sebuah versi server tidak
      // boleh membuat handler berhenti di tengah (mis. klik/XP batal diproses) dan tidak
      // boleh pula hilang tanpa jejak - kegagalannya dicatat untuk /skill check.
      try {
         this.handleQuit(event);
      } catch (Throwable throwable) {
         this.note(throwable, "player-quit");
      }
   }

   private void handleQuit(PlayerQuitEvent event) {
      if (this.plugin.skills() != null) {
         this.plugin.skills().onQuit(event.getPlayer());
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onDeath(PlayerDeathEvent event) {
      // Seluruh isi handler dibungkus: satu API yang hilang di sebuah versi server tidak
      // boleh membuat handler berhenti di tengah (mis. klik/XP batal diproses) dan tidak
      // boleh pula hilang tanpa jejak - kegagalannya dicatat untuk /skill check.
      try {
         this.handleDeath(event);
      } catch (Throwable throwable) {
         this.note(throwable, "player-death");
      }
   }

   private void handleDeath(PlayerDeathEvent event) {
      if (this.plugin.skills() != null) {
         // Darah akan diisi ulang saat respawn; lupakan angka lama agar regen tidak terhitung.
         this.plugin.skills().onRespawnOrHealReset(event.getEntity());
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onBreak(BlockBreakEvent event) {
      // Seluruh isi handler dibungkus: satu API yang hilang di sebuah versi server tidak
      // boleh membuat handler berhenti di tengah (mis. klik/XP batal diproses) dan tidak
      // boleh pula hilang tanpa jejak - kegagalannya dicatat untuk /skill check.
      try {
         this.handleBreak(event);
      } catch (Throwable throwable) {
         this.note(throwable, "block-break");
      }
   }

   private void handleBreak(BlockBreakEvent event) {
      SkillService service = this.service();
      if (service == null || !service.probe().blockApi()) {
         return;
      }

      Player player = event.getPlayer();
      if (player == null || !service.worldAllowed(player.getWorld()) || !service.gameModeAllowed(player)) {
         return;
      }

      Block block;
      try {
         block = event.getBlock();
      } catch (Throwable throwable) {
         return;
      }

      if (block == null) {
         return;
      }

      Material material;
      try {
         material = block.getType();
      } catch (Throwable throwable) {
         return;
      }

      if (material == null) {
         return;
      }

      double vanillaXpBoost = 0.0D;

      for (SkillType type : BLOCK_SKILLS) {
         SkillSettings settings = service.settings(type);
         if (!settings.enabled() || !settings.hasBlocks() || !settings.blocks().contains(material)) {
            continue;
         }

         if (settings.xpPerBlock() > 0.0D) {
            service.addXp(player, type, settings.xpPerBlock());
         }

         if (service.rollChance(player, type)) {
            this.extraBlockDrop(player, block, type, service);
         }

         // Satu blok bisa cocok dengan beberapa skill; buff XP vanilla diambil yang terbesar saja
         // supaya XP ore tidak pernah digandakan dua kali dalam satu pukulan.
         vanillaXpBoost = Math.max(vanillaXpBoost, service.buffValue(player, type, BuffKind.VANILLA_XP));
      }

      if (vanillaXpBoost > 0.0D) {
         this.boostBlockExp(event, vanillaXpBoost, service);
      }
   }

   /** XP vanilla blok (ore) ditambah sekian persen - buff VANILLA_XP milik mining. */
   private void boostBlockExp(BlockBreakEvent event, double percent, SkillService service) {
      if (!service.probe().blockExpApi() || !(percent > 0.0D)) {
         return;
      }

      try {
         int base = event.getExpToDrop();
         if (base <= 0) {
            return;
         }

         int extra = (int) Math.round((double) base * percent / 100.0D);
         if (extra > 0) {
            event.setExpToDrop(base + extra);
         }
      } catch (Throwable throwable) {
         this.plugin.debug("Skill: gagal menambah XP vanilla blok (" + throwable + ").");
      }
   }

   /** Satu drop tambahan (buff woodcutting/farming) - diambil dari drop asli blok itu. */
   private void extraBlockDrop(Player player, Block block, SkillType type, SkillService service) {
      if (!service.probe().blockDropsApi()) {
         return;
      }

      try {
         Collection<ItemStack> drops = block.getDrops();
         if (drops == null || drops.isEmpty()) {
            return;
         }

         boolean given = false;

         for (ItemStack drop : drops) {
            if (drop == null || drop.getType() == null || drop.getType().isAir()) {
               continue;
            }

            ItemStack copy = drop.clone();
            if (copy == null) {
               continue;
            }

            given |= this.give(player, copy);
         }

         if (given && service.notifyExtraDrop()) {
            this.plugin.messages().send(player, "skill.extra-drop", "skill", service.label(type));
         }
      } catch (Throwable throwable) {
         this.plugin.debug("Skill: gagal memberi drop tambahan " + type.key() + " untuk " + player.getName() + " (" + throwable + ").");
      }
   }

   /** Masukkan item ke inventory; sisa yang tidak muat dijatuhkan di kaki pemain. */
   private boolean give(Player player, ItemStack stack) {
      if (player == null || stack == null) {
         return false;
      }

      HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
      if (leftover == null || leftover.isEmpty()) {
         return true;
      }

      for (ItemStack rest : leftover.values()) {
         if (rest != null && player.getWorld() != null) {
            try {
               player.getWorld().dropItemNaturally(player.getLocation(), rest);
            } catch (Throwable throwable) {
               this.plugin.debug("Skill: gagal menjatuhkan item sisa (" + throwable + ").");
            }
         }
      }

      return true;
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onMove(PlayerMoveEvent event) {
      // Seluruh isi handler dibungkus: satu API yang hilang di sebuah versi server tidak
      // boleh membuat handler berhenti di tengah (mis. klik/XP batal diproses) dan tidak
      // boleh pula hilang tanpa jejak - kegagalannya dicatat untuk /skill check.
      try {
         this.handleMove(event);
      } catch (Throwable throwable) {
         this.note(throwable, "player-move");
      }
   }

   private void handleMove(PlayerMoveEvent event) {
      SkillService service = this.service();
      if (service != null) {
         service.onMove(event.getPlayer(), event.getTo());
      }
   }

   /** Serangan pemain (tangan/pedang atau proyektil): buff damage + XP fighting/archery. */
   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onDamageByEntity(EntityDamageByEntityEvent event) {
      // Seluruh isi handler dibungkus: satu API yang hilang di sebuah versi server tidak
      // boleh membuat handler berhenti di tengah (mis. klik/XP batal diproses) dan tidak
      // boleh pula hilang tanpa jejak - kegagalannya dicatat untuk /skill check.
      try {
         this.handleDamageByEntity(event);
      } catch (Throwable throwable) {
         this.note(throwable, "damage-by-entity");
      }
   }

   private void handleDamageByEntity(EntityDamageByEntityEvent event) {
      SkillService service = this.service();
      if (service == null || !service.probe().damageApi()) {
         return;
      }

      if (!(event.getEntity() instanceof LivingEntity victim)) {
         return;
      }

      Player attacker = null;
      boolean projectile = false;
      Entity damager = event.getDamager();
      if (damager instanceof Player direct) {
         attacker = direct;
      } else if (damager instanceof Projectile shot) {
         ProjectileSource source;
         try {
            source = shot.getShooter();
         } catch (Throwable throwable) {
            return;
         }

         if (source instanceof Player shooter) {
            attacker = shooter;
            projectile = true;
         }
      }

      if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
         return;
      }

      SkillType type = projectile ? SkillType.ARCHERY : SkillType.FIGHTING;
      SkillSettings settings = service.settings(type);
      if (!settings.enabled() || !service.worldAllowed(attacker.getWorld()) || !service.gameModeAllowed(attacker)) {
         return;
      }

      double damage;
      try {
         damage = event.getDamage();
      } catch (Throwable throwable) {
         return;
      }

      if (!(damage > 0.0D)) {
         return;
      }

      double multiplier = service.damageMultiplier(attacker, type);
      boolean critical = service.rollCrit(attacker, type);
      if (critical) {
         multiplier *= service.critMultiplier(attacker, type);
      }

      if (multiplier != 1.0D) {
         try {
            event.setDamage(damage * multiplier);
         } catch (Throwable throwable) {
            this.plugin.debug("Skill: setDamage ditolak server (" + throwable + ").");
         }
      }

      if (critical && service.notifyCrit() && this.plugin.messages().has("skill.crit")) {
         try {
            attacker.sendActionBar(this.plugin.messages().component("skill.crit",
               "skill", service.label(type),
               "damage", SkillService.format(damage * multiplier),
               "power", SkillService.format((multiplier - 1.0D) * 100.0D)));
         } catch (Throwable throwable) {
            this.plugin.debug("Skill: gagal mengirim actionbar critical (" + throwable + ").");
         }
      }

      if (settings.xpDamageDealt() > 0.0D) {
         boolean pvp = victim instanceof Player;
         service.addXp(attacker, type, damage * settings.xpDamageDealt() * (pvp ? settings.xpPvpMultiplier() : 1.0D));
      }

      if (projectile) {
         service.noteProjectileHit(victim.getUniqueId());
      }
   }

   /** Damage yang diterima pemain: buff pengurangan + XP defense/vitality. */
   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onDamage(EntityDamageEvent event) {
      // Seluruh isi handler dibungkus: satu API yang hilang di sebuah versi server tidak
      // boleh membuat handler berhenti di tengah (mis. klik/XP batal diproses) dan tidak
      // boleh pula hilang tanpa jejak - kegagalannya dicatat untuk /skill check.
      try {
         this.handleDamage(event);
      } catch (Throwable throwable) {
         this.note(throwable, "damage");
      }
   }

   private void handleDamage(EntityDamageEvent event) {
      SkillService service = this.service();
      if (service == null || !service.probe().damageApi()) {
         return;
      }

      if (!(event.getEntity() instanceof Player victim)) {
         return;
      }

      double damage;
      try {
         damage = event.getDamage();
      } catch (Throwable throwable) {
         return;
      }

      if (!(damage > 0.0D)) {
         return;
      }

      String cause = this.causeName(event, service);
      double multiplier = service.damageTakenMultiplier(victim, cause);
      if (multiplier != 1.0D) {
         try {
            event.setDamage(damage * multiplier);
         } catch (Throwable throwable) {
            this.plugin.debug("Skill: setDamage (bertahan) ditolak server (" + throwable + ").");
         }
      }

      service.noteDamage(victim);
      if (!service.worldAllowed(victim.getWorld()) || !service.gameModeAllowed(victim)) {
         return;
      }

      SkillSettings defense = service.settings(SkillType.DEFENSE);
      if (defense.enabled() && defense.xpDamageTaken() > 0.0D) {
         service.addXp(victim, SkillType.DEFENSE, damage * defense.xpDamageTaken());
      }

      SkillSettings vitality = service.settings(SkillType.VITALITY);
      if (vitality.enabled() && vitality.xpDamageSurvived() > 0.0D && service.probe().healthApi()) {
         try {
            double after = victim.getHealth() - event.getFinalDamage();
            if (after > 0.0D && after <= vitality.xpLowHealthThreshold()) {
               service.addXp(victim, SkillType.VITALITY, damage * vitality.xpDamageSurvived() * vitality.xpLowHealthMultiplier());
            }
         } catch (Throwable throwable) {
            this.plugin.debug("Skill: gagal menghitung XP vitality (" + throwable + ").");
         }
      }
   }

   /** Nama penyebab damage (mis. "FALL"); null bila API-nya tidak ada di server ini. */
   private String causeName(EntityDamageEvent event, SkillService service) {
      if (!service.probe().damageCauseApi()) {
         return null;
      }

      try {
         EntityDamageEvent.DamageCause cause = event.getCause();
         return cause == null ? null : cause.name();
      } catch (Throwable throwable) {
         return null;
      }
   }

   /** XP kill: archery bila pukulan terakhir proyektil, selain itu fighting. */
   @EventHandler(priority = EventPriority.MONITOR)
   public void onEntityDeath(EntityDeathEvent event) {
      // Seluruh isi handler dibungkus: satu API yang hilang di sebuah versi server tidak
      // boleh membuat handler berhenti di tengah (mis. klik/XP batal diproses) dan tidak
      // boleh pula hilang tanpa jejak - kegagalannya dicatat untuk /skill check.
      try {
         this.handleEntityDeath(event);
      } catch (Throwable throwable) {
         this.note(throwable, "entity-death");
      }
   }

   private void handleEntityDeath(EntityDeathEvent event) {
      SkillService service = this.service();
      if (service == null) {
         return;
      }

      LivingEntity entity = event.getEntity();
      if (entity == null) {
         return;
      }

      Player killer;
      try {
         killer = entity.getKiller();
      } catch (Throwable throwable) {
         return;
      }

      boolean projectile = service.wasProjectileKill(entity.getUniqueId());
      if (killer == null || !killer.isOnline() || !service.worldAllowed(killer.getWorld()) || !service.gameModeAllowed(killer)) {
         return;
      }

      SkillType type = projectile ? SkillType.ARCHERY : SkillType.FIGHTING;
      SkillSettings settings = service.settings(type);
      if (!settings.enabled()) {
         return;
      }

      double amount = entity instanceof Player ? settings.xpKillPlayer() : settings.xpKillMob();
      if (amount > 0.0D) {
         service.addXp(killer, type, amount);
      }

      if (!(entity instanceof Player)) {
         this.extraMobLoot(event, killer, type, service);
      }
   }

   /**
    * Buff MOB_LOOT: satu drop mob diduplikasi bila peluang terpenuhi. Drop diambil dari daftar drop
    * event (bukan dihitung sendiri), jadi plugin lain yang mengubah loot tetap dihormati - kita
    * hanya menambah satu salinan.
    */
   private void extraMobLoot(EntityDeathEvent event, Player killer, SkillType type, SkillService service) {
      if (!service.probe().mobLootApi() || !service.rollChance(killer, type, BuffKind.MOB_LOOT)) {
         return;
      }

      try {
         List<ItemStack> drops = event.getDrops();
         if (drops == null || drops.isEmpty()) {
            return;
         }

         int index = service.randomIndex(drops.size());
         if (index < 0) {
            return;
         }

         ItemStack source = drops.get(index);
         if (source == null || source.getType() == null || source.getType().isAir()) {
            return;
         }

         ItemStack copy = source.clone();
         if (copy == null) {
            return;
         }

         drops.add(copy);
         if (service.notifyExtraDrop()) {
            this.plugin.messages().send(killer, "skill.mob-loot", "skill", service.label(type));
         }
      } catch (Throwable throwable) {
         this.plugin.debug("Skill: gagal menggandakan drop mob " + type.key() + " untuk " + killer.getName() + " (" + throwable + ").");
      }
   }
}
