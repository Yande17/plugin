package me.w2n.w2nsmp.sell;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public record SellResult(long total, int itemCount, List<Material> unsellableMaterials) {
   public SellResult {
   }

   public boolean isEmpty() {
      return this.itemCount <= 0;
   }

   public boolean isUnsellable(ItemStack stack) {
      return stack != null && this.unsellableMaterials.contains(stack.getType());
   }
}
