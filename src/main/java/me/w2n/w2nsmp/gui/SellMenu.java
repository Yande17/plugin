package me.w2n.w2nsmp.gui;

import java.util.ArrayList;
import java.util.List;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.config.GuiConfig;
import me.w2n.w2nsmp.economy.DynamicEconomy;
import me.w2n.w2nsmp.utility.GuiKit;
import me.w2n.w2nsmp.utility.Items;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

public final class SellMenu {
   public static final int ROWS = 6;
   public static final int SIZE = 54;
   public static final int ITEM_AREA_START = 0;
   public static final int ITEM_AREA_END = 45;
   public static final int SLOT_TOTAL = 48;
   public static final int SLOT_SELL = 49;
   public static final int SLOT_CANCEL = 51;
   public static final int SLOT_INFO = 45;
   private static final int SLOT_UNUSED = -1;

   private SellMenu() {
   }

   public static GuiConfig gui(W2NSMP plugin) {
      return plugin.guiConfigs().sell();
   }

   public static int size(W2NSMP plugin) {
      return gui(plugin).size(54);
   }

   public static int itemAreaEnd(W2NSMP plugin) {
      return size(plugin) - 9;
   }

   public static int slotTotal(W2NSMP plugin) {
      return slotSafe(plugin, "buttons.total.slot", Math.max(itemAreaEnd(plugin), size(plugin) - 6));
   }

   public static int slotSell(W2NSMP plugin) {
      return slotSafe(plugin, "buttons.sell.slot", Math.max(itemAreaEnd(plugin), size(plugin) - 5));
   }

   public static int slotCancel(W2NSMP plugin) {
      return slotSafe(plugin, "buttons.cancel.slot", Math.max(itemAreaEnd(plugin), size(plugin) - 3));
   }

   public static int slotInfo(W2NSMP plugin) {
      int slot = slotSafe(plugin, "buttons.info.slot", Math.max(itemAreaEnd(plugin), size(plugin) - 9));
      return slot < 0 ? -1 : slot;
   }

   private static int slotSafe(W2NSMP plugin, String path, int fallback) {
      int configured = gui(plugin).slot(path, fallback);
      if (configured >= itemAreaEnd(plugin) && configured < size(plugin)) {
         return configured;
      }

      plugin.getLogger().warning("gui/sell.yml: slot " + path + "=" + configured + " berada di luar baris tombol (baris terakhir); memakai " + fallback + ".");
      return fallback;
   }

   public static boolean isReservedSlot(W2NSMP plugin, int rawSlot) {
      return rawSlot >= itemAreaEnd(plugin) && rawSlot < size(plugin);
   }

   public static boolean isReservedSlot(int rawSlot) {
      return rawSlot >= 45 && rawSlot < 54;
   }

   public static void open(W2NSMP plugin, Player player) {
      InventoryView current = player.getOpenInventory();
      if (current != null && current.getTopInventory().getHolder() instanceof SellMenuHolder previous && previous.markItemsReturned()) {
         returnItems(plugin, player, current.getTopInventory());
      }

      SellMenuHolder holder = new SellMenuHolder(player.getUniqueId());
      Inventory inventory = Bukkit.createInventory(holder, size(plugin), gui(plugin).title("sell.gui-title"));
      holder.setInventory(inventory);
      decorate(plugin, inventory);
      refresh(plugin, inventory);
      player.openInventory(inventory);
      plugin.guiSounds().play(player, gui(plugin), "open");
   }

   public static String trendText(W2NSMP plugin, Material material) {
      String key = switch (material != null && plugin.dynamic() != null ? plugin.dynamic().trend(material) : DynamicEconomy.Trend.STABLE) {
         case FALLING -> "sell.trend-falling";
         case RISING -> "sell.trend-rising";
         default -> "sell.trend-stable";
      };
      return plugin.messages().raw(key);
   }

   public static void refresh(W2NSMP plugin, Inventory inventory) {
      refresh(plugin, inventory, null);
   }

