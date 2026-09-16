package me.w2n.w2nsmp.worth;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class WorthLore {
   private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
   public static final int MAX_LORE_LINES = 4;
   private final NamespacedKey priceKey;
   private final NamespacedKey linesKey;

   public WorthLore(JavaPlugin plugin) {
      super();
      this.priceKey = new NamespacedKey(plugin, "worth-price");
      this.linesKey = new NamespacedKey(plugin, "worth-lines");
   }

   public boolean isMarked(ItemStack stack) {
      return !this.markedLines(stack).isEmpty();
   }

   public long markedPrice(ItemStack stack) {
      ItemMeta meta = stack == null ? null : stack.getItemMeta();
      if (meta == null) {
         return -1L;
      }

      Long price = (Long)meta.getPersistentDataContainer().get(this.priceKey, PersistentDataType.LONG);
      return price == null ? -1L : price;
   }

   public List<String> markedLines(ItemStack stack) {
      ItemMeta meta = stack == null ? null : stack.getItemMeta();
      if (meta == null) {
         return List.of();
      }

      String packed = (String)meta.getPersistentDataContainer().get(this.linesKey, PersistentDataType.STRING);
      return packed != null && !packed.isEmpty() ? List.of(packed.split("\n", -1)) : List.of();
   }

   public ItemStack apply(ItemStack stack, long price, List<Component> lines, boolean atTop) {
      if (stack != null && !stack.getType().isAir() && !lines.isEmpty()) {
         ItemMeta meta = stack.getItemMeta();
         if (meta == null) {
            return stack;
         }

         List<Component> lore = new ArrayList<>(existingLore(meta));
         this.removeMarked(lore, meta);
         if (atTop) {
            lore.addAll(0, lines);
         } else {
            lore.addAll(lines);
         }

         meta.lore(lore);
         PersistentDataContainer container = meta.getPersistentDataContainer();
         container.set(this.priceKey, PersistentDataType.LONG, price);
         container.set(this.linesKey, PersistentDataType.STRING, pack(lines));
         ItemStack result = stack.clone();
         result.setItemMeta(meta);
         return result;
      } else {
         return stack;
      }
   }

   public ItemStack strip(ItemStack stack) {
      if (stack != null && !stack.getType().isAir()) {
         ItemMeta meta = stack.getItemMeta();
         if (meta != null && meta.getPersistentDataContainer().has(this.linesKey, PersistentDataType.STRING)) {
            List<Component> lore = new ArrayList<>(existingLore(meta));
            this.removeMarked(lore, meta);
            meta.lore(lore.isEmpty() ? null : lore);
            ItemStack result = stack.clone();
            result.setItemMeta(meta);
            return result;
         } else {
            return stack;
         }
      } else {
         return stack;
      }
   }

   public boolean matches(ItemStack stack, long price, List<Component> lines) {
      return this.isMarked(stack) && this.markedPrice(stack) == price ? String.join("\n", this.markedLines(stack)).equals(pack(lines)) : false;
   }

   private void removeMarked(List<Component> lore, ItemMeta meta) {
      String packed = (String)meta.getPersistentDataContainer().get(this.linesKey, PersistentDataType.STRING);
      if (packed != null) {
         for (String marked : packed.split("\n", -1)) {
            for (int index = 0; index < lore.size(); index++) {
               if (LEGACY.serialize(lore.get(index)).equals(marked)) {
                  lore.remove(index);
                  break;
               }
            }
         }
      }

      meta.getPersistentDataContainer().remove(this.linesKey);
      meta.getPersistentDataContainer().remove(this.priceKey);
   }

   private static List<Component> existingLore(ItemMeta meta) {
      List<Component> lore = meta.lore();
      return lore == null ? List.of() : lore;
   }

   private static String pack(List<Component> lines) {
      StringBuilder builder = new StringBuilder();

      for (int index = 0; index < lines.size(); index++) {
         if (index > 0) {
            builder.append('\n');
         }

         builder.append(LEGACY.serialize(lines.get(index)));
      }

      return builder.toString();
   }
}
