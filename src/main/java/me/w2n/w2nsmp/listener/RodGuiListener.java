package me.w2n.w2nsmp.listener;

import java.util.List;
import java.util.Map;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.fishing.FishingItem;
import me.w2n.w2nsmp.fishing.FishingService;
import me.w2n.w2nsmp.gui.RodMenu;
import me.w2n.w2nsmp.gui.RodMenuHolder;
import me.w2n.w2nsmp.utility.Items;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Listener menu /rod (v1.4.0).
 *
 * <p>Aturan anti-dupe: SEMUA klik di menu dibatalkan (GUI murni tampilan); operasi
 * pasang/copot/upgrade membaca rod di tangan utama saat klik, mengubah PDC, lalu menulis
 * balik. Pasang attachment = klik item attachment di INVENTORY SENDIRI saat menu terbuka
 * (satu item dikurangi, id ditambahkan ke rod); copot = klik kartu attachment di menu
 * (id dihapus, item dikembalikan). Tidak ada item yang pernah disimpan di GUI.
 */
public final class RodGuiListener implements Listener {
   private final W2NSMP plugin;

   public RodGuiListener(W2NSMP plugin) {
      this.plugin = plugin;
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onClick(InventoryClickEvent event) {
      Inventory top;
      try {
         top = event.getView().getTopInventory();
      } catch (Throwable throwable) {
         return;
      }

      if (!RodMenu.isMenu(top)) {
         return;
      }

      // Batalkan dulu (termasuk shift-click, hotbar swap, double-click) - GUI murni tampilan.
      event.setCancelled(true);

      if (!(event.getWhoClicked() instanceof Player player)) {
         return;
      }

      RodMenuHolder holder = top.getHolder() instanceof RodMenuHolder h ? h : null;
      if (holder == null || !holder.isOwner(player)) {
         return;
      }

      if (!holder.beginProcessing()) {
         return;
      }

      try {
         int raw = event.getRawSlot();
         if (raw >= top.getSize()) {
            // Klik di inventory pemain sendiri: coba pasang attachment.
            this.tryInstall(event, player, top);
            return;
         }

         this.handleTopClick(player, top, raw);
      } catch (Throwable throwable) {
         this.plugin.debug("Rod: gangguan di klik menu (" + throwable + ").");
      } finally {
         holder.endProcessing();
      }
   }

   private void handleTopClick(Player player, Inventory top, int raw) {
      FishingService fishing = this.plugin.fishing();
      if (fishing == null) {
         return;
      }

      if (raw == RodMenu.closeSlot(this.plugin)) {
         this.plugin.guiSounds().play(player, RodMenu.gui(this.plugin), "click");
         player.closeInventory();
         return;
      }

      if (raw == RodMenu.upgradeSlot(this.plugin)) {
         this.tryUpgrade(player, top, fishing);
         return;
      }

      int start = RodMenu.attachmentSlot(this.plugin);
      if (raw >= start && raw < start + fishing.attachmentSlots()) {
         this.tryRemove(player, top, fishing, raw - start);
      }
   }

   // ------------------------------------------------------------------ //
   // Pasang attachment (klik item di inventory sendiri)
   // ------------------------------------------------------------------ //

   private void tryInstall(InventoryClickEvent event, Player player, Inventory top) {
      FishingService fishing = this.plugin.fishing();
      if (fishing == null) {
         return;
      }

      ItemStack clicked = event.getCurrentItem();
      String itemId = Items.isEmpty(clicked) ? null : fishing.itemId(clicked);
      FishingItem definition = itemId == null ? null : fishing.item(itemId);
      if (definition == null || !definition.isAttachment()) {
         return;
      }

      ItemStack rod = player.getInventory().getItemInMainHand();
      if (!fishing.isRod(rod)) {
         this.plugin.messages().send(player, "fishing.hold-rod");
         this.plugin.guiSounds().play(player, RodMenu.gui(this.plugin), "error");
         return;
      }

      List<String> attached = fishing.attachments(rod);
      if (attached.contains(definition.id())) {
         this.plugin.messages().send(player, "fishing.attachment-duplicate", "name", definition.itemName());
         this.plugin.guiSounds().play(player, RodMenu.gui(this.plugin), "error");
         return;
      }

      if (attached.size() >= fishing.attachmentSlots()) {
         this.plugin.messages().send(player, "fishing.attachment-full",
            "slots", Integer.toString(fishing.attachmentSlots()));
         this.plugin.guiSounds().play(player, RodMenu.gui(this.plugin), "error");
         return;
      }

      // Urutan anti-dupe: kurangi item DULU, baru tulis attachment ke rod. Keduanya di
      // main thread dalam satu event, jadi tidak ada jendela race.
      if (clicked.getAmount() <= 1) {
         event.setCurrentItem(null);
      } else {
         clicked.setAmount(clicked.getAmount() - 1);
         event.setCurrentItem(clicked);
      }

      attached.add(definition.id());
      fishing.writeAttachments(rod, attached);
      fishing.refreshRodLore(rod);
      player.getInventory().setItemInMainHand(rod);

      this.plugin.messages().send(player, "fishing.attachment-installed", "name", definition.itemName());
      this.plugin.guiSounds().play(player, RodMenu.gui(this.plugin), "success");
      RodMenu.render(this.plugin, top, player);
   }

   // ------------------------------------------------------------------ //
   // Copot attachment (klik kartu attachment di menu)
   // ------------------------------------------------------------------ //

   private void tryRemove(Player player, Inventory top, FishingService fishing, int index) {
      ItemStack rod = player.getInventory().getItemInMainHand();
      if (!fishing.isRod(rod)) {
         this.plugin.messages().send(player, "fishing.hold-rod");
         this.plugin.guiSounds().play(player, RodMenu.gui(this.plugin), "error");
         return;
      }

      List<String> attached = fishing.attachments(rod);
      if (index < 0 || index >= attached.size()) {
         return;
      }

      FishingItem definition = fishing.item(attached.get(index));
      if (definition == null) {
         return;
      }

      // Urutan anti-dupe: hapus id dari rod DULU, baru berikan itemnya. Bila inventory
      // penuh, item dijatuhkan di kaki pemain - tidak pernah hilang.
      attached.remove(index);
      fishing.writeAttachments(rod, attached);
      fishing.refreshRodLore(rod);
      player.getInventory().setItemInMainHand(rod);

      ItemStack item = fishing.createItem(definition, 1);
      Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
      if (leftover != null) {
         for (ItemStack rest : leftover.values()) {
            if (rest != null) {
               player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
         }
      }

      this.plugin.messages().send(player, "fishing.attachment-removed", "name", definition.itemName());
      this.plugin.guiSounds().play(player, RodMenu.gui(this.plugin), "success");
      RodMenu.render(this.plugin, top, player);
   }

   // ------------------------------------------------------------------ //
   // Upgrade rod
   // ------------------------------------------------------------------ //

   private void tryUpgrade(Player player, Inventory top, FishingService fishing) {
      ItemStack rod = player.getInventory().getItemInMainHand();
      if (!fishing.isRod(rod)) {
         this.plugin.messages().send(player, "fishing.hold-rod");
         this.plugin.guiSounds().play(player, RodMenu.gui(this.plugin), "error");
         return;
      }

      int level = fishing.rodLevel(rod);
      if (level >= fishing.rodMaxLevel()) {
         this.plugin.messages().send(player, "fishing.upgrade-already-max");
         this.plugin.guiSounds().play(player, RodMenu.gui(this.plugin), "error");
         return;
      }

      int target = level + 1;
      double needed = fishing.xpNeeded(target);
      if (fishing.rodXp(rod) < needed) {
         this.plugin.messages().send(player, "fishing.upgrade-need-xp",
            "xp", Long.toString((long)fishing.rodXp(rod)), "needed", Long.toString((long)needed));
         this.plugin.guiSounds().play(player, RodMenu.gui(this.plugin), "error");
         return;
      }

      Map<String, Integer> cost = fishing.upgradeCost(target);
      for (Map.Entry<String, Integer> entry : cost.entrySet()) {
         if (RodMenu.countItems(this.plugin, player, entry.getKey()) < entry.getValue()) {
            FishingItem item = fishing.item(entry.getKey());
            this.plugin.messages().send(player, "fishing.upgrade-need-items",
               "amount", Integer.toString(entry.getValue()),
               "item", item == null ? entry.getKey() : item.itemName());
            this.plugin.guiSounds().play(player, RodMenu.gui(this.plugin), "error");
            return;
         }
      }

      // Semua syarat terpenuhi: konsumsi bahan lalu naikkan level (main thread, satu event).
      for (Map.Entry<String, Integer> entry : cost.entrySet()) {
         this.consume(player, fishing, entry.getKey(), entry.getValue());
      }

      this.writeLevel(fishing, rod, target);
      fishing.refreshRodLore(rod);
      player.getInventory().setItemInMainHand(rod);

      this.plugin.messages().send(player, "fishing.upgrade-success", "level", Integer.toString(target));
      this.plugin.guiSounds().play(player, RodMenu.gui(this.plugin), "success");
      RodMenu.render(this.plugin, top, player);
   }

   private void writeLevel(FishingService fishing, ItemStack rod, int level) {
      try {
         org.bukkit.inventory.meta.ItemMeta meta = rod.getItemMeta();
         if (meta != null) {
            me.w2n.w2nsmp.utility.ItemTags.setInt(meta, fishing.keys().rodLevel, level);
            me.w2n.w2nsmp.utility.ItemTags.setDouble(meta, fishing.keys().rodXp, 0.0D);
            rod.setItemMeta(meta);
         }
      } catch (Throwable throwable) {
         this.plugin.debug("Rod: gagal menulis level (" + throwable + ").");
      }
   }

   /** Kurangi item pancing custom ber-id tertentu sebanyak {@code amount} dari inventory. */
   private void consume(Player player, FishingService fishing, String itemId, int amount) {
      int remaining = amount;

      try {
         int size = player.getInventory().getSize();

         for (int slot = 0; slot < size && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (Items.isEmpty(stack) || !itemId.equalsIgnoreCase(fishing.itemId(stack))) {
               continue;
            }

            int take = Math.min(remaining, stack.getAmount());
            remaining -= take;
            if (take >= stack.getAmount()) {
               player.getInventory().setItem(slot, null);
            } else {
               stack.setAmount(stack.getAmount() - take);
               player.getInventory().setItem(slot, stack);
            }
         }
      } catch (Throwable throwable) {
         this.plugin.debug("Rod: gagal mengonsumsi bahan upgrade (" + throwable + ").");
      }
   }

   // ------------------------------------------------------------------ //

   @EventHandler(priority = EventPriority.LOWEST)
   public void onDrag(InventoryDragEvent event) {
      try {
         if (RodMenu.isMenu(event.getView().getTopInventory())) {
            event.setCancelled(true);
         }
      } catch (Throwable ignored) {
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onClose(InventoryCloseEvent event) {
      try {
         RodMenu.markClosed(event.getPlayer(), event.getInventory());
      } catch (Throwable ignored) {
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onQuit(PlayerQuitEvent event) {
      try {
         RodMenu.markClosed(event.getPlayer(), null);
      } catch (Throwable ignored) {
      }
   }
}
