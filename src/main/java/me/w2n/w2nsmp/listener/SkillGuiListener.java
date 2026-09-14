package me.w2n.w2nsmp.listener;

import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.gui.SkillMenu;
import me.w2n.w2nsmp.gui.SkillMenuHolder;
import me.w2n.w2nsmp.gui.SkillProgressMenu;
import me.w2n.w2nsmp.gui.SkillProgressMenuHolder;
import me.w2n.w2nsmp.skill.SkillBuff;
import me.w2n.w2nsmp.skill.SkillInfo;
import me.w2n.w2nsmp.skill.SkillService;
import me.w2n.w2nsmp.skill.SkillSettings;
import me.w2n.w2nsmp.skill.SkillType;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
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
import org.bukkit.inventory.InventoryView;

/**
 * Klik di GUI {@code /skill} dan di menu <b>progres skill</b> (v1.3.0).
 *
 * <p>Aturan utamanya: <b>item menu tidak boleh pernah bisa diambil</b>. Karena itu pembatalan
 * dilakukan lebih dulu (prioritas {@code LOWEST}, sebelum plugin lain bertindak) dan penandaan
 * "ini menu kami" punya tiga jalur - holder inventory, pendaftaran menu terbuka
 * ({@link SkillMenu#isMenu} / {@link SkillProgressMenu#isMenu}), dan pendaftaran pemiliknya. Baru
 * sesudah event dibatalkan, aksi klik dijalankan; seluruh aksi dibungkus {@code try/catch} dan
 * kegagalannya dicatat ke {@link me.w2n.w2nsmp.skill.SkillDiagnostics} sehingga terlihat di konsol
 * dan di {@code /skill check} - tidak ada kegagalan yang terjadi diam-diam.
 *
 * <p>Pindah antar menu (ikon skill -&gt; menu progres -&gt; kembali) dijadwalkan satu tick kemudian
 * seperti menu W2NSMP lain ({@code HomeMenu}, {@code AuctionMenu}), supaya inventory tidak dibuka
 * di tengah event klik.
 *
 * <p>Sisanya meniru penjagaan {@code SettingsGuiListener}: hanya klik kiri "bersih" yang memicu
 * aksi, jadi shift-click, drag, swap offhand, klik kreatif, dan double-click tidak bisa dipakai
 * untuk menduplikasi item menu.
 */
public final class SkillGuiListener implements Listener {
   private final W2NSMP plugin;

   public SkillGuiListener(W2NSMP plugin) {
      this.plugin = plugin;
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onClick(InventoryClickEvent event) {
      Inventory top = null;
      boolean ours = false;

      try {
         InventoryView view = event.getView();
         top = view == null ? null : view.getTopInventory();
         ours = isMenuInventory(top);
      } catch (Throwable throwable) {
         // Jalur cadangan: getView()/getHolder() bermasalah di server ini -> pakai pendaftaran
         // menu terbuka. Pembatalan tetap jalan, jadi item menu tidak bisa diambil.
         this.note(throwable, "gui-klik.getView");

         try {
            ours = SkillMenu.isOwnerMenu(event.getWhoClicked()) || SkillProgressMenu.isOwnerMenu(event.getWhoClicked());
         } catch (Throwable ignored) {
            return;
         }
      }

      if (!ours) {
         return;
      }

      try {
         event.setCancelled(true);
      } catch (Throwable throwable) {
         this.note(throwable, "gui-klik.setCancelled");
         return;
      }

      try {
         this.handleClick(event, top);
      } catch (Throwable throwable) {
         this.note(throwable, "gui-klik.aksi");
      }
   }

   /** Aksi klik kiri bersih. Event sudah dibatalkan sebelum metode ini berjalan. */
   private void handleClick(InventoryClickEvent event, Inventory top) {
      HumanEntity who = event.getWhoClicked();
      if (!(who instanceof Player player) || top == null) {
         return;
      }

      if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) {
         return;
      }

      if (!isCleanLeftClick(event)) {
         return;
      }

      Object holder = holderOf(top);
      if (holder instanceof SkillProgressMenuHolder progress) {
         if (progress.isOwner(player)) {
            this.handleProgressClick(event, player, progress);
         }

         return;
      }

      if (holder instanceof SkillMenuHolder menu && menu.isOwner(player)) {
         this.handleMenuClick(event, player, top, menu);
      }
   }

   /** Menu utama: ikon skill membuka menu progres, tombol top mencetak peringkat, close keluar. */
   private void handleMenuClick(InventoryClickEvent event, Player player, Inventory top, SkillMenuHolder holder) {
      String key = SkillMenu.keyAt(this.plugin, event.getRawSlot());
      if (key == null) {
         return;
      }

      if ("close".equals(key)) {
         this.plugin.guiSounds().play(player, SkillMenu.gui(this.plugin), "click");
         player.closeInventory();
         return;
      }

      if ("info".equals(key)) {
         // Item ringkasan: hanya bunyi klik. Angka-angkanya sudah tertulis di lore item.
         this.plugin.guiSounds().play(player, SkillMenu.gui(this.plugin), "click");
         return;
      }

      if ("top".equals(key)) {
         if (!holder.beginProcessing()) {
            return;
         }

         holder.pending(key);

         try {
            this.plugin.guiSounds().play(player, SkillMenu.gui(this.plugin), "click");
            player.closeInventory();
            Bukkit.getScheduler().runTask(this.plugin, () -> {
               if (player.isOnline()) {
                  SkillInfo.sendTop(this.plugin, player, null);
               }
            });
         } finally {
            holder.pending(null);
            holder.endProcessing();
         }

         return;
      }

      SkillType type = SkillMenu.typeAt(this.plugin, event.getRawSlot());
      if (type == null || !holder.beginProcessing()) {
         return;
      }

      holder.pending(key);

      try {
         this.plugin.guiSounds().play(player, SkillMenu.gui(this.plugin), "click");
         this.openProgressLater(player, type);
      } finally {
         holder.pending(null);
         holder.endProcessing();
      }
   }

