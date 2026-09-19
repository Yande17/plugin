package me.w2n.w2nsmp.manager;

import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.listener.AuctionGuiListener;
import me.w2n.w2nsmp.listener.BlockStatsListener;
import me.w2n.w2nsmp.listener.BountyGuiListener;
import me.w2n.w2nsmp.listener.BountyListener;
import me.w2n.w2nsmp.listener.CombatListener;
import me.w2n.w2nsmp.listener.ConfirmGuiListener;
import me.w2n.w2nsmp.listener.FishingListener;
import me.w2n.w2nsmp.listener.GearEquipListener;
import me.w2n.w2nsmp.listener.GearListener;
import me.w2n.w2nsmp.listener.HomeGuiListener;
import me.w2n.w2nsmp.listener.LeaderboardGuiListener;
import me.w2n.w2nsmp.listener.ProfileGuiListener;
import me.w2n.w2nsmp.listener.AutoFishGuiListener;
import me.w2n.w2nsmp.listener.FishGuiListener;
import me.w2n.w2nsmp.listener.MoneyDisplayListener;
import me.w2n.w2nsmp.listener.NightVisionListener;
import me.w2n.w2nsmp.listener.RodGuiListener;
import me.w2n.w2nsmp.listener.ScoreboardListener;
import me.w2n.w2nsmp.listener.SellGuiListener;
import me.w2n.w2nsmp.listener.SettingsGuiListener;
import me.w2n.w2nsmp.listener.StatsListener;
import me.w2n.w2nsmp.listener.TeleportListener;
import me.w2n.w2nsmp.listener.TpaGuiListener;
import me.w2n.w2nsmp.listener.TpaListener;
import me.w2n.w2nsmp.listener.WorthListener;
import org.bukkit.event.Listener;

public final class ListenerManager {
   private final W2NSMP plugin;

   public ListenerManager(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   public void register() {
      this.register(new SellGuiListener(this.plugin));
      this.register(new HomeGuiListener(this.plugin));
      this.register(new ConfirmGuiListener(this.plugin));
      this.register(new TeleportListener(this.plugin));
      this.register(new CombatListener(this.plugin));
      this.register(new AuctionGuiListener(this.plugin));
      this.register(new WorthListener(this.plugin));
      this.register(new StatsListener(this.plugin));
      this.register(new BlockStatsListener(this.plugin));
      this.register(new ScoreboardListener(this.plugin));
      this.register(new LeaderboardGuiListener(this.plugin));
      this.register(new ProfileGuiListener(this.plugin));
      this.register(new SettingsGuiListener(this.plugin));
      this.register(new TpaListener(this.plugin));
      this.register(new TpaGuiListener(this.plugin));
      this.register(new BountyListener(this.plugin));
      this.register(new BountyGuiListener(this.plugin));
      this.register(new GearListener(this.plugin));
      this.register(new GearEquipListener(this.plugin));
      this.register(new RodGuiListener(this.plugin));
      this.register(new FishGuiListener(this.plugin));
      this.register(new AutoFishGuiListener(this.plugin));
      this.register(new NightVisionListener(this.plugin));
      this.register(new MoneyDisplayListener(this.plugin));
      this.registerFishingListener();
      this.registerSkillListeners();
   }

   /**
    * Listener skill didaftarkan oleh {@code SkillService} sendiri, bukan di sini, supaya hanya ada
    * satu sumber kebenaran: watchdog fitur skill bisa memeriksa ke HandlerList server dan memasang
    * ulang listener yang hilang tanpa pernah mendaftarkannya dua kali (yang akan membuat XP
    * terhitung ganda). Pendaftaran juga tidak lagi bergantung pada {@code skills.enabled} - kalau
    * fitur dimatikan lewat config, handler-nya sendiri yang diam, jadi {@code /w2nsmp reload}
    * cukup untuk menyalakan fitur kembali tanpa restart.
    */
   private void registerSkillListeners() {
      if (this.plugin.skills() == null) {
         this.plugin.getLogger().severe("Skill: layanan skill belum siap - listener skill TIDAK didaftarkan.");
         return;
      }

      try {
         String result = this.plugin.skills().registerListeners();
         this.plugin.getLogger().info("Skill: listener " + result + ".");
      } catch (Throwable throwable) {
         this.plugin.getLogger().severe("Skill: pendaftaran listener gagal -> " + throwable);
      }
   }

   /**
    * Listener custom fishing (v1.4.0) memakai {@code PlayerFishEvent} - didaftarkan bersyarat
    * seperti SkillFishingListener: bila kelas event tidak ada di server, hanya fitur ini yang
    * mati, plugin tetap dimuat.
    */
   private void registerFishingListener() {
      try {
         if (this.plugin.skills() != null && this.plugin.skills().probe().fishingEventApi()) {
            this.register(new FishingListener(this.plugin));
         } else {
            this.plugin.getLogger().info("Fishing: PlayerFishEvent tidak tersedia - custom fishing dilewati.");
         }
      } catch (Throwable throwable) {
         this.plugin.getLogger().severe("Fishing: pendaftaran listener gagal -> " + throwable);
      }
   }

   private void register(Listener listener) {
      this.plugin.getServer().getPluginManager().registerEvents(listener, this.plugin);
   }
}
