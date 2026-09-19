package me.w2n.w2nsmp.listener;

import me.w2n.w2nsmp.W2NSMP;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

/**
 * Siklus hidup display uang di atas kepala (v1.6.1, PHASE 2).
 *
 * <p>Passenger TextDisplay dilepas otomatis oleh server pada teleport/pindah dunia/mati,
 * jadi listener ini memasang ulang display 1 tick setelah kejadian tersebut. Sneak
 * menyembunyikan display (meniru nametag vanilla), spectator juga. Semua handler MONITOR
 * dan dibungkus try/catch - tidak pernah mengganggu fitur lain.
 */
public final class MoneyDisplayListener implements Listener {
   private final W2NSMP plugin;

   public MoneyDisplayListener(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onJoin(PlayerJoinEvent event) {
      // 1) pasang display milik pemain baru; 2) terapkan preferensi VIEWER-nya sendiri ke
      // semua display yang sudah ada (setting OFF harus langsung berlaku sejak join).
      this.applyLater(event.getPlayer());
      Bukkit.getScheduler().runTask(this.plugin, () -> {
         try {
            Player player = event.getPlayer();
            if (player.isOnline() && this.plugin.nametag() != null) {
               this.plugin.nametag().updateViewer(player);
            }
         } catch (Throwable ignored) {
         }
      });
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onQuit(PlayerQuitEvent event) {
      try {
         if (this.plugin.nametag() != null) {
            this.plugin.nametag().remove(event.getPlayer());
         }
      } catch (Throwable ignored) {
      }
   }

   /** Mati: cabut display segera supaya tidak melayang di lokasi kematian. */
   @EventHandler(priority = EventPriority.MONITOR)
   public void onDeath(PlayerDeathEvent event) {
      try {
         if (this.plugin.nametag() != null && event.getEntity() instanceof Player player) {
            this.plugin.nametag().remove(player);
         }
      } catch (Throwable ignored) {
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onRespawn(PlayerRespawnEvent event) {
      this.applyLater(event.getPlayer());
   }

   /** Teleport melepas passenger - pasang ulang setelah teleport selesai. */
   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onTeleport(PlayerTeleportEvent event) {
      this.applyLater(event.getPlayer());
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onWorldChange(PlayerChangedWorldEvent event) {
      this.applyLater(event.getPlayer());
   }

   /** Sneak menyembunyikan display; berdiri lagi menampilkannya (meniru nametag vanilla). */
   @EventHandler(priority = EventPriority.MONITOR)
   public void onSneak(PlayerToggleSneakEvent event) {
      try {
         if (this.plugin.nametag() == null) {
            return;
         }

         if (event.isSneaking()) {
            this.plugin.nametag().remove(event.getPlayer());
         } else {
            this.applyLater(event.getPlayer());
         }
      } catch (Throwable ignored) {
      }
   }

   /** Spectator tidak boleh membocorkan posisinya lewat teks uang. */
   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onGameModeChange(PlayerGameModeChangeEvent event) {
      this.applyLater(event.getPlayer());
   }

   /** apply() 1 tick kemudian: menunggu server menyelesaikan perpindahan/status pemain. */
   private void applyLater(Player player) {
      try {
         if (this.plugin.nametag() == null || !this.plugin.nametag().enabled()) {
            return;
         }

         Bukkit.getScheduler().runTask(this.plugin, () -> {
            try {
               if (player.isOnline() && this.plugin.nametag() != null) {
                  this.plugin.nametag().apply(player);
               }
            } catch (Throwable ignored) {
            }
         });
      } catch (Throwable ignored) {
      }
   }
}
