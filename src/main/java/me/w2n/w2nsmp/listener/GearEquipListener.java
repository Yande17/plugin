package me.w2n.w2nsmp.listener;

import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.gear.GearService;
import me.w2n.w2nsmp.utility.Items;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseArmorEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Penegakan syarat gear di SEMUA jalur pemasangan (v1.5.4, PHASE 4).
 *
 * <p>Syarat item dibaca dari PDC item (lalu config class) dan SELALU dibandingkan dengan stat
 * pemain yang sedang mencoba memakai - bukan pemilik pertama. Jalur yang ditutup:
 *
 * <ul>
 *   <li>Klik inventory ke slot armor (place, number-key/hotbar swap, double-click, drag).</li>
 *   <li>Shift-click armor di inventory sendiri (auto-equip vanilla).</li>
 *   <li>Klik kanan armor di tangan (auto-equip vanilla) - {@link PlayerInteractEvent}.</li>
 *   <li>Tukar offhand dengan tombol F - {@link PlayerSwapHandItemsEvent}.</li>
 *   <li>Dispenser memasangkan armor - {@link BlockDispenseArmorEvent}.</li>
 *   <li>Sisa jalur tak langsung (death/respawn keep-inventory, relog, /give + plugin lain
 *       yang menulis slot armor langsung, GUI custom): pemindaian tertunda 1 tick lewat
 *       {@link #enforceEquipped(Player)} setelah join/respawn/klik yang menyentuh armor -
 *       potongan ilegal dicopot ke inventory (jatuh di kaki bila penuh), TIDAK pernah hilang.</li>
 * </ul>
 *
 * <p>Senjata/tool bersyarat ditolak saat DIPAKAI menyerang (lihat {@code GearListener}), karena
 * memegang item tidak bisa (dan tidak perlu) diblokir.
 */
public final class GearEquipListener implements Listener {
   private final W2NSMP plugin;

   public GearEquipListener(W2NSMP plugin) {
      this.plugin = plugin;
   }

   private GearService gear() {
      GearService gear = this.plugin.gear();
      return gear != null && gear.enabled() && gear.enforceRequirements() ? gear : null;
   }

   // ------------------------------------------------------------------ //
   // Klik & drag inventory
   // ------------------------------------------------------------------ //

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onInventoryClick(InventoryClickEvent event) {
      GearService gear = this.gear();
      if (gear == null || !(event.getWhoClicked() instanceof Player player)) {
         return;
      }

      try {
         boolean ownView = this.isOwnInventoryView(event);

         // 1. Klik langsung di slot armor (raw 5-8 pada view inventory sendiri): item yang
         //    akan masuk bisa berasal dari cursor (place) atau hotbar (number key).
         if (ownView && event.getRawSlot() >= 5 && event.getRawSlot() <= 8) {
            String reason = this.firstDeny(gear, player,
               cursorOf(event), hotbarItemOf(event, player));
            if (reason != null) {
               event.setCancelled(true);
               gear.sendDeny(player, reason);
               return;
            }
         }

         // 2. Shift-click armor di inventory sendiri = auto-equip vanilla.
         if (ownView && event.isShiftClick()) {
            ItemStack current = event.getCurrentItem();
            if (this.isArmor(gear, current)) {
               String reason = gear.denyReason(player, current);
               if (reason != null) {
                  event.setCancelled(true);
                  gear.sendDeny(player, reason);
                  return;
               }
            }
         }

         // 3. Tombol F di atas item (SWAP_OFFHAND): item yang diklik pindah ke offhand.
         String click = String.valueOf(event.getClick());
         if ("SWAP_OFFHAND".equals(click)) {
            ItemStack current = event.getCurrentItem();
            String reason = current == null ? null : gear.denyReason(player, current);
            if (reason != null) {
               event.setCancelled(true);
               gear.sendDeny(player, reason);
               return;
            }
         }

         // 4. Jaring pengaman: klik apa pun yang menyentuh area armor -> pindai 1 tick lagi.
         if (ownView && event.getRawSlot() >= 5 && event.getRawSlot() <= 8) {
            this.scheduleEnforce(player);
         }
      } catch (Throwable throwable) {
         this.plugin.debug("Gear: gangguan cek klik equip (" + throwable + ").");
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onInventoryDrag(InventoryDragEvent event) {
      GearService gear = this.gear();
      if (gear == null || !(event.getWhoClicked() instanceof Player player)) {
         return;
      }

      try {
         // Drag yang menyentuh slot armor (raw 5-8 di view inventory sendiri).
         if (event.getView() != null && event.getView().getTopInventory() != null
            && event.getView().getTopInventory().getType() == InventoryType.CRAFTING) {
            for (Integer raw : event.getRawSlots()) {
               if (raw != null && raw.intValue() >= 5 && raw.intValue() <= 8) {
                  this.scheduleEnforce(player);
                  return;
               }
            }
         }
      } catch (Throwable throwable) {
         this.plugin.debug("Gear: gangguan cek drag equip (" + throwable + ").");
      }
   }

   // ------------------------------------------------------------------ //
   // Auto-equip klik kanan, offhand F, dispenser
   // ------------------------------------------------------------------ //

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onInteract(PlayerInteractEvent event) {
      GearService gear = this.gear();
      if (gear == null) {
         return;
      }

      try {
         Player player = event.getPlayer();
         ItemStack item = event.getItem();
         if (player == null || !this.isArmor(gear, item)) {
            return;
         }

         String reason = gear.denyReason(player, item);
         if (reason != null) {
            // Klik kanan armor = auto-equip vanilla; batalkan interaksinya.
            event.setCancelled(true);
            gear.sendDeny(player, reason);
         }
      } catch (Throwable throwable) {
         this.plugin.debug("Gear: gangguan cek klik kanan equip (" + throwable + ").");
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onSwapHands(PlayerSwapHandItemsEvent event) {
      GearService gear = this.gear();
      if (gear == null) {
         return;
      }

      try {
         Player player = event.getPlayer();
         ItemStack toOffhand = event.getOffHandItem();
         String reason = player == null || toOffhand == null ? null : gear.denyReason(player, toOffhand);
         if (reason != null) {
            event.setCancelled(true);
            gear.sendDeny(player, reason);
         }
      } catch (Throwable throwable) {
         this.plugin.debug("Gear: gangguan cek swap offhand (" + throwable + ").");
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onDispenseArmor(BlockDispenseArmorEvent event) {
      GearService gear = this.gear();
      if (gear == null) {
         return;
      }

      try {
         if (event.getTargetEntity() instanceof Player player) {
            String reason = gear.denyReason(player, event.getItem());
            if (reason != null) {
               event.setCancelled(true);
               gear.sendDeny(player, reason);
            }
         }
      } catch (Throwable throwable) {
         this.plugin.debug("Gear: gangguan cek dispenser armor (" + throwable + ").");
      }
   }

   // ------------------------------------------------------------------ //
   // Jaring pengaman: join & respawn (menutup relog, keep-inventory, /give, plugin lain)
   // ------------------------------------------------------------------ //

   @EventHandler(priority = EventPriority.MONITOR)
   public void onJoin(PlayerJoinEvent event) {
      this.scheduleEnforce(event.getPlayer());
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onRespawn(PlayerRespawnEvent event) {
      this.scheduleEnforce(event.getPlayer());
   }

   /** Jadwalkan pemindaian armor+offhand 1 tick lagi (setelah aksi vanilla selesai). */
   private void scheduleEnforce(Player player) {
      if (player == null) {
         return;
      }

      Bukkit.getScheduler().runTask(this.plugin, () -> {
         try {
            if (player.isOnline()) {
               this.enforceEquipped(player);
            }
         } catch (Throwable throwable) {
            this.plugin.debug("Gear: gangguan pemindaian equip (" + throwable + ").");
         }
      });
   }

   /**
    * Copot armor & offhand yang syaratnya tidak dipenuhi PEMAIN INI: potongan dipindah ke
    * inventory (jatuh di kaki bila penuh) - item TIDAK pernah hilang atau terduplikasi.
    */
   public void enforceEquipped(Player player) {
      GearService gear = this.gear();
      if (gear == null || player == null) {
         return;
      }

      PlayerInventory inventory = player.getInventory();
      ItemStack[] armor;
      try {
         armor = inventory.getArmorContents();
      } catch (Throwable throwable) {
         return;
      }

      boolean changed = false;
      String lastReason = null;

      if (armor != null) {
         for (int i = 0; i < armor.length; i++) {
            ItemStack piece = armor[i];
            if (Items.isEmpty(piece)) {
               continue;
            }

            String reason = gear.denyReason(player, piece);
            if (reason != null) {
               armor[i] = null;
               changed = true;
               lastReason = reason;
               this.giveBack(player, piece);
            }
         }

         if (changed) {
            try {
               inventory.setArmorContents(armor);
            } catch (Throwable ignored) {
            }
         }
      }

      try {
         ItemStack offhand = inventory.getItemInOffHand();
         if (!Items.isEmpty(offhand)) {
            String reason = gear.denyReason(player, offhand);
            if (reason != null) {
               inventory.setItemInOffHand(null);
               lastReason = reason;
               this.giveBack(player, offhand);
            }
         }
      } catch (Throwable ignored) {
      }

      if (lastReason != null) {
         gear.sendDeny(player, lastReason);
      }
   }

   // ------------------------------------------------------------------ //

   /** Kembalikan item ke inventory; bila penuh jatuh di kaki - tidak pernah hilang. */
   private void giveBack(Player player, ItemStack stack) {
      try {
         java.util.HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
         if (leftover != null) {
            for (ItemStack rest : leftover.values()) {
               if (rest != null) {
                  player.getWorld().dropItemNaturally(player.getLocation(), rest);
               }
            }
         }
      } catch (Throwable throwable) {
         try {
            player.getWorld().dropItemNaturally(player.getLocation(), stack);
         } catch (Throwable ignored) {
         }
      }
   }

   /** View inventory sendiri (tanpa GUI): top inventory bertipe CRAFTING. */
   private boolean isOwnInventoryView(InventoryClickEvent event) {
      try {
         return event.getView() != null && event.getView().getTopInventory() != null
            && event.getView().getTopInventory().getType() == InventoryType.CRAFTING;
      } catch (Throwable throwable) {
         return false;
      }
   }

   /** Item armor menurut registry gear (class ber-slot "armor"). */
   private boolean isArmor(GearService gear, ItemStack stack) {
      if (Items.isEmpty(stack)) {
         return false;
      }

      me.w2n.w2nsmp.gear.GearClass gearClass = gear.classFor(stack.getType());
      return gearClass != null && gearClass.isArmor();
   }

   /** Alasan penolakan pertama dari beberapa kandidat item yang mungkin masuk slot armor. */
   private String firstDeny(GearService gear, Player player, ItemStack... candidates) {
      for (ItemStack candidate : candidates) {
         if (!Items.isEmpty(candidate)) {
            String reason = gear.denyReason(player, candidate);
            if (reason != null) {
               return reason;
            }
         }
      }

      return null;
   }

   private static ItemStack cursorOf(InventoryClickEvent event) {
      try {
         return event.getCursor();
      } catch (Throwable throwable) {
         return null;
      }
   }

   /** Item hotbar pada klik NUMBER_KEY (swap angka 1-9 ke slot armor). */
   private static ItemStack hotbarItemOf(InventoryClickEvent event, Player player) {
      try {
         int button = event.getHotbarButton();
         return button >= 0 ? player.getInventory().getItem(button) : null;
      } catch (Throwable throwable) {
         return null;
      }
   }
}
