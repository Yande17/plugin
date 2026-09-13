package me.w2n.w2nsmp.manager;

import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.listener.AuctionGuiListener;
import me.w2n.w2nsmp.listener.BlockStatsListener;
import me.w2n.w2nsmp.listener.BountyGuiListener;
import me.w2n.w2nsmp.listener.BountyListener;
import me.w2n.w2nsmp.listener.CombatListener;
import me.w2n.w2nsmp.listener.ConfirmGuiListener;
import me.w2n.w2nsmp.listener.HomeGuiListener;
import me.w2n.w2nsmp.listener.LeaderboardGuiListener;
import me.w2n.w2nsmp.listener.ProfileGuiListener;
import me.w2n.w2nsmp.listener.ScoreboardListener;
import me.w2n.w2nsmp.listener.SellGuiListener;
import me.w2n.w2nsmp.listener.SettingsGuiListener;
import me.w2n.w2nsmp.listener.SkillFishingListener;
import me.w2n.w2nsmp.listener.SkillGuiListener;
import me.w2n.w2nsmp.listener.SkillListener;
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
      this.register(new SkillGuiListener(this.plugin));
      if (this.plugin.skills() != null && this.plugin.skills().enabled()) {
         this.register(new SkillListener(this.plugin));
         // PlayerFishEvent adalah satu-satunya event yang tidak dipakai fitur lama; listenernya
         // hanya didaftarkan bila kelas event itu benar-benar ada di server ini.
         if (this.plugin.skills().probe().fishingEventApi()) {
            this.register(new SkillFishingListener(this.plugin));
         } else {
            this.plugin.getLogger().info("Skill: PlayerFishEvent tidak ada di server ini - XP memancing dilewati (fitur lain tetap jalan).");
         }
      }
   }

   private void register(Listener listener) {
      this.plugin.getServer().getPluginManager().registerEvents(listener, this.plugin);
   }
}
