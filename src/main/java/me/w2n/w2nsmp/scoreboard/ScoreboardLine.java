package me.w2n.w2nsmp.scoreboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public record ScoreboardLine(String key, boolean enabled, boolean playerToggle, List<String> texts) {
   private static final Map<String, String> KEY_BY_PLACEHOLDER = new LinkedHashMap<>();
   private static final Map<String, List<String>> DEFAULT_TEXTS = new LinkedHashMap<>();

   public ScoreboardLine {
   }

   private static void register(String placeholder, String key) {
      KEY_BY_PLACEHOLDER.put(placeholder, key);
      DEFAULT_TEXTS.put(key, defaultTextsFor(key));
   }

   private static List<String> defaultTextsFor(String key) {
      return switch (key) {
         case "money" -> List.of("&fMoney: &a%money%");
         case "kills" -> List.of("&fKills: &c%kills%");
         case "deaths" -> List.of("&fDeaths: &c%deaths%");
         case "playtime" -> List.of("&fPlaytime: &b%playtime%");
         case "online" -> List.of("&fOnline: &a%online%");
         case "bounty" -> List.of("&fBounty: &6%bounty%");
         case "ping" -> List.of("&fPing: &a%ping%");
         case "mobs" -> List.of("&fMob dibunuh: &e%mobs%");
         case "items-sold" -> List.of("&fItem terjual: &e%items-sold%");
         case "money-earned" -> List.of("&fUang didapat: &6%money-earned%");
         case "auction-sold" -> List.of("&fAuction terjual: &e%auction-sold%");
         case "auction-bought" -> List.of("&fAuction dibeli: &e%auction-bought%");
         case "auction-listed" -> List.of("&fAuction dipasang: &e%auction-listed%");
         case "blocks-broken" -> List.of("&fBlok ditambang: &e%blocks-broken%");
         case "blocks-placed" -> List.of("&fBlok dipasang: &e%blocks-placed%");
         case "homes" -> List.of("&fHome: &e%homes%");
         case "home-teleports" -> List.of("&fTeleport home: &e%home-teleports%");
         case "combat" -> List.of("&fCombat: &c%combat%");
         case "world" -> List.of("&fDunia: &a%world%");
         default -> List.of("&f%player%: &a%world%");
      };
   }

   public static List<String> defaultTexts(String key) {
      return DEFAULT_TEXTS.getOrDefault(key, List.of("&f" + key + ": &a%" + key + "%"));
   }

   public static boolean isKnown(String key) {
      return DEFAULT_TEXTS.containsKey(key);
   }

   public static ScoreboardLine parse(String key, ConfigurationSection section) {
      String name = key != null && !key.isBlank() ? key.trim().toLowerCase(Locale.ROOT) : "line";
      boolean enabled = section == null || section.getBoolean("enabled", true);
      boolean toggle = section == null || section.getBoolean("player-toggle", true);
      return new ScoreboardLine(name, enabled, toggle, readTexts(name, section));
   }

   private static List<String> readTexts(String key, ConfigurationSection section) {
      List<String> result = new ArrayList<>(2);
      if (section != null && section.isList("text")) {
         for (String line : section.getStringList("text")) {
            if (line != null && !line.isEmpty()) {
               result.add(line);
            }
         }
      } else if (section != null && section.isString("text")) {
         String single = section.getString("text", "");
         if (!single.isEmpty()) {
            result.add(single);
         }
      }

      return List.copyOf(result);
   }

   public ScoreboardLine withTexts(List<String> texts) {
      return new ScoreboardLine(this.key, this.enabled, this.playerToggle, List.copyOf(texts));
   }

   public static ScoreboardLine legacy(String text, int index) {
      String key = guessKey(text, index);
      return new ScoreboardLine(key, true, true, List.of(text == null ? "" : text));
   }

   public static String guessKey(String text, int index) {
      if (text != null) {
         int start = text.indexOf(37);

         while (start >= 0) {
            int end = text.indexOf(37, start + 1);
            if (end < 0) {
               break;
            }

            String token = text.substring(start + 1, end).toLowerCase(Locale.ROOT);
            String key = KEY_BY_PLACEHOLDER.get(token);
            if (key != null) {
               return key;
            }

            start = text.indexOf(37, end + 1);
         }
      }

      return "line-" + index;
   }

   public int placeholderTokens() {
      int count = 0;

      for (String text : this.texts) {
         int start = text.indexOf(37);

         while (start >= 0) {
            int end = text.indexOf(37, start + 1);
            if (end < 0) {
               break;
            }

            count++;
            start = text.indexOf(37, end + 1);
         }
      }

      return count;
   }

   static {
      register("money-earned", "money-earned");
      register("items-sold", "items-sold");
      register("blocks-broken", "blocks-broken");
      register("blocks-placed", "blocks-placed");
      register("auction-sold", "auction-sold");
      register("auction-bought", "auction-bought");
      register("auction-listed", "auction-listed");
      register("home-teleports", "home-teleports");
      register("playtime", "playtime");
      register("mobs", "mobs");
      register("kills", "kills");
      register("deaths", "deaths");
      register("money", "money");
      register("bounty", "bounty");
      register("online", "online");
      register("ping", "ping");
      register("combat", "combat");
      register("homes", "homes");
      register("world", "world");
      register("player", "player");
   }
}
