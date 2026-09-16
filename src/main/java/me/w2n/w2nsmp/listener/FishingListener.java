package me.w2n.w2nsmp.listener;

import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.fishing.CustomFish;
import me.w2n.w2nsmp.fishing.FishingService;
import me.w2n.w2nsmp.skill.SkillService;
import me.w2n.w2nsmp.skill.SkillType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listener custom fishing & rod (v1.4.0).
 *
 * <p>Semua keputusan (undian ikan, syarat biome/cuaca/waktu/level, efek rod) ada di
 * {@link FishingService} - listener ini hanya membaca event lalu memanggil layanan, sesuai
 * aturan "registry terpusat, tidak ada logika tersebar di listener". Terpisah dari
 * {@link SkillFishingListener} (XP skill) supaya kedua fitur bisa mati-hidup sendiri-sendiri.
 */
public final class FishingListener implements Listener {
   private final W2NSMP plugin;

   public FishingListener(W2NSMP plugin) {
      this.plugin = plugin;
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onFish(PlayerFishEvent event) {
      try {
         this.handleFish(event);
      } catch (Throwable throwable) {
         this.plugin.debug("Fishing: gangguan di handler memancing (" + throwable + ").");
      }
   }

   private void handleFish(PlayerFishEvent event) {
      FishingService fishing = this.plugin.fishing();
      if (fishing == null || !fishing.enabled()) {
         return;
      }

      Player player = event.getPlayer();
      if (player == null) {
         return;
      }

      // Bonus kecepatan gigitan diterapkan saat lempar (state FISHING); tangkapan diproses
      // saat CAUGHT_FISH. getState dibungkus try/catch untuk jaga-jaga di API lain.
      String state;
      try {
         state = String.valueOf(event.getState());
      } catch (Throwable throwable) {
         state = "CAUGHT_FISH";
      }

      ItemStack rod = player.getInventory().getItemInMainHand();
      boolean holdingRod = fishing.isRod(rod);

      if ("FISHING".equals(state)) {
         if (holdingRod) {
            this.applyBiteSpeed(event, fishing, rod);
         }

         return;
      }

      if (!"CAUGHT_FISH".equals(state) || !(event.getCaught() instanceof Item caughtItem)) {
         return;
      }

      SkillService skills = this.plugin.skills();
      int fishingLevel = skills == null ? 0 : skills.level(player, SkillType.FISHING);
      int rodLevel = holdingRod ? fishing.rodLevel(rod) : 1;

      // XP rod per tangkapan (vanilla ataupun custom).
      if (holdingRod) {
         fishing.addRodXp(rod, fishing.rodXpPerCatch());
      }

      String biome = this.biomeName(player);
      boolean storming = this.storming(player);
      long time = this.worldTime(player);

      double bonusChance = holdingRod ? fishing.effectTotal(rod, "custom-chance") : 0.0D;
      double rarityBoost = holdingRod ? 1.0D + fishing.effectTotal(rod, "rarity-boost") / 100.0D : 1.0D;

      CustomFish rolled = fishing.roll(biome, storming, time, fishingLevel, rodLevel, bonusChance, rarityBoost);
      if (rolled != null) {
         // Ganti isi entity item tangkapan dengan ikan custom - tetap "ditarik" secara vanilla,
         // jadi tidak ada dupe dan momentum kail tidak berubah. Efek "value" (Golden Hook /
         // level rod) ikut dihitung supaya lore & PDC konsisten.
         double valueBonus = holdingRod ? fishing.effectTotal(rod, "value") : 0.0D;
         ItemStack custom = fishing.createFish(rolled, valueBonus);
         try {
            caughtItem.setItemStack(custom);
         } catch (Throwable throwable) {
            this.plugin.debug("Fishing: gagal mengganti tangkapan custom (" + throwable + ").");
            return;
         }

         if (holdingRod) {
            fishing.addRodXp(rod, fishing.rodXpPerCustomCatch());
         }

         if (skills != null && skills.enabled() && rolled.xp() > 0.0D) {
            // Efek "xp" (XP Reel / level rod): XP skill memancing dinaikkan sekian persen.
            double xpBonus = holdingRod ? fishing.effectTotal(rod, "xp") : 0.0D;
            skills.addXp(player, SkillType.FISHING, rolled.xp() * (1.0D + xpBonus / 100.0D));
         }

         this.plugin.messages().send(player, "fishing.caught",
            "fish", rolled.fishName(),
            "rarity", fishing.rarityLabel(rolled.rarity()));

         // v1.4.1: catat penemuan untuk Fish Gallery (/fish). Penemuan baru diumumkan.
         if (fishing.discovery().discover(player.getUniqueId(), rolled.id())) {
            this.plugin.messages().send(player, "fishing.discovered",
               "fish", rolled.fishName(),
               "rarity", fishing.rarityLabel(rolled.rarity()));
         }

         // Bonus bahan upgrade (drop-chance-percent di config; inventory penuh -> jatuh di kaki).
         // Efek "luck" (Fishing Luck, v1.4.1) memperbesar peluang drop secara relatif.
         double luck = holdingRod ? fishing.effectTotal(rod, "luck") : 0.0D;
         me.w2n.w2nsmp.fishing.FishingItem bonus = fishing.rollBonusItem(luck);
         if (bonus != null) {
            ItemStack bonusStack = fishing.createItem(bonus, 1);
            java.util.HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(bonusStack);
            if (leftover != null) {
               for (ItemStack rest : leftover.values()) {
                  if (rest != null) {
                     player.getWorld().dropItemNaturally(player.getLocation(), rest);
                  }
               }
            }

            this.plugin.messages().send(player, "fishing.bonus-item", "name", bonus.itemName());
         }
      }

      // Efek "treasure" (Treasure Chance, v1.4.1): peluang harta karun tambahan, berlaku juga
      // untuk tangkapan vanilla. Inventory penuh -> jatuh di kaki (tidak pernah hilang).
      double treasureChance = fishing.treasureBaseChance()
         + (holdingRod ? fishing.effectTotal(rod, "treasure") : 0.0D);
      ItemStack treasure = fishing.rollTreasure(treasureChance);
      if (treasure != null) {
         java.util.HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(treasure);
         if (leftover != null) {
            for (ItemStack rest : leftover.values()) {
               if (rest != null) {
                  player.getWorld().dropItemNaturally(player.getLocation(), rest);
               }
            }
         }

         this.plugin.messages().send(player, "fishing.treasure-found",
            "item", treasure.getType().name(),
            "amount", Integer.toString(treasure.getAmount()));
      }

      // Peluang tangkapan ganda dari rod/attachment (persen).
      if (holdingRod) {
         double doubleChance = fishing.effectTotal(rod, "double-catch");
         if (doubleChance > 0.0D && Math.random() * 100.0D < doubleChance) {
            try {
               ItemStack stack = caughtItem.getItemStack();
               if (stack != null && !stack.getType().isAir()) {
                  player.getInventory().addItem(stack.clone());
               }
            } catch (Throwable ignored) {
            }
         }

         // Tulis balik rod (getItemInMainHand mengembalikan salinan di CraftBukkit).
         fishing.refreshRodLore(rod);
         player.getInventory().setItemInMainHand(rod);
      }
   }

   /** Efek bite-speed: kurangi waktu tunggu gigitan sekian persen (dibungkus try/catch). */
   private void applyBiteSpeed(PlayerFishEvent event, FishingService fishing, ItemStack rod) {
      double percent = fishing.effectTotal(rod, "bite-speed");
      if (percent <= 0.0D) {
         return;
      }

      try {
         // Waktu tunggu vanilla 100..600 tick; kita set ulang ke rentang yang dipersingkat.
         double factor = Math.max(0.2D, 1.0D - percent / 100.0D);
         int wait = (int)Math.round((100.0D + Math.random() * 500.0D) * factor);
         event.getHook().setWaitTime(Math.max(20, wait));
      } catch (Throwable throwable) {
         this.plugin.debug("Fishing: bite-speed tidak didukung server ini (" + throwable + ").");
      }
   }

   private String biomeName(Player player) {
      try {
         return String.valueOf(player.getWorld()
            .getBlockAt(player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ())
            .getBiome());
      } catch (Throwable throwable) {
         return null;
      }
   }

   private boolean storming(Player player) {
      try {
         return player.getWorld().hasStorm();
      } catch (Throwable throwable) {
         return false;
      }
   }

   private long worldTime(Player player) {
      try {
         return player.getWorld().getTime();
      } catch (Throwable throwable) {
         return 6000L;
      }
   }
}
