package me.w2n.w2nsmp.listener;

import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.stats.StatType;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public final class BlockStatsListener implements Listener {
   private final W2NSMP plugin;

   public BlockStatsListener(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onBreak(BlockBreakEvent event) {
      if (this.plugin.config().statisticsTrackBlocks()) {
         Player player = event.getPlayer();
         if (player.getGameMode() != GameMode.CREATIVE && this.plugin.stats() != null) {
            this.plugin.stats().add(player, StatType.BLOCKS_BROKEN, 1L);
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onPlace(BlockPlaceEvent event) {
      if (this.plugin.config().statisticsTrackBlocks()) {
         Player player = event.getPlayer();
         if (player.getGameMode() != GameMode.CREATIVE && this.plugin.stats() != null) {
            this.plugin.stats().add(player, StatType.BLOCKS_PLACED, 1L);
         }
      }
   }
}
