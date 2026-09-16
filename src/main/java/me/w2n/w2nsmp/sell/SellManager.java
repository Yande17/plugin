package me.w2n.w2nsmp.sell;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.utility.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class SellManager {
   private final W2NSMP plugin;
   private final PriceRegistry prices;

   public SellManager(W2NSMP plugin) {
      super();
      this.plugin = plugin;
      this.prices = new PriceRegistry(plugin);
   }

   public PriceRegistry prices() {
      return this.prices;
   }

   public boolean isSellable(ItemStack stack) {
      return !Items.isEmpty(stack) && (this.prices.price(stack.getType()) > 0 || this.customFishValue(stack) > 0);
   }

   public int currentUnitPrice(ItemStack stack) {
      if (Items.isEmpty(stack)) {
         return 0;
      }

      // Ikan custom (v1.4.0): nilai jual tersimpan di PDC item MENANG atas harga material,
      // supaya Golden Koi tidak dihargai seperti ikan mas biasa.
      int fishValue = this.customFishValue(stack);
      if (fishValue > 0) {
         return fishValue;
      }

      return this.plugin.dynamic() == null ? this.prices.price(stack.getType()) : this.plugin.dynamic().currentPrice(stack.getType());
   }

   /** Nilai jual ikan custom di PDC (0 = bukan ikan custom). Aman bila layanan fishing mati. */
   private int customFishValue(ItemStack stack) {
      try {
         if (this.plugin.fishing() == null) {
            return 0;
         }

         long value = this.plugin.fishing().fishValue(stack);
         return value <= 0L ? 0 : (int)Math.min(Integer.MAX_VALUE, value);
      } catch (Throwable throwable) {
         return 0;
      }
   }

   public int currentUnitPrice(Material material) {
      if (material != null && !material.isAir()) {
         return this.plugin.dynamic() == null ? this.prices.price(material) : this.plugin.dynamic().currentPrice(material);
      } else {
         return 0;
      }
   }

   public SellResult evaluate(List<ItemStack> stacks) {
      long total = 0L;
      int itemCount = 0;
      Set<Material> unsellable = new LinkedHashSet<>();

      for (ItemStack stack : stacks) {
         if (!Items.isEmpty(stack)) {
            int unitPrice = this.currentUnitPrice(stack);
            if (unitPrice <= 0) {
               unsellable.add(stack.getType());
            } else {
               total += (long)unitPrice * stack.getAmount();
               itemCount += stack.getAmount();
            }
         }
      }

      return new SellResult(total, itemCount, new ArrayList<>(unsellable));
   }

   public SellResult evaluateBase(List<ItemStack> stacks) {
      long total = 0L;
      int itemCount = 0;
      Set<Material> unsellable = new LinkedHashSet<>();

      for (ItemStack stack : stacks) {
         if (!Items.isEmpty(stack)) {
            int unitPrice = this.prices.price(stack.getType());
            if (unitPrice <= 0) {
               unitPrice = this.customFishValue(stack);
            }

            if (unitPrice <= 0) {
               unsellable.add(stack.getType());
            } else {
               total += (long)unitPrice * stack.getAmount();
               itemCount += stack.getAmount();
            }
         }
      }

      return new SellResult(total, itemCount, new ArrayList<>(unsellable));
   }

   public void recordSale(List<ItemStack> sold) {
      if (this.plugin.dynamic() != null && this.plugin.dynamic().enabled() && sold != null) {
         for (ItemStack stack : sold) {
            if (!Items.isEmpty(stack) && this.prices.price(stack.getType()) > 0) {
               this.plugin.dynamic().recordSale(stack.getType(), stack.getAmount());
            }
         }
      }
   }

   public boolean payout(Player player, long total) {
      return total <= 0L ? false : this.plugin.economy().deposit(player, total);
   }
}
