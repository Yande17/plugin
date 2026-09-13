package me.w2n.w2nsmp.stats;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;

public enum TopCategory {
   MONEY("money", Material.GOLD_INGOT, null),
   MONEY_EARNED("money-earned", Material.GOLD_NUGGET, StatType.MONEY_EARNED),
   ITEMS_SOLD("items-sold", Material.DIAMOND, StatType.ITEMS_SOLD),
   AUCTION_SOLD("auction-sold", Material.EMERALD_BLOCK, StatType.AUCTION_SOLD),
   AUCTION_BOUGHT("auction-bought", Material.EMERALD, StatType.AUCTION_BOUGHT),
   AUCTION_LISTED("auction-listed", Material.CHEST, StatType.AUCTION_LISTED),
   KILLS("kills", Material.IRON_SWORD, StatType.KILLS),
   DEATHS("deaths", Material.SKELETON_SKULL, StatType.DEATHS),
   MOBS("mobs", Material.BONE, StatType.MOBS),
   BLOCKS_BROKEN("blocks-broken", Material.IRON_PICKAXE, StatType.BLOCKS_BROKEN),
   BLOCKS_PLACED("blocks-placed", Material.BRICKS, StatType.BLOCKS_PLACED),
   HOMES("homes", Material.RED_BED, StatType.HOMES),
   HOME_TELEPORTS("home-teleports", Material.ENDER_PEARL, StatType.HOME_TELEPORTS),
   RTP("rtp", Material.COMPASS, StatType.RTP),
   PLAYTIME("playtime", Material.CLOCK, StatType.PLAYTIME),
   HIGHEST_MONEY("highest-money", Material.GOLD_BLOCK, StatType.HIGHEST_MONEY),
   BOUNTY("bounty", Material.WITHER_SKELETON_SKULL, null, true);

   private final String key;
   private final Material icon;
   private final StatType statType;
   private final boolean bounty;

   TopCategory(String key, Material icon, StatType statType) {
      this(key, icon, statType, false);
   }

   TopCategory(String key, Material icon, StatType statType, boolean bounty) {
      this.key = key;
      this.icon = icon;
      this.statType = statType;
      this.bounty = bounty;
   }

   public String key() {
      return this.key;
   }

   public Material icon() {
      return this.icon;
   }

   public StatType statType() {
      return this.statType;
   }

   public boolean isMoney() {
      return this.statType == null && !this.bounty;
   }

   public boolean isBounty() {
      return this.bounty;
   }

   public String messageKey() {
      return this.statType == null ? "stats.category." + this.key : this.statType.messageKey();
   }

   public static TopCategory fromKey(String raw) {
      if (raw != null && !raw.isBlank()) {
         String wanted = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');

         for (TopCategory category : values()) {
            if (category.key.equals(wanted) || category.name().toLowerCase(Locale.ROOT).equals(wanted)) {
               return category;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public static List<String> keys() {
      List<String> keys = new ArrayList<>(values().length);

      for (TopCategory category : values()) {
         keys.add(category.key);
      }

      return keys;
   }
}
