package me.w2n.w2nsmp.utility;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class Items {
   private Items() {
   }

   public static ItemStack create(Material material, String name, List<String> loreLines) {
      ItemStack stack = new ItemStack(material);
      ItemMeta meta = stack.getItemMeta();
      if (meta == null) {
         return stack;
      }

      if (name != null && !name.isEmpty()) {
         meta.displayName(Text.itemName(name));
      }

      if (loreLines != null && !loreLines.isEmpty()) {
         List<Component> lore = new ArrayList<>(loreLines.size());

         for (String line : loreLines) {
            lore.add(Text.itemLore(line));
         }

         meta.lore(lore);
      }

      stack.setItemMeta(meta);
      return stack;
   }

   public static void fillEmpty(Inventory inventory, ItemStack filler) {
      for (int slot = 0; slot < inventory.getSize(); slot++) {
         if (isEmpty(inventory.getItem(slot))) {
            inventory.setItem(slot, filler);
         }
      }
   }

   public static boolean isEmpty(ItemStack stack) {
      return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
   }

   public static String name(Material material) {
      return material == null ? "?" : material.name();
   }

   public static String joinNames(List<Material> materials) {
      StringBuilder builder = new StringBuilder();

      for (Material material : materials) {
         if (builder.length() > 0) {
            builder.append(", ");
         }

         builder.append(name(material));
      }

      return builder.toString();
   }
}
