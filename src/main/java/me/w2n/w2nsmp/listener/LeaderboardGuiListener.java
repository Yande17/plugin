package me.w2n.w2nsmp.listener;

import java.util.List;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.gui.LeaderboardHolder;
import me.w2n.w2nsmp.gui.LeaderboardMenu;
import me.w2n.w2nsmp.stats.LeaderboardEntry;
import me.w2n.w2nsmp.stats.TopCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public final class LeaderboardGuiListener implements Listener {
   private final W2NSMP plugin;

   public LeaderboardGuiListener(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onDrag(InventoryDragEvent event) {
      if (event.getView().getTopInventory().getHolder() instanceof LeaderboardHolder) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         Inventory top = event.getView().getTopInventory();
         if (top.getHolder() instanceof LeaderboardHolder holder) {
            event.setCancelled(true);
            if (holder.isOwner(player) && holder.beginProcessing()) {
               try {
                  ClickType click = event.getClick();
                  if (event.isShiftClick()
                     || click.isKeyboardClick()
                     || click.isCreativeAction()
                     || click == ClickType.DOUBLE_CLICK
                     || click == ClickType.SWAP_OFFHAND
                     || click == ClickType.DROP
                     || click == ClickType.CONTROL_DROP
                     || click == ClickType.MIDDLE
                     || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                     return;
                  }

                  if (!holder.categories()) {
                     this.handleEntries(player, holder, top, event.getRawSlot(), event.getClickedInventory());
                     return;
                  }

                  this.handleCategories(player, holder, event.getRawSlot(), event.getClickedInventory());
               } finally {
                  holder.endProcessing();
               }
            }
         }
      }
   }

   private void handleCategories(Player player, LeaderboardHolder holder, int slot, Inventory clicked) {
      if (clicked != null && clicked.equals(holder.getInventory())) {
         if (slot == LeaderboardMenu.slotCategoriesClose(this.plugin)) {
            player.closeInventory();
         } else if (slot == LeaderboardMenu.slotBack(this.plugin)) {
            LeaderboardMenu.open(this.plugin, player, holder.category(), holder.page());
         } else {
            TopCategory[] categories = TopCategory.values();
            int index = slot - 0;
            if (index >= 0 && index < categories.length) {
               LeaderboardMenu.open(this.plugin, player, categories[index], 1);
            }
         }
      }
   }

   private void handleEntries(Player player, LeaderboardHolder holder, Inventory top, int slot, Inventory clicked) {
      int size = top.getSize();
      if (clicked != null && clicked.equals(top)) {
         if (slot == LeaderboardMenu.slotClose(this.plugin, size)) {
            player.closeInventory();
         } else if (slot == LeaderboardMenu.slotCategories(this.plugin, size)) {
            LeaderboardMenu.openCategories(this.plugin, player, holder.page());
         } else if (slot == LeaderboardMenu.slotPrev(this.plugin, size)) {
            int pages = this.plugin.stats() == null ? 1 : this.plugin.stats().pages(holder.category(), holder.pageSize());
            int target = holder.page() <= 1 ? pages : holder.page() - 1;
            LeaderboardMenu.open(this.plugin, player, holder.category(), target);
         } else if (slot == LeaderboardMenu.slotNext(this.plugin, size)) {
            int pages = this.plugin.stats() == null ? 1 : this.plugin.stats().pages(holder.category(), holder.pageSize());
            int target = holder.page() >= pages ? 1 : holder.page() + 1;
            LeaderboardMenu.open(this.plugin, player, holder.category(), target);
         } else if (slot >= 0 && slot < holder.pageSize()) {
            List<LeaderboardEntry> entries = this.plugin.stats() == null
               ? List.of()
               : this.plugin.stats().page(holder.category(), holder.page(), holder.pageSize());
            if (slot < entries.size()) {
               LeaderboardEntry entry = entries.get(slot);
               this.plugin
                  .messages()
                  .send(
                     player,
                     "top.entry-click",
                     "player",
                     entry.name(),
                     "category",
                     LeaderboardMenu.categoryLabel(this.plugin, holder.category()),
                     "value",
                     LeaderboardMenu.displayValue(this.plugin, holder.category(), entry.value()),
                     "rank",
                     Integer.toString(entry.rank()),
                     "total",
                     Integer.toString(this.plugin.stats() == null ? 0 : this.plugin.stats().rankedCount(holder.category()))
                  );
            }
         }
      }
   }
}
