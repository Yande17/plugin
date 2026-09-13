package me.w2n.w2nsmp.listener;

import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.gui.SkillMenu;
import me.w2n.w2nsmp.gui.SkillMenuHolder;
import me.w2n.w2nsmp.skill.SkillInfo;
import me.w2n.w2nsmp.skill.SkillType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/**
 * Klik di GUI {@code /skill}.
 *
 * <p>Meniru penjagaan {@code SettingsGuiListener}: hanya inventory dengan holder
 * {@link SkillMenuHolder} yang disentuh, semua klik dibatalkan (item tidak pernah bisa diambil),
 * dan hanya klik kiri "bersih" yang memicu aksi - jadi shift-click, drag, swap offhand, dan
 * klik kreatif tidak bisa dipakai untuk menduplikasi item menu.
 */
public final class SkillGuiListener implements Listener {
   private final W2NSMP plugin;

   public SkillGuiListener(W2NSMP plugin) {
      this.plugin = plugin;
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onClick(InventoryClickEvent event) {
      Inventory top = event.getView().getTopInventory();
      if (!(top.getHolder() instanceof SkillMenuHolder holder)) {
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
      } catch (RuntimeException exception) {
         this.plugin.debug("Skill: klik GUI gagal untuk " + player.getName() + " (" + exception + ").");
      } finally {
         holder.pending(null);
         holder.endProcessing();
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onDrag(InventoryDragEvent event) {
      if (event.getView().getTopInventory().getHolder() instanceof SkillMenuHolder) {
         event.setCancelled(true);
      }
   }
}
