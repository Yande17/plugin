package me.w2n.w2nsmp.utility;

import java.util.ArrayList;
import java.util.List;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.gui.AuctionMenu;
import me.w2n.w2nsmp.gui.AuctionMenuHolder;
import me.w2n.w2nsmp.gui.ConfirmMenu;
import me.w2n.w2nsmp.gui.ConfirmMenuHolder;
import me.w2n.w2nsmp.gui.HomeMenu;
import me.w2n.w2nsmp.gui.HomeMenuHolder;
import me.w2n.w2nsmp.gui.LeaderboardHolder;
import me.w2n.w2nsmp.gui.LeaderboardMenu;
import me.w2n.w2nsmp.gui.SellMenu;
import me.w2n.w2nsmp.gui.SellMenuHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class CompatAudit {
   private final W2NSMP plugin;

   public CompatAudit(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   public List<CompatAudit.GuiCheck> audit(Player player) {
      if (player != null && player.isOnline()) {
         List<CompatAudit.GuiCheck> checks = new ArrayList<>(5);
         checks.add(this.check(player, "sell", () -> SellMenu.open(this.plugin, player), SellMenuHolder.class));
         checks.add(this.check(player, "home", () -> HomeMenu.open(this.plugin, player), HomeMenuHolder.class));
         checks.add(this.check(player, "auction", () -> AuctionMenu.open(this.plugin, player, 1, null), AuctionMenuHolder.class));
         checks.add(
            this.check(player, "top", () -> LeaderboardMenu.open(this.plugin, player, LeaderboardMenu.defaultCategory(this.plugin), 1), LeaderboardHolder.class)
         );
         checks.add(
            this.check(
               player, "confirm", () -> ConfirmMenu.open(this.plugin, player, "home.purchase", confirmed -> {}, null, "price", "$1"), ConfirmMenuHolder.class
            )
         );
         return List.copyOf(checks);
      } else {
         return List.of();
      }
   }

   public int safeCount(List<CompatAudit.GuiCheck> checks) {
      int safe = 0;

      for (CompatAudit.GuiCheck check : checks) {
         if (check.safe()) {
            safe++;
         }
      }

      return safe;
   }

   public boolean bedrockSafe(List<CompatAudit.GuiCheck> checks) {
      return !checks.isEmpty() && this.safeCount(checks) == checks.size();
   }

   public String summary(List<CompatAudit.GuiCheck> checks) {
      return this.safeCount(checks) + "/" + checks.size();
   }

   public List<String> designLines() {
      return List.of(
         "GUI      : inventory chest standar + custom InventoryHolder (judul tidak pernah dibaca)",
         "Klik     : semua aksi bisa dilakukan dengan satu klik kiri/kanan (tanpa shift/double-click)",
         "Input    : tidak ada GUI anvil/sign/merchant yang butuh mengetik di Bedrock",
         "UUID     : UUID asli server dipakai apa adanya (Floodgate/XUID tidak diubah)",
         "Item     : GUI menutup dengan aman saat disconnect/death/reload; item tidak hilang"
      );
   }

   private CompatAudit.GuiCheck check(Player player, String name, Runnable opener, Class<?> expectedHolder) {
      try {
         opener.run();
         Inventory top = player.getOpenInventory().getTopInventory();
         InventoryHolder holder = top.getHolder();
         boolean chest = top.getType() == InventoryType.CHEST;
         boolean custom = expectedHolder.isInstance(holder);
         String holderName = holder == null ? "null" : holder.getClass().getSimpleName();
         String detail = top.getType() + " " + top.getSize() + " slot, holder=" + holderName;
         return new CompatAudit.GuiCheck(name, chest, custom, holderName, detail);
      } catch (RuntimeException exception) {
         return new CompatAudit.GuiCheck(name, false, false, "error", "gagal dibuka: " + exception);
      } finally {
         try {
            player.closeInventory();
         } catch (RuntimeException var20) {
         }
      }
   }

   public record GuiCheck(String name, boolean chest, boolean customHolder, String holderName, String detail) {
      public GuiCheck {
      }

      public boolean safe() {
         return this.chest && this.customHolder;
      }
   }
}
