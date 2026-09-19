package me.w2n.w2nsmp.player;

import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.skill.SkillApiProbe;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Night Vision pribadi lewat /setting (v1.5.1, PHASE 1).
 *
 * <p>Desain anti-flicker & anti-stack: efek dipasang berdurasi {@link #DURATION_SECONDS}
 * (300 dtk) dan disegarkan oleh SATU task global tiap {@link #REFRESH_TICKS} (60 dtk).
 * Klien Minecraft hanya membuat layar berkedip saat sisa efek &lt; 10 detik - dengan pola
 * ini sisa durasi tidak pernah di bawah 240 detik, jadi tidak ada flicker. Menambahkan
 * ulang efek bertipe sama MENIMPA yang lama (bukan menumpuk), dan pemasangan memakai
 * ambient=true + particles=false sehingga tidak ada partikel mengganggu.
 *
 * <p>Pemasangan efek memakai {@link SkillApiProbe} (reflection yang sudah teruji lintas
 * versi) - tidak ada API baru yang dikarang. Task berhenti sendiri bila tidak ada pemain
 * online yang menyalakan setting (lazy, bukan task abadi).
 *
 * <p>Siklus hidup yang ditangani: toggle ON/OFF, join (setting persist di settings.yml),
 * respawn (efek vanilla hilang saat mati), reload (fitur dimatikan admin -> efek dicabut),
 * disable (efek dicabut dari semua pemain yang memakainya).
 */
public final class NightVisionService {
   /** Kunci efek untuk SkillApiProbe (dicocokkan ke PotionEffectType night_vision). */
   private static final String EFFECT_KEY = "night_vision";
   /** Kunci setting di settings.yml (per UUID). */
   public static final String SETTING_KEY = "night-vision";
   private static final int DURATION_SECONDS = 300;
   private static final long REFRESH_TICKS = 1200L;

   private final W2NSMP plugin;
   private BukkitTask task;
   private boolean enabled = true;

   public NightVisionService(W2NSMP plugin) {
      this.plugin = plugin;
   }

   /** Muat gate config. Bila admin mematikan fitur, efek aktif dicabut dari semua pemain. */
   public void reload() {
      boolean wasEnabled = this.enabled;
      this.enabled = this.plugin.config().nightVisionEnabled();
      if (wasEnabled && !this.enabled) {
         this.removeAllOnline();
         this.stopTask();
      } else if (this.enabled) {
         // Fitur (kembali) aktif: pulihkan efek pemain online yang setting-nya menyala.
         for (Player player : Bukkit.getOnlinePlayers()) {
            try {
               if (this.isOn(player)) {
                  this.apply(player);
               }
            } catch (Throwable ignored) {
            }
         }
      }
   }

   public boolean enabled() {
      return this.enabled;
   }

   /** Setting pemain ini menyala? (persist per UUID di settings.yml). */
   public boolean isOn(Player player) {
      return player != null && this.plugin.settings() != null
         && this.plugin.settings().nightVision(player);
   }

   /**
    * Pasang/segarkan efek untuk pemain (dipanggil saat toggle ON, join, respawn, dan oleh
    * task penyegar). Aman dipanggil berulang: efek lama ditimpa, tidak menumpuk.
    */
   public void apply(Player player) {
      if (!this.enabled || player == null || !player.isOnline()) {
         return;
      }

      SkillApiProbe probe = this.probe();
      if (probe == null || !probe.applyPotion(player, EFFECT_KEY, 0, DURATION_SECONDS)) {
         return;
      }

      this.ensureTask();
   }

   /** Cabut efek (toggle OFF / fitur dimatikan / plugin disable). */
   public void remove(Player player) {
      if (player == null) {
         return;
      }

      SkillApiProbe probe = this.probe();
      if (probe != null) {
         probe.removePotion(player, EFFECT_KEY);
      }
   }

   /** Pemain baru masuk: pulihkan efek bila setting-nya menyala. */
   public void onJoin(Player player) {
      if (this.enabled && this.isOn(player)) {
         this.apply(player);
      }
   }

   /** Setelah respawn efek vanilla kosong: pasang ulang bila setting menyala. */
   public void onRespawn(Player player) {
      this.onJoin(player);
   }

   /** Matikan layanan: cabut efek semua pemain yang memakainya + batalkan task. */
   public void shutdown() {
      this.removeAllOnline();
      this.stopTask();
   }

   // ------------------------------------------------------------------ //
   // Task penyegar (satu untuk semua pemain, lazy)
   // ------------------------------------------------------------------ //

   private void ensureTask() {
      if (this.task == null) {
         this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::refreshAll, REFRESH_TICKS, REFRESH_TICKS);
      }
   }

   private void stopTask() {
      if (this.task != null) {
         try {
            this.task.cancel();
         } catch (Throwable ignored) {
         }

         this.task = null;
      }
   }

   /** Segarkan efek semua pemain aktif; berhenti sendiri bila tidak ada yang memakai. */
   private void refreshAll() {
      if (!this.enabled) {
         this.stopTask();
         return;
      }

      SkillApiProbe probe = this.probe();
      if (probe == null) {
         return;
      }

      boolean any = false;

      for (Player player : Bukkit.getOnlinePlayers()) {
         try {
            if (this.isOn(player)) {
               any = true;
               probe.applyPotion(player, EFFECT_KEY, 0, DURATION_SECONDS);
            }
         } catch (Throwable throwable) {
            this.plugin.debug("NightVision: gangguan penyegaran untuk " + player.getName() + " (" + throwable + ").");
         }
      }

      if (!any) {
         this.stopTask();
      }
   }

   private void removeAllOnline() {
      for (Player player : Bukkit.getOnlinePlayers()) {
         try {
            if (this.isOn(player)) {
               this.remove(player);
            }
         } catch (Throwable ignored) {
         }
      }
   }

   /** Probe potion milik sistem skill (reflection lintas versi); null bila belum siap. */
   private SkillApiProbe probe() {
      return this.plugin.skills() == null ? null : this.plugin.skills().probe();
   }
}
