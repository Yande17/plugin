package me.w2n.w2nsmp.gui;

import java.util.function.Consumer;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.config.GuiConfig;
import me.w2n.w2nsmp.utility.GuiKit;
import me.w2n.w2nsmp.utility.Items;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public final class ConfirmMenu {
   public static final int SIZE = 27;
   public static final int SLOT_ICON = 13;
   public static final int SLOT_CONFIRM = 11;
   public static final int SLOT_CANCEL = 15;

   private ConfirmMenu() {
   }

   public static GuiConfig gui(W2NSMP plugin) {
      return plugin.guiConfigs().confirm();
   }

   public static int size(W2NSMP plugin) {
      return gui(plugin).size(27);
   }

   public static int slotConfirm(W2NSMP plugin) {
      return clamp(plugin, gui(plugin).slot("buttons.confirm.slot", 11), 11);
   }

   public static int slotCancel(W2NSMP plugin) {
      return clamp(plugin, gui(plugin).slot("buttons.cancel.slot", 15), 15);
   }

   public static int slotIcon(W2NSMP plugin) {
      return clamp(plugin, gui(plugin).slot("buttons.icon.slot", 13), 13);
   }

   private static int clamp(W2NSMP plugin, int slot, int fallback) {
      if (slot >= size(plugin)) {
         plugin.getLogger().warning("gui/confirm.yml: slot " + slot + " di luar ukuran GUI (" + size(plugin) + "); memakai " + fallback + ".");
         return fallback;
      } else {
         return slot;
      }
   }

   public static void open(W2NSMP plugin, Player player, String key, Consumer<Player> onConfirm, Consumer<Player> onCancel, String... placeholders) {
      GuiConfig gui = gui(plugin);
      int size = size(plugin);
      ConfirmMenuHolder holder = new ConfirmMenuHolder(player.getUniqueId(), onConfirm, onCancel);
      Inventory inventory = Bukkit.createInventory(holder, size, plugin.messages().component(key + ".title", placeholders));
      holder.setInventory(inventory);
      GuiKit.shell(
         plugin,
         gui,
         inventory,
         Material.GRAY_STAINED_GLASS_PANE,
         "home.filler-material",
         Material.BLACK_STAINED_GLASS_PANE,
         slot -> slot == slotIcon(plugin) || slot == slotConfirm(plugin) || slot == slotCancel(plugin)
      );
      inventory.setItem(
         slotIcon(plugin),
         Items.create(
            material(plugin, key + ".icon-material", Material.PAPER),
            plugin.messages().raw(key + ".icon-name", placeholders),
            plugin.messages().rawList(key + ".icon-lore", placeholders)
         )
      );
      inventory.setItem(
         slotConfirm(plugin), GuiKit.nav(plugin, gui, "confirm", Material.LIME_CONCRETE, key + ".confirm-name", key + ".confirm-lore", placeholders)
      );
      inventory.setItem(slotCancel(plugin), GuiKit.nav(plugin, gui, "cancel", Material.RED_CONCRETE, key + ".cancel-name", key + ".cancel-lore", placeholders));
      player.openInventory(inventory);
      plugin.guiSounds().play(player, gui, "open");
   }

   private static Material material(W2NSMP plugin, String key, Material fallback) {
      return GuiKit.material(plugin, key, fallback);
   }
}