   /** Menu progres: kembali, detail chat, close, atau penjelasan satu buff di chat. */
   private void handleProgressClick(InventoryClickEvent event, Player player, SkillProgressMenuHolder holder) {
      String key = SkillProgressMenu.keyAt(this.plugin, holder.type(), event.getRawSlot());
      if (key == null) {
         return;
      }

      if (!holder.beginProcessing()) {
         return;
      }

      holder.pending(key);

      try {
         this.plugin.guiSounds().play(player, SkillProgressMenu.gui(this.plugin), "click");
         SkillType type = holder.type();

         if ("close".equals(key)) {
            player.closeInventory();
            return;
         }

         if ("back".equals(key)) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(this.plugin, () -> {
               if (player.isOnline()) {
                  SkillMenu.open(this.plugin, player);
               }
            });
            return;
         }

         if ("detail".equals(key)) {
            SkillInfo.sendDetail(this.plugin, player, player, type);
            SkillProgressMenu.render(this.plugin, event.getView().getTopInventory(), player, type);
            return;
         }

         int index = SkillProgressMenu.buffIndex(key);
         SkillService service = this.plugin.skills();
         SkillSettings settings = service == null || type == null ? null : service.settings(type);
         SkillBuff buff = settings == null || index < 0 || index >= settings.buffCount() ? null : settings.buffs().get(index);
         if (buff != null) {
            SkillInfo.sendBuffDetail(this.plugin, player, type, buff);
            SkillProgressMenu.render(this.plugin, event.getView().getTopInventory(), player, type);
         }
      } finally {
         holder.pending(null);
         holder.endProcessing();
      }
   }

   private void openProgressLater(Player player, SkillType type) {
      Bukkit.getScheduler().runTask(this.plugin, () -> {
         if (player.isOnline() && type != null) {
            SkillProgressMenu.open(this.plugin, player, type);
         }
      });
   }

   /** Holder menu kami (bila server bisa membacanya); null bila inventory bukan menu skill. */
   private static Object holderOf(Inventory top) {
      try {
         return top.getHolder();
      } catch (Throwable throwable) {
         return null;
      }
   }

   /** Hanya klik kiri bersih yang boleh memicu aksi menu. */
   private static boolean isCleanLeftClick(InventoryClickEvent event) {
      return !event.isShiftClick()
         && !event.isRightClick()
         && !event.getClick().isKeyboardClick()
         && !event.getClick().isCreativeAction()
         && event.getClick() != ClickType.DOUBLE_CLICK
         && event.getClick() != ClickType.SWAP_OFFHAND
         && event.getClick() != ClickType.DROP
         && event.getClick() != ClickType.CONTROL_DROP
         && event.getAction() != InventoryAction.COLLECT_TO_CURSOR;
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onDrag(InventoryDragEvent event) {
      try {
         InventoryView view = event.getView();
         if (isMenuInventory(view == null ? null : view.getTopInventory())) {
            event.setCancelled(true);
         }
      } catch (Throwable throwable) {
         this.note(throwable, "gui-drag");
      }
   }

   /** Pemain keluar: pastikan pendaftaran menu tidak tertinggal. */
   @EventHandler(priority = EventPriority.MONITOR)
   public void onQuit(PlayerQuitEvent event) {
      try {
         SkillMenu.markClosed(event.getPlayer(), null);
         SkillProgressMenu.markClosed(event.getPlayer(), null);
      } catch (Throwable throwable) {
         this.note(throwable, "gui-keluar");
      }
   }

   /** Menu ditutup: lupakan pendaftaran supaya penjagaan tidak menempel ke inventory lain. */
   @EventHandler(priority = EventPriority.MONITOR)
   public void onClose(InventoryCloseEvent event) {
      try {
         SkillMenu.markClosed(event.getPlayer(), event.getInventory());
         SkillProgressMenu.markClosed(event.getPlayer(), event.getInventory());
      } catch (Throwable throwable) {
         this.note(throwable, "gui-tutup");
      }
   }

   /** Penanda "ini menu skill": holder kami, atau inventory yang memang tercatat sedang terbuka. */
   private static boolean isMenuInventory(Inventory top) {
      if (top == null) {
         return false;
      }

      Object holder = holderOf(top);
      return holder instanceof SkillMenuHolder || holder instanceof SkillProgressMenuHolder
         || SkillMenu.isMenu(top) || SkillProgressMenu.isMenu(top);
   }

   private void note(Throwable throwable, String where) {
      SkillService service = this.plugin.skills();
      if (service != null) {
         service.diagnostics().noteError(where, throwable);
      } else {
         this.plugin.getLogger().warning("Skill: gangguan di " + where + " -> " + throwable);
      }
   }
}