   public static void refresh(W2NSMP plugin, Inventory inventory, Player player) {
      long total = 0L;
      long baseTotal = 0L;
      long dominantValue = 0L;
      Material dominant = null;
      int count = 0;

      for (int slot = 0; slot < itemAreaEnd(plugin); slot++) {
         ItemStack stack = inventory.getItem(slot);
         if (!Items.isEmpty(stack)) {
            if (plugin.config().worthSellGui()) {
               ItemStack decorated = plugin.worth().decorate(stack, player);
               if (decorated != stack) {
                  inventory.setItem(slot, decorated);
                  stack = decorated;
               }
            }

            int unitPrice = plugin.sell().currentUnitPrice(stack);
            int basePrice = plugin.sell().prices().price(stack.getType());
            if (unitPrice > 0 && basePrice > 0) {
               total += (long)unitPrice * stack.getAmount();
               long baseValue = (long)basePrice * stack.getAmount();
               baseTotal += baseValue;
               if (baseValue > dominantValue) {
                  dominantValue = baseValue;
                  dominant = stack.getType();
               }

               count += stack.getAmount();
            }
         }
      }

      List<String> lore = new ArrayList<>(
         plugin.messages()
            .rawList("sell.total-lore", "total", plugin.economy().format(total), "base", plugin.economy().format(baseTotal), "count", Integer.toString(count))
      );
      if (plugin.dynamic() != null && plugin.dynamic().enabled() && plugin.messages().hasList("sell.dynamic-lore")) {
         double ratio = baseTotal <= 0L ? 1.0 : (double)total / baseTotal;
         int multiplierPercent = (int)Math.round(ratio * 100.0);
         String trend = trendText(plugin, dominant);
         lore.addAll(
            plugin.messages()
               .applyList(
                  plugin.messages().rawList("sell.dynamic-lore"),
                  "base",
                  plugin.economy().format(baseTotal),
                  "multiplier",
                  multiplierPercent + "%",
                  "trend",
                  trend
               )
         );
      }

      inventory.setItem(
         slotTotal(plugin),
         Items.create(
            material(plugin, "sell.total-material", Material.PAPER), plugin.messages().raw("sell.total-name", "total", plugin.economy().format(total)), lore
         )
      );
   }

   public static void decorate(W2NSMP plugin, Inventory inventory) {
      GuiConfig gui = gui(plugin);
      int infoSlot = slotInfo(plugin);
      ItemStack filler = GuiKit.filler(plugin, gui, Material.GRAY_STAINED_GLASS_PANE, "sell.filler-material");

      for (int slot = itemAreaEnd(plugin); slot < size(plugin); slot++) {
         if (slot != slotTotal(plugin) && slot != slotSell(plugin) && slot != slotCancel(plugin) && slot != infoSlot) {
            inventory.setItem(slot, gui.fillerEnabled() ? filler : null);
         }
      }

      inventory.setItem(slotSell(plugin), GuiKit.nav(plugin, gui, "sell", Material.LIME_CONCRETE, "sell.sell-button-name", "sell.sell-button-lore"));
      inventory.setItem(slotCancel(plugin), GuiKit.nav(plugin, gui, "cancel", Material.RED_CONCRETE, "sell.cancel-button-name", "sell.cancel-button-lore"));
      if (infoSlot != -1) {
         inventory.setItem(infoSlot, GuiKit.nav(plugin, gui, "info", Material.BOOK, "sell.info-name", "sell.info-lore"));
      }
   }

   public static List<ItemStack> collectItems(W2NSMP plugin, Inventory inventory) {
      List<ItemStack> items = new ArrayList<>();

      for (int slot = 0; slot < itemAreaEnd(plugin); slot++) {
         ItemStack stack = inventory.getItem(slot);
         if (!Items.isEmpty(stack)) {
            items.add(stack);
         }
      }

      return items;
   }

   public static int returnItems(W2NSMP plugin, Player player, Inventory inventory) {
      int moved = 0;

      for (int slot = 0; slot < itemAreaEnd(plugin); slot++) {
         ItemStack stack = inventory.getItem(slot);
         if (!Items.isEmpty(stack)) {
            inventory.setItem(slot, null);
            giveOrDrop(player, stack);
            moved += stack.getAmount();
         }
      }

      return moved;
   }

   public static void giveOrDrop(Player player, ItemStack stack) {
      if (!Items.isEmpty(stack)) {
         for (ItemStack leftover : player.getInventory().addItem(new ItemStack[]{stack}).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
         }
      }
   }

   public static void closeAll(W2NSMP plugin) {
      for (Player player : Bukkit.getOnlinePlayers()) {
         InventoryView view = player.getOpenInventory();
         if (view != null && view.getTopInventory().getHolder() instanceof SellMenuHolder) {
            player.closeInventory();
         }
      }
   }

   private static Material material(W2NSMP plugin, String key, Material fallback) {
      Material material = GuiKit.material(plugin, key, fallback);
      if (material == fallback && !fallback.name().equalsIgnoreCase(plugin.messages().raw(key))) {
         plugin.getLogger()
            .warning("messages.yml: material '" + plugin.messages().raw(key) + "' pada " + key + " tidak valid - memakai " + fallback.name() + ".");
      }

      return material;
   }
}
