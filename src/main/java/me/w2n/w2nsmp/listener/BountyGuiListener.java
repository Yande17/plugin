package me.w2n.w2nsmp.listener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.bounty.BountyEntry;
import me.w2n.w2nsmp.bounty.BountyService;
import me.w2n.w2nsmp.gui.BountyMenu;
import me.w2n.w2nsmp.gui.BountyMenuHolder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public final class BountyGuiListener implements Listener {
   private final W2NSMP plugin;

   public BountyGuiListener(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onClick(InventoryClickEvent event) {
      Inventory top = event.getView().getTopInventory();
      if (top.getHolder() instanceof BountyMenuHolder holder) {
         event.setCancelled(true);
         if (event.getWhoClicked() instanceof Player player && holder.isOwner(player)) {
            if (event.getClickedInventory() != null && event.getClickedInventory().equals(top)) {
               if (!event.isShiftClick()
                  && !event.isRightClick()
                  && !event.getClick().isKeyboardClick()
                  && !event.getClick().isCreativeAction()
                  && event.getClick() != ClickType.DOUBLE_CLICK
                  && event.getClick() != ClickType.SWAP_OFFHAND
                  && event.getClick() != ClickType.DROP
                  && event.getClick() != ClickType.CONTROL_DROP
                  && event.getAction() != InventoryAction.COLLECT_TO_CURSOR) {
                  int slot = event.getRawSlot();
                  if (slot == BountyMenu.slotClose(this.plugin)) {
                     this.plugin.guiSounds().play(player, BountyMenu.gui(this.plugin), "click");
                     player.closeInventory();
                  } else if (slot == BountyMenu.slotPrev(this.plugin)) {
                     if (holder.mode() != BountyMenuHolder.Mode.AMOUNT && holder.page() > 1) {
                        holder.setPage(holder.page() - 1);
                        this.plugin.guiSounds().play(player, BountyMenu.gui(this.plugin), "click");
                        BountyMenu.refresh(this.plugin, player, top);
                     }
                  } else if (slot == BountyMenu.slotNext(this.plugin)) {
                     if (holder.mode() != BountyMenuHolder.Mode.AMOUNT && holder.page() < BountyMenu.pages(this.plugin, holder)) {
                        holder.setPage(holder.page() + 1);
                        this.plugin.guiSounds().play(player, BountyMenu.gui(this.plugin), "click");
                        BountyMenu.refresh(this.plugin, player, top);
                     }
                  } else if (slot == BountyMenu.slotTargets(this.plugin)) {
                     this.plugin.guiSounds().play(player, BountyMenu.gui(this.plugin), "click");

                     BountyMenuHolder.Mode next = switch (holder.mode()) {
                        case TOP -> BountyMenuHolder.Mode.TARGETS;
                        default -> BountyMenuHolder.Mode.TOP;
                     };
                     holder.setMode(next);
                     holder.setPage(1);
                     BountyMenu.refresh(this.plugin, player, top);
                  } else if (slot == BountyMenu.slotInfo(this.plugin)) {
                     this.plugin.guiSounds().play(player, BountyMenu.gui(this.plugin), "click");
                     this.plugin
                        .messages()
                        .send(
                           player,
                           "bounty.info-own",
                           "count",
                           Integer.toString(this.plugin.bounty().count()),
                           "total",
                           this.plugin.economy() == null
                              ? Long.toString(this.plugin.bounty().total())
                              : this.plugin.economy().format(this.plugin.bounty().total()),
                           "own",
                           this.plugin.bounty().formatted(player.getUniqueId()),
                           "minimum",
                           this.plugin.economy() == null
                              ? Long.toString(this.plugin.config().bountyMinimum())
                              : this.plugin.economy().format(this.plugin.config().bountyMinimum()),
                           "maximum",
                           this.plugin.economy() == null
                              ? Long.toString(this.plugin.config().bountyMaximum())
                              : this.plugin.economy().format(this.plugin.config().bountyMaximum())
                        );
                  } else {
                     int start = BountyMenu.entryStart(this.plugin);
                     int perPage = BountyMenu.pageSize(this.plugin);
                     if (slot >= start && slot < start + perPage) {
                        int index = slot - start;
                        switch (holder.mode()) {
                           case AMOUNT:
                              this.placePreset(player, holder, top, index);
                              break;
                           case TARGETS:
                              this.selectTarget(player, holder, top, index);
                              break;
                           default:
                              this.selectBounty(player, holder, top, index);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onDrag(InventoryDragEvent event) {
      if (event.getView().getTopInventory().getHolder() instanceof BountyMenuHolder) {
         event.setCancelled(true);
      }
   }

   private void selectBounty(Player player, BountyMenuHolder holder, Inventory top, int index) {
      List<BountyEntry> entries = this.plugin.bounty().top();
      int position = (holder.page() - 1) * BountyMenu.pageSize(this.plugin) + index;
      if (position < entries.size()) {
         BountyEntry entry = entries.get(position);
         holder.setSubject(entry.uniqueId(), entry.displayName(entry.uniqueId().toString()));
         holder.setMode(BountyMenuHolder.Mode.AMOUNT);
         this.plugin.guiSounds().play(player, BountyMenu.gui(this.plugin), "click");
         BountyMenu.refresh(this.plugin, player, top);
      }
   }

   private void selectTarget(Player player, BountyMenuHolder holder, Inventory top, int index) {
      List<Player> online = onlinePlayers();
      int position = (holder.page() - 1) * BountyMenu.pageSize(this.plugin) + index;
      if (position < online.size()) {
         Player target = online.get(position);
         holder.setSubject(target.getUniqueId(), target.getName());
         holder.setMode(BountyMenuHolder.Mode.AMOUNT);
         this.plugin.guiSounds().play(player, BountyMenu.gui(this.plugin), "click");
         BountyMenu.refresh(this.plugin, player, top);
      }
   }

   private void placePreset(Player player, BountyMenuHolder holder, Inventory top, int index) {
      List<Long> presets = this.plugin.config().bountyPresets();
      if (index < presets.size() && holder.subject() != null) {
         OfflinePlayer target = Bukkit.getOfflinePlayer(holder.subject());
         long amount = presets.get(index);
         BountyService.PlaceResult result = this.plugin.bounty().place(player, target, amount);
         this.plugin.guiSounds().play(player, BountyMenu.gui(this.plugin), result == BountyService.PlaceResult.OK ? "success" : "error");
         if (result == BountyService.PlaceResult.OK) {
            holder.setMode(BountyMenuHolder.Mode.TOP);
            holder.setPage(1);
            holder.setSubject(null, null);
         }

         BountyMenu.refresh(this.plugin, player, top);
      }
   }

   public static List<Player> onlinePlayers() {
      List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
      players.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
      return players;
   }
}
