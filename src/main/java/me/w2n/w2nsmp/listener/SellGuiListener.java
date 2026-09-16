package me.w2n.w2nsmp.listener;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.gui.SellMenu;
import me.w2n.w2nsmp.gui.SellMenuHolder;
import me.w2n.w2nsmp.sell.SellResult;
import me.w2n.w2nsmp.stats.StatType;
import me.w2n.w2nsmp.utility.Items;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

public final class SellGuiListener implements Listener {
   private final W2NSMP plugin;

   public SellGuiListener(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   @EventHandler(ignoreCancelled = true)
   public void onInventoryClick(InventoryClickEvent event) {
      Inventory top = event.getView().getTopInventory();
      if (top.getHolder() instanceof SellMenuHolder holder) {
         if (!(event.getWhoClicked() instanceof Player player && holder.isOwner(player))) {
            event.setCancelled(true);
         } else if (event.getClick() != ClickType.DOUBLE_CLICK && event.getClick() != ClickType.CREATIVE) {
            int rawSlot = event.getRawSlot();
            boolean clickedTop = event.getClickedInventory() != null && event.getClickedInventory().equals(top);
            if (clickedTop && SellMenu.isReservedSlot(this.plugin, rawSlot)) {
               event.setCancelled(true);
               if (!event.isShiftClick()) {
                  if (rawSlot == SellMenu.slotSell(this.plugin)) {
                     this.plugin.guiSounds().play(player, SellMenu.gui(this.plugin), "click");
                     this.sell(player, top);
                  } else if (rawSlot == SellMenu.slotCancel(this.plugin)) {
                     this.plugin.guiSounds().play(player, SellMenu.gui(this.plugin), "click");
                     this.cancel(player, top);
                  }
               }
            } else if (clickedTop) {
               this.refreshLater(player);
            } else {
               if (event.isShiftClick()) {
                  event.setCancelled(true);
                  Inventory source = event.getClickedInventory();
                  if (source == null) {
                     return;
                  }

                  int sourceSlot = resolveSlot(source, event.getSlot(), rawSlot - top.getSize());
                  if (sourceSlot < 0) {
                     this.plugin.getLogger().warning("Shift-click sell: slot tidak bisa ditentukan (raw=" + rawSlot + ")");
                     return;
                  }

                  this.moveIntoItemArea(player, top, source, sourceSlot, event.getCurrentItem());
                  this.refreshLater(player);
               }
            }
         } else {
            event.setCancelled(true);
         }
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onInventoryDrag(InventoryDragEvent event) {
      if (event.getInventory().getHolder() instanceof SellMenuHolder) {
         int topSize = event.getView().getTopInventory().getSize();

         for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < topSize && SellMenu.isReservedSlot(this.plugin, rawSlot)) {
               event.setCancelled(true);
               return;
            }
         }

         if (event.getWhoClicked() instanceof Player player) {
            this.refreshLater(player);
         }
      }
   }

   @EventHandler
   public void onInventoryClose(InventoryCloseEvent event) {
      if (event.getInventory().getHolder() instanceof SellMenuHolder holder) {
         if (event.getPlayer() instanceof Player player) {
            if (holder.markItemsReturned()) {
               int moved = SellMenu.returnItems(this.plugin, player, event.getInventory());
               if (moved > 0) {
                  this.plugin.messages().send(player, "sell.items-returned", "amount", Integer.toString(moved));
               }
            }
         }
      }
   }

   @EventHandler
   public void onPlayerQuit(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      Inventory top = topInventoryOf(player);
      if (top != null && top.getHolder() instanceof SellMenuHolder holder) {
         if (holder.markItemsReturned()) {
            SellMenu.returnItems(this.plugin, player, top);
         }
      }
   }

   @EventHandler
   public void onPlayerDeath(PlayerDeathEvent event) {
      Player player = event.getEntity();
      Inventory top = topInventoryOf(player);
      if (top != null && top.getHolder() instanceof SellMenuHolder holder) {
         if (holder.markItemsReturned()) {
            for (ItemStack stack : SellMenu.collectItems(this.plugin, top)) {
               event.getDrops().add(stack.clone());
            }

            for (int slot = 0; slot < SellMenu.itemAreaEnd(this.plugin); slot++) {
               top.setItem(slot, null);
            }
         }
      }
   }

   private void sell(Player player, Inventory top) {
      SellMenuHolder holder = top.getHolder() instanceof SellMenuHolder sellHolder ? sellHolder : null;
      if (holder != null && holder.beginProcessing()) {
         try {
            if (!this.plugin.economy().isEnabled()) {
               this.plugin.messages().send(player, "economy.disabled");
               return;
            }

            SellResult result = this.plugin.sell().evaluate(SellMenu.collectItems(this.plugin, top));
            if (result.isEmpty()) {
               this.plugin.guiSounds().play(player, SellMenu.gui(this.plugin), "error");
               this.plugin.messages().send(player, "sell.no-items");
               if (!result.unsellableMaterials().isEmpty()) {
                  this.plugin.messages().send(player, "sell.no-price", "items", Items.joinNames(result.unsellableMaterials()));
               }

               return;
            }

            long maxTotal = this.plugin.config().sellMaxTotal();
            if (maxTotal > 0L && result.total() > maxTotal) {
               this.plugin.messages().send(player, "sell.over-limit", "limit", this.plugin.economy().format(maxTotal));
               return;
            }

            Map<Integer, ItemStack> removed = new LinkedHashMap<>();

            for (int slot = 0; slot < SellMenu.itemAreaEnd(this.plugin); slot++) {
               ItemStack stack = top.getItem(slot);
               if (this.plugin.sell().isSellable(stack)) {
                  removed.put(slot, stack.clone());
                  top.setItem(slot, null);
               }
            }

            if (this.plugin.sell().payout(player, result.total())) {
               this.plugin.sell().recordSale(new ArrayList<>(removed.values()));
               if (this.plugin.stats() != null) {
                  this.plugin.stats().add(player, StatType.ITEMS_SOLD, result.itemCount());
                  this.plugin.stats().add(player, StatType.MONEY_EARNED, result.total());
               }

               String total = this.plugin.economy().format(result.total());
               this.plugin
                  .messages()
                  .send(player, "sell.success", "total", total, "items", Integer.toString(result.itemCount()), "stacks", Integer.toString(removed.size()));
               if (!result.unsellableMaterials().isEmpty()) {
                  this.plugin.messages().send(player, "sell.partially-unsellable", "items", Items.joinNames(result.unsellableMaterials()));
               }

               this.sendActionBar(player, total, result, removed.size(), this.countAreaItems(top));
               this.plugin.guiSounds().play(player, SellMenu.gui(this.plugin), "success");
               this.plugin.debug("SELL " + player.getName() + " | " + result.itemCount() + " item | " + total + " | " + removed.size() + " stack");
               SellMenu.refresh(this.plugin, top, player);
               return;
            }

            for (Entry<Integer, ItemStack> entry : removed.entrySet()) {
               if (Items.isEmpty(top.getItem(entry.getKey()))) {
                  top.setItem(entry.getKey(), entry.getValue());
               } else {
                  SellMenu.giveOrDrop(player, entry.getValue());
               }
            }

            this.plugin.messages().send(player, "economy.transaction-failed");
            this.plugin
               .getLogger()
               .warning("Sell gagal (payout ditolak) untuk " + player.getName() + " senilai " + result.total() + "; item sudah dikembalikan ke GUI.");
         } finally {
            holder.endProcessing();
         }
      }
   }

   private void sendActionBar(Player player, String total, SellResult result, int stacks, int skipped) {
      if (this.plugin.config().sellActionBar()) {
         String key = skipped > 0 ? "sell.actionbar-partial" : "sell.actionbar";
         player.sendActionBar(
            this.plugin
               .messages()
               .component(
                  key, "total", total, "items", Integer.toString(result.itemCount()), "stacks", Integer.toString(stacks), "skipped", Integer.toString(skipped)
               )
         );
      }
   }

   private int countAreaItems(Inventory top) {
      int total = 0;

      for (int slot = 0; slot < SellMenu.itemAreaEnd(this.plugin); slot++) {
         ItemStack stack = top.getItem(slot);
         if (!Items.isEmpty(stack)) {
            total += stack.getAmount();
         }
      }

      return total;
   }

   private void cancel(Player player, Inventory top) {
      SellMenuHolder holder = top.getHolder() instanceof SellMenuHolder sellHolder ? sellHolder : null;
      if (holder != null && holder.markItemsReturned()) {
         int moved = SellMenu.returnItems(this.plugin, player, top);
         this.plugin.messages().send(player, moved > 0 ? "sell.cancelled" : "sell.cancelled-empty", "amount", Integer.toString(moved));
      }

      Bukkit.getScheduler().runTask(this.plugin, () -> player.closeInventory());
   }

   private void moveIntoItemArea(Player player, Inventory top, Inventory source, int sourceSlot, ItemStack clicked) {
      if (source != null && !Items.isEmpty(clicked)) {
         ItemStack moving = clicked.clone();
         int remaining = moving.getAmount();

         for (int slot = 0; slot < SellMenu.itemAreaEnd(this.plugin) && remaining > 0; slot++) {
            ItemStack existing = top.getItem(slot);
            if (!Items.isEmpty(existing) && existing.getType() == moving.getType()) {
               int space = existing.getMaxStackSize() - existing.getAmount();
               if (space > 0) {
                  int transfer = Math.min(space, remaining);
                  existing.setAmount(existing.getAmount() + transfer);
                  top.setItem(slot, existing);
                  remaining -= transfer;
               }
            }
         }

         for (int slot = 0; slot < SellMenu.itemAreaEnd(this.plugin) && remaining > 0; slot++) {
            if (Items.isEmpty(top.getItem(slot))) {
               int transfer = Math.min(moving.getMaxStackSize(), remaining);
               ItemStack placed = moving.clone();
               placed.setAmount(transfer);
               top.setItem(slot, placed);
               remaining -= transfer;
            }
         }

         int moved = moving.getAmount() - remaining;
         if (moved <= 0) {
            this.plugin.messages().send(player, "sell.no-space");
         } else {
            if (remaining <= 0) {
               source.setItem(sourceSlot, null);
            } else {
               moving.setAmount(remaining);
               source.setItem(sourceSlot, moving);
            }
         }
      }
   }

   private static Inventory topInventoryOf(Player player) {
      InventoryView view = player.getOpenInventory();
      return view == null ? null : view.getTopInventory();
   }

   private static int resolveSlot(Inventory inventory, int candidate, int fallback) {
      if (candidate >= 0 && candidate < inventory.getSize()) {
         return candidate;
      } else {
         return fallback >= 0 && fallback < inventory.getSize() ? fallback : -1;
      }
   }

   private void refreshLater(Player player) {
      Bukkit.getScheduler().runTask(this.plugin, () -> {
         Inventory top = topInventoryOf(player);
         if (top != null && top.getHolder() instanceof SellMenuHolder) {
            SellMenu.refresh(this.plugin, top, player);
         }
      });
   }
}
