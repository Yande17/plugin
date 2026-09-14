package me.w2n.w2nsmp.listener;

import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.gui.SkillMenu;
import me.w2n.w2nsmp.gui.SkillMenuHolder;
import me.w2n.w2nsmp.skill.SkillInfo;
import me.w2n.w2nsmp.skill.SkillService;
import me.w2n.w2nsmp.skill.SkillType;
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
 * Klik di GUI {@code /skill}.
 *
 * <p>Aturan utamanya: <b>item menu tidak boleh pernah bisa diambil</b>. Karena itu pembatalan
 * dilakukan lebih dulu (prioritas {@code LOWEST}, sebelum plugin lain bertindak) dan penandaan
 * "ini menu kami" punya tiga jalur - holder inventory, pendaftaran menu terbuka
 * ({@link SkillMenu#isMenu}), dan pendaftaran pemiliknya. Baru sesudah event dibatalkan, aksi
 * klik dijalankan; seluruh aksi dibungkus {@code try/catch} dan kegagalannya dicatat ke
 * {@link me.w2n.w2nsmp.skill.SkillDiagnostics} sehingga terlihat di konsol dan di
 * {@code /skill check} - tidak ada lagi kegagalan yang terjadi diam-diam.
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
            ours = SkillMenu.isOwnerMenu(event.getWhoClicked());
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

   /** Aksi klik kiri bersih pada ikon skill / info / close. Event sudah dibatalkan sebelum ini. */
   private void handleClick(InventoryClickEvent event, Inventory top) {
      HumanEntity who = event.getWhoClicked();
      if (!(who instanceof Player player) || top == null) {
         return;
      }

      if (!(top.getHolder() instanceof SkillMenuHolder holder) || !holder.isOwner(player)) {
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

      SkillType type = SkillMenu.typeAt(this.plugin, event.getRawSlot());
      if (type == null || !holder.beginProcessing()) {
         return;
      }

      holder.pending(key);

      try {
         this.plugin.guiSounds().play(player, SkillMenu.gui(this.plugin), "click");
         SkillInfo.sendDetail(this.plugin, player, player, type);
         SkillMenu.render(this.plugin, top, player);
      } finally {
         holder.pending(null);
         holder.endProcessing();
      }
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
      } catch (Throwable throwable) {
         this.note(throwable, "gui-keluar");
      }
   }

   /** Menu ditutup: lupakan pendaftaran supaya penjagaan tidak menempel ke inventory lain. */
   @EventHandler(priority = EventPriority.MONITOR)
   public void onClose(InventoryCloseEvent event) {
      try {
         SkillMenu.markClosed(event.getPlayer(), event.getInventory());
      } catch (Throwable throwable) {
         this.note(throwable, "gui-tutup");
      }
   }

   /** Penanda "ini menu skill": holder kami, atau inventory yang memang tercatat sedang terbuka. */
   private static boolean isMenuInventory(Inventory top) {
      if (top == null) {
         return false;
      }

      return top.getHolder() instanceof SkillMenuHolder || SkillMenu.isMenu(top);
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
