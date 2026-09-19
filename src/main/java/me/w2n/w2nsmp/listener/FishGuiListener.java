package me.w2n.w2nsmp.listener;

import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.fishing.FishInventory;
import me.w2n.w2nsmp.gui.FishMenu;
import me.w2n.w2nsmp.gui.FishMenuHolder;
import me.w2n.w2nsmp.gui.RodMenu;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Listener GUI /fish (v1.4.1): hub dan galeri ikan.
 *
 * <p>Semua klik di menu dibatalkan (murni tampilan); hanya klik kiri bersih pemilik yang
 * memicu aksi. Pola guard sama dengan GUI lain (holder + beginProcessing + pending).
 */
public final class FishGuiListener implements Listener {
   private final W2NSMP plugin;

   public FishGuiListener(W2NSMP plugin) {
      this.plugin = plugin;
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onClick(InventoryClickEvent event) {
      Inventory top = event.getView().getTopInventory();
      if (!(top.getHolder() instanceof FishMenuHolder holder)) {
         return;
      }

      event.setCancelled(true);
      if (!(event.getWhoClicked() instanceof Player player) || !holder.isOwner(player)) {
         return;
      }

      if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) {
         return;
      }

      if (event.isShiftClick()
         || event.isRightClick()
         || event.getClick().isKeyboardClick()
         || event.getClick().isCreativeAction()
         || event.getClick() == ClickType.DOUBLE_CLICK
         || event.getClick() == ClickType.SWAP_OFFHAND
         || event.getClick() == ClickType.DROP
         || event.getClick() == ClickType.CONTROL_DROP
         || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
         return;
      }

      String key = FishMenu.keyAt(this.plugin, holder, event.getRawSlot());
      if (key == null || !holder.beginProcessing()) {
         return;
      }

      holder.pending(key);

      try {
         this.plugin.guiSounds().play(player, FishMenu.gui(this.plugin), "click");

         if ("close".equals(key)) {
            player.closeInventory();
            return;
         }

         if ("gallery".equals(key)) {
            // Ukuran hub (27) berbeda dengan galeri (54), jadi buka inventory baru.
            player.closeInventory();
            Bukkit.getScheduler().runTask(this.plugin, () -> {
               if (player.isOnline()) {
                  FishMenu.open(this.plugin, player, FishMenuHolder.MODE_GALLERY, 0);
               }
            });
            return;
         }

         if ("storage".equals(key)) {
            // v1.8.0 (PHASE 4): halaman Fish Storage (54) - buka inventory baru.
            player.closeInventory();
            Bukkit.getScheduler().runTask(this.plugin, () -> {
               if (player.isOnline()) {
                  FishMenu.open(this.plugin, player, FishMenuHolder.MODE_STORAGE, 0);
               }
            });
            return;
         }

         if ("upgrade".equals(key)) {
            this.handleUpgrade(player, holder, top);
            return;
         }

         if (key.startsWith("take:")) {
            this.handleTake(player, holder, top, key);
            return;
         }

         if ("rod".equals(key)) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(this.plugin, () -> {
               if (player.isOnline()) {
                  RodMenu.open(this.plugin, player);
               }
            });
            return;
         }

         if ("back".equals(key)) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(this.plugin, () -> {
               if (player.isOnline()) {
                  FishMenu.open(this.plugin, player, FishMenuHolder.MODE_HUB, 0);
               }
            });
            return;
         }

