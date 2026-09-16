package me.w2n.w2nsmp.stats;

import java.util.Locale;
import org.bukkit.Material;

public enum StatType {
   MONEY_EARNED("money-earned", Material.GOLD_NUGGET),
   ITEMS_SOLD("items-sold", Material.DIAMOND),
   AUCTION_LISTED("auction-listed", Material.CHEST),
   AUCTION_SOLD("auction-sold", Material.EMERALD_BLOCK),
   AUCTION_BOUGHT("auction-bought", Material.EMERALD),
   HOMES("homes", Material.RED_BED),
   HOME_TELEPORTS("home-teleports", Material.ENDER_PEARL),
   RTP("rtp", Material.COMPASS),
   KILLS("kills", Material.IRON_SWORD),
   DEATHS("deaths", Material.SKELETON_SKULL),
   MOBS("mobs", Material.BONE),
   BLOCKS_BROKEN("blocks-broken", Material.IRON_PICKAXE),
   BLOCKS_PLACED("blocks-placed", Material.BRICKS),
   PLAYTIME("playtime", Material.CLOCK),
   HIGHEST_MONEY("highest-money", Material.GOLD_BLOCK),
   TPA_SENT("tpa-sent", Material.ENDER_EYE),
   TPA_ACCEPTED("tpa-accepted", Material.ENDER_PEARL);

   private final String key;
   private final Material icon;

   StatType(String key, Material icon) {
      this.key = key;
      this.icon = icon;
   }

   public String key() {
      return this.key;
   }

   public Material icon() {
      return this.icon;
   }

   public String messageKey() {
      return "stats.category." + this.key;
   }

   public static StatType fromKey(String raw) {
      if (raw != null && !raw.isBlank()) {
         String wanted = raw.trim().toLowerCase(Locale.ROOT);

         for (StatType type : values()) {
            if (type.key.equals(wanted) || type.name().toLowerCase(Locale.ROOT).equals(wanted)) {
               return type;
            }
         }

         return null;
      } else {
         return null;
      }
   }
}
