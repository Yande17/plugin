package me.w2n.w2nsmp.utility;

import java.util.List;
import java.util.UUID;
import java.util.function.IntPredicate;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.config.GuiConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public final class GuiKit {
   public static final String NAV_BACK = "back";
   public static final String NAV_CLOSE = "close";
   public static final String NAV_PREV = "prev";
   public static final String NAV_NEXT = "next";
   public static final String NAV_CONFIRM = "confirm";
   public static final String NAV_CANCEL = "cancel";

   private GuiKit() {
   }

   public static ItemStack item(Material material, String name, List<String> lore) {
      return material == null ? null : Items.create(material, name, lore == null ? List.of() : lore);
   }

   public static Material material(W2NSMP plugin, String messageKey, Material fallback) {
      String name = plugin.messages().raw(messageKey);
      if (name != null && !name.isBlank()) {
         Material material = Material.matchMaterial(name.trim());
         return material == null ? fallback : material;
      } else {
         return fallback;
      }
   }

   public static ItemStack head(ItemStack item, UUID uniqueId) {
      if (item != null && item.getType() == Material.PLAYER_HEAD && uniqueId != null) {
         if (item.getItemMeta() instanceof SkullMeta meta) {
            Player online = Bukkit.getPlayer(uniqueId);
            OfflinePlayer owner = (OfflinePlayer)(online != null ? online : Bukkit.getOfflinePlayer(uniqueId));
            meta.setOwningPlayer(owner);
            item.setItemMeta(meta);
            return item;
         } else {
            return item;
         }
      } else {
         return item;
      }
   }

   public static ItemStack filler(W2NSMP plugin, GuiConfig gui, Material fallbackMaterial, String fallbackMessageKey) {
      Material material = gui == null
         ? material(plugin, fallbackMessageKey, fallbackMaterial)
         : gui.fillerMaterial(material(plugin, fallbackMessageKey, fallbackMaterial));
      String name = gui == null ? null : gui.fillerName();
      return Items.create(material, name == null ? " " : name, List.of());
   }

   public static ItemStack border(W2NSMP plugin, GuiConfig gui, Material fallback) {
      Material material = gui == null ? fallback : gui.material("border.material", fallback);
      String name = gui == null ? " " : gui.name("border.name");
      return Items.create(material, name == null ? " " : name, List.of());
   }

   public static void shell(
      W2NSMP plugin, GuiConfig gui, Inventory inventory, Material fillerFallback, String fillerMessageKey, Material borderFallback, int... skip
   ) {
      if (gui != null && gui.fillerEnabled() && inventory != null) {
         ItemStack filler = filler(plugin, gui, fillerFallback, fillerMessageKey);
         ItemStack border = gui.material("border.material", borderFallback) == null ? filler : border(plugin, gui, borderFallback);
         int size = inventory.getSize();
         int rows = size / 9;

         for (int slot = 0; slot < size; slot++) {
            if (!isSkipped(slot, skip)) {
               int row = slot / 9;
               int column = slot % 9;
               boolean edge = row == 0 || row == rows - 1 || column == 0 || column == 8;
               inventory.setItem(slot, edge ? border : filler);
            }
         }
      }
   }

   public static void shell(
      W2NSMP plugin, GuiConfig gui, Inventory inventory, Material fillerFallback, String fillerMessageKey, Material borderFallback, IntPredicate isContent
   ) {
      if (gui != null && gui.fillerEnabled() && inventory != null) {
         ItemStack filler = filler(plugin, gui, fillerFallback, fillerMessageKey);
         ItemStack border = border(plugin, gui, borderFallback);
         int size = inventory.getSize();
         int rows = size / 9;

         for (int slot = 0; slot < size; slot++) {
            if (!isContent.test(slot)) {
               int row = slot / 9;
               int column = slot % 9;
               boolean edge = row == 0 || row == rows - 1 || column == 0 || column == 8;
               inventory.setItem(slot, edge ? border : filler);
            }
         }
      }
   }

   private static boolean isSkipped(int slot, int[] skip) {
      for (int candidate : skip) {
         if (candidate == slot) {
            return true;
         }
      }

      return false;
   }

   public static ItemStack nav(W2NSMP plugin, GuiConfig gui, String key, Material fallbackMaterial, String messageKey, String loreKey, String... placeholders) {
      Material legacyMaterial = gui == null ? null : gui.material("buttons." + key + ".material", null);
      Material material = gui == null
         ? material(plugin, messageKey + "-material", fallbackMaterial)
         : gui.material(
            "navigation." + key + ".material",
            legacyMaterial != null
               ? legacyMaterial
               : material(plugin, messageKey + "-material", gui.material("buttons." + key + ".material", fallbackMaterial))
         );
      String name = gui == null ? null : gui.name("navigation." + key + ".name", "buttons." + key + ".name");
      if (name == null) {
         name = plugin.messages().raw(messageKey, placeholders);
      }

      List<String> lore = gui == null ? List.of() : gui.lore("navigation." + key + ".lore", "buttons." + key + ".lore");
      if (lore.isEmpty() && loreKey != null) {
         lore = plugin.messages().rawList(loreKey, placeholders);
         if (lore.isEmpty() && plugin.messages().has(loreKey)) {
            lore = List.of(plugin.messages().raw(loreKey, placeholders));
         }
      }

      return Items.create(material, name, lore);
   }

   public static Material navMaterial(W2NSMP plugin, GuiConfig gui, String key, String messageKey, Material fallback) {
      if (gui != null) {
         Material legacy = gui.material("buttons." + key + ".material", null);
         return legacy != null ? legacy : gui.material("navigation." + key + ".material", material(plugin, messageKey + "-material", fallback));
      } else {
         return material(plugin, messageKey + "-material", fallback);
      }
   }

   public static String state(W2NSMP plugin, boolean enabled) {
      return plugin.messages().raw(enabled ? "gui.common.enabled" : "gui.common.disabled");
   }

   public static String optionName(W2NSMP plugin, String text, String state) {
      return plugin.messages().raw("gui.common.option", "name", text, "state", state);
   }
}