         if ("prev".equals(key) || "next".equals(key)) {
            int delta = "prev".equals(key) ? -1 : 1;
            holder.page(Math.max(0, holder.page() + delta));
            FishMenu.render(this.plugin, top, player, holder);
         }
      } catch (Throwable throwable) {
         this.plugin.debug("Fishing: gangguan klik GUI /fish (" + throwable + ").");
      } finally {
         holder.pending(null);
         holder.endProcessing();
      }
   }

   /**
    * v1.8.0 (PHASE 4): ambil ikan dari Fish Storage ke inventory normal. Urutan anti-dupe:
    * cek ruang -> take() menghapus dari storage -> addItem. Semua di satu event main-thread;
    * beginProcessing menahan double-click.
    */
   private void handleTake(Player player, FishMenuHolder holder, Inventory top, String key) {
      FishInventory storage = this.plugin.fishInventory();
      if (storage == null || !storage.enabled()) {
         return;
      }

      int slotIndex;
      try {
         slotIndex = Integer.parseInt(key.substring("take:".length()));
      } catch (NumberFormatException invalid) {
         return;
      }

      int count = storage.count(player.getUniqueId());
      int index = FishMenu.indexAt(holder.page(), slotIndex, count);
      if (index < 0) {
         return;
      }

      // Inventory penuh -> tolak TANPA menyentuh storage (ikan tetap aman).
      if (player.getInventory().firstEmpty() < 0) {
         this.plugin.messages().send(player, "fishing.storage-inventory-full");
         this.plugin.guiSounds().play(player, FishMenu.gui(this.plugin), "error");
         return;
      }

      ItemStack taken = storage.take(player.getUniqueId(), index);
      if (taken == null) {
         return;
      }

      java.util.HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(taken);
      if (leftover != null) {
         // Seharusnya tidak terjadi (slot kosong sudah dicek) - jangan pernah buang ikan.
         for (ItemStack rest : leftover.values()) {
            if (rest != null) {
               player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
         }
      }

      this.plugin.guiSounds().play(player, FishMenu.gui(this.plugin), "success");
      FishMenu.render(this.plugin, top, player, holder);
   }

   /**
    * v1.8.0: upgrade kapasitas dengan konfirmasi dua-klik (klik pertama mengubah tombol jadi
    * "confirm" selama beberapa detik, klik kedua mengeksekusi). Validasi saldo di
    * FishInventory.upgrade() memakai economy existing.
    */
   private void handleUpgrade(Player player, FishMenuHolder holder, Inventory top) {
      FishInventory storage = this.plugin.fishInventory();
      if (storage == null || !storage.enabled()) {
         return;
      }

      if (storage.nextCapacity(player.getUniqueId()) < 0) {
         this.plugin.guiSounds().play(player, FishMenu.gui(this.plugin), "error");
         return;
      }

      long now = System.currentTimeMillis();
      if (holder.confirmUntil() <= now) {
         holder.confirmUntil(now + 5000L);
         FishMenu.render(this.plugin, top, player, holder);
         return;
      }

      holder.confirmUntil(0L);
      FishInventory.UpgradeResult result = storage.upgrade(player);
      switch (result) {
         case SUCCESS -> {
            this.plugin.messages().send(player, "fishing.storage-upgraded",
               "capacity", Integer.toString(storage.capacity(player.getUniqueId())));
            this.plugin.guiSounds().play(player, FishMenu.gui(this.plugin), "success");
         }
         case NO_MONEY -> {
            this.plugin.messages().send(player, "fishing.storage-upgrade-no-money");
            this.plugin.guiSounds().play(player, FishMenu.gui(this.plugin), "error");
         }
         default -> this.plugin.guiSounds().play(player, FishMenu.gui(this.plugin), "error");
      }

      FishMenu.render(this.plugin, top, player, holder);
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onDrag(InventoryDragEvent event) {
      if (event.getView().getTopInventory().getHolder() instanceof FishMenuHolder) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onClose(InventoryCloseEvent event) {
      try {
         FishMenu.markClosed(event.getPlayer(), event.getInventory());
      } catch (Throwable throwable) {
         this.plugin.debug("Fishing: gangguan tutup GUI /fish (" + throwable + ").");
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onQuit(PlayerQuitEvent event) {
      try {
         FishMenu.markClosed(event.getPlayer(), null);
      } catch (Throwable throwable) {
         this.plugin.debug("Fishing: gangguan keluar GUI /fish (" + throwable + ").");
      }
   }
}
