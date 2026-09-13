package me.w2n.w2nsmp.gui;

import java.util.ArrayList;
import java.util.List;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.config.GuiConfig;
import me.w2n.w2nsmp.player.PlayerSettingsService;
import me.w2n.w2nsmp.scoreboard.ScoreboardLine;
import me.w2n.w2nsmp.utility.GuiKit;
import me.w2n.w2nsmp.utility.Items;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

public final class SettingsMenu {
   public static final int SLOT_SCOREBOARD = 10;
   public static final int SLOT_NAMETAG_MONEY = 11;
   public static final int SLOT_SOUNDS = 12;
   public static final int SLOT_NOTIFICATIONS = 13;
   public static final int SLOT_TELEPORT_COUNTDOWN = 14;
   public static final int SLOT_NOTIFY_BOUNTY = 15;
   public static final int SLOT_NOTIFY_AUCTION = 16;
   public static final int SLOT_NOTIFY_TPA = 17;
   public static final int SLOT_LINE_START = 20;
   public static final int SLOT_INFO = 49;
   public static final int SLOT_CLOSE = 53;

   private SettingsMenu() {
   }

   public static GuiConfig gui(W2NSMP plugin) {
      return plugin.guiConfigs().settings();
   }

   public static int size(W2NSMP plugin) {
      return gui(plugin).size(54);
   }

   public static int slot(W2NSMP plugin, String path, int fallback) {
      int slot = gui(plugin).slot("slots." + path, fallback);
      return slot < size(plugin) ? slot : fallback;
   }

   public static int lineStart(W2NSMP plugin) {
      return slot(plugin, "line-start", 20);
   }

   public static void open(W2NSMP plugin, Player player) {
      SettingsMenuHolder holder = new SettingsMenuHolder(player.getUniqueId());
      Inventory inventory = Bukkit.createInventory(holder, size(plugin), gui(plugin).title("setting.gui-title", "player", player.getName()));
      holder.setInventory(inventory);
      render(plugin, inventory, player);
      player.openInventory(inventory);
      plugin.guiSounds().play(player, gui(plugin), "open");
   }

   public static void render(W2NSMP plugin, Inventory inventory, Player player) {
      GuiConfig gui = gui(plugin);
      int size = size(plugin);
      GuiKit.shell(
         plugin,
         gui,
         inventory,
         Material.GRAY_STAINED_GLASS_PANE,
         "setting.filler-material",
         Material.BLACK_STAINED_GLASS_PANE,
         slot -> slot >= 9 && slot <= 44 || slot == slot(plugin, "info", 49) || slot == slot(plugin, "close", 53)
      );
      toggle(
         plugin,
         inventory,
         player,
         "scoreboard",
         10,
         plugin.scoreboard() != null && plugin.scoreboard().visibleFor(player),
         plugin.config().scoreboardPersonalToggle(),
         Material.CLOCK
      );
      toggle(plugin, inventory, player, "nametag-money", 11, plugin.settings().nametagMoney(player), plugin.config().nametagMoneyEnabled(), Material.NAME_TAG);
      toggle(plugin, inventory, player, "sounds", 12, plugin.settings().sounds(player), plugin.config().soundsEnabled(), Material.NOTE_BLOCK);
      toggle(plugin, inventory, player, "notifications", 13, plugin.settings().notifications(player), true, Material.BELL);
      toggle(plugin, inventory, player, "teleport-countdown", 14, plugin.settings().teleportCountdown(player), true, Material.ENDER_PEARL);
      toggle(plugin, inventory, player, "notify-bounty", 15, plugin.settings().bountyNotifications(player), true, Material.GOLD_INGOT);
      toggle(plugin, inventory, player, "notify-auction", 16, plugin.settings().auctionNotifications(player), true, Material.EMERALD);
      toggle(plugin, inventory, player, "notify-tpa", 17, plugin.settings().tpaNotifications(player), true, Material.COMPASS);
      renderLines(plugin, inventory, player);
      inventory.setItem(
         slot(plugin, "info", 49),
         Items.create(
            material(plugin, "setting.info-material", Material.BOOK),
            plugin.messages().raw("setting.info-name", "player", player.getName()),
            plugin.messages().rawList("setting.info-lore", "file", plugin.settings().file().getName())
         )
      );
      inventory.setItem(slot(plugin, "close", 53), GuiKit.nav(plugin, gui, "close", Material.BARRIER, "setting.close-name", "setting.close-lore"));
   }

   private static void renderLines(W2NSMP plugin, Inventory inventory, Player player) {
      if (plugin.scoreboard() != null) {
         int start = lineStart(plugin);
         int size = size(plugin);
         int index = 0;

         for (ScoreboardLine line : plugin.scoreboard().lines()) {
            int slot = start + index;
            index++;
            if (slot < size && slot != slot(plugin, "info", 49) && slot != slot(plugin, "close", 53)) {
               boolean toggled = plugin.scoreboard().isLineVisible(player.getUniqueId(), line.key());
               boolean locked = !line.enabled() || !line.playerToggle();
               inventory.setItem(slot, toggleItem(plugin, "line:" + line.key(), toggled, locked, Material.OAK_SIGN));
            }
         }
      }
   }

   private static void toggle(W2NSMP plugin, Inventory inventory, Player player, String key, int fallbackSlot, boolean value, boolean allowed, Material icon) {
      inventory.setItem(slot(plugin, key, fallbackSlot), toggleItem(plugin, key, value, !allowed, icon));
   }

   private static ItemStack toggleItem(W2NSMP plugin, String key, boolean value, boolean locked, Material icon) {
      String state = locked ? plugin.messages().raw("setting.state-locked") : plugin.messages().raw(value ? "setting.state-on" : "setting.state-off");
      String[] placeholders = new String[]{"state", state, "setting", label(plugin, key), "line", label(plugin, key), "key", key};
      GuiConfig gui = gui(plugin);
      Material material = materialFor(plugin, value, locked, icon);
      String name = gui.name("buttons." + key.replace(':', '-') + ".name");
      List<String> lore = gui.lore("buttons." + key.replace(':', '-') + ".lore");
      return Items.create(
         material,
         name == null ? plugin.messages().raw("setting.item-name", placeholders) : plugin.messages().apply(name, placeholders),
         lore.isEmpty() ? plugin.messages().rawList("setting.item-lore", placeholders) : plugin.messages().applyList(lore, placeholders)
      );
   }

   public static String label(W2NSMP plugin, String key) {
      if (key != null && key.startsWith("line:")) {
         String lineKey = key.substring("line:".length());
         String translated = plugin.messages().has("stats.category." + lineKey) ? plugin.messages().raw("stats.category." + lineKey) : lineKey;
         return plugin.messages().raw("setting.label.line", "line", translated);
      } else {
         String id = key == null ? "" : key;
         return plugin.messages().has("setting.label." + id) ? plugin.messages().raw("setting.label." + id) : id;
      }
   }

   private static Material materialFor(W2NSMP plugin, boolean value, boolean locked, Material fallback) {
      GuiConfig gui = gui(plugin);
      if (locked) {
         return gui.material("materials.locked", Material.BARRIER);
      }

      Material inactive = gui.material("materials.inactive", Material.GRAY_DYE);
      return value ? gui.material("materials.active", fallback) : inactive;
   }

   public static List<String> keys(W2NSMP plugin) {
      List<String> keys = new ArrayList<>(
         List.of("scoreboard", "nametag-money", "sounds", "notifications", "teleport-countdown", "notify-bounty", "notify-auction", "notify-tpa")
      );
      if (plugin.scoreboard() != null) {
         for (ScoreboardLine line : plugin.scoreboard().lines()) {
            keys.add("line:" + line.key());
         }
      }

      return keys;
   }

   public static boolean toggle(W2NSMP plugin, Player player, String key) {
      if (key == null) {
         return false;
      }

      PlayerSettingsService settings = plugin.settings();
      if (key.startsWith("line:")) {
         if (plugin.scoreboard() == null) {
            return false;
         }

         String lineKey = key.substring("line:".length());
         boolean visible = plugin.scoreboard().isLineVisible(player.getUniqueId(), lineKey);
         return plugin.scoreboard().setLineVisible(player, lineKey, !visible);
      } else {
         return switch (key) {
            case "scoreboard" -> plugin.scoreboard() != null && plugin.scoreboard().setVisible(player, !plugin.scoreboard().visibleFor(player));
            case "nametag-money" -> {
               boolean value = !settings.nametagMoney(player);
               boolean changed = settings.set(player, "nametag-money", value);
               if (changed && plugin.nametag() != null) {
                  if (value) {
                     plugin.nametag().apply(player);
                  } else {
                     plugin.nametag().remove(player);
                  }
               }

               yield changed;
            }
            case "sounds" -> settings.set(player, "sounds", !settings.sounds(player));
            case "notifications" -> settings.set(player, "notifications", !settings.notifications(player));
            case "teleport-countdown" -> settings.set(player, "teleport-countdown", !settings.teleportCountdown(player));
            case "notify-bounty" -> settings.set(player, "notify-bounty", !settings.bountyNotifications(player));
            case "notify-auction" -> settings.set(player, "notify-auction", !settings.auctionNotifications(player));
            case "notify-tpa" -> settings.set(player, "notify-tpa", !settings.tpaNotifications(player));
            default -> false;
         };
      }
   }

   public static boolean locked(W2NSMP plugin, String path, Player player) {
      if (path == null) {
         return true;
      }

      if (!path.startsWith("line:")) {
         return switch (path) {
            case "scoreboard" -> !plugin.config().scoreboardPersonalToggle();
            case "nametag-money" -> !plugin.config().nametagMoneyEnabled();
            case "sounds" -> !plugin.config().soundsEnabled();
            default -> false;
         };
      } else {
         if (plugin.scoreboard() == null) {
            return true;
         }

         String lineKey = path.substring("line:".length());

         for (ScoreboardLine line : plugin.scoreboard().lines()) {
            if (line.key().equalsIgnoreCase(lineKey)) {
               return !line.enabled() || !line.playerToggle();
            }
         }

         return true;
      }
   }

   public static String keyAt(W2NSMP plugin, int slot) {
      if (slot == slot(plugin, "scoreboard", 10)) {
         return "scoreboard";
      }

      if (slot == slot(plugin, "nametag-money", 11)) {
         return "nametag-money";
      }

      if (slot == slot(plugin, "sounds", 12)) {
         return "sounds";
      }

      if (slot == slot(plugin, "notifications", 13)) {
         return "notifications";
      }

      if (slot == slot(plugin, "teleport-countdown", 14)) {
         return "teleport-countdown";
      }

      if (slot == slot(plugin, "notify-bounty", 15)) {
         return "notify-bounty";
      }

      if (slot == slot(plugin, "notify-auction", 16)) {
         return "notify-auction";
      }

      if (slot == slot(plugin, "notify-tpa", 17)) {
         return "notify-tpa";
      }

      if (slot == slot(plugin, "close", 53)) {
         return "close";
      }

      if (slot == slot(plugin, "info", 49)) {
         return "info";
      }

      int index = slot - lineStart(plugin);
      if (index >= 0 && plugin.scoreboard() != null) {
         List<ScoreboardLine> lines = plugin.scoreboard().lines();
         if (index < lines.size()) {
            return "line:" + lines.get(index).key();
         }
      }

      return null;
   }

   public static void closeAll(W2NSMP plugin) {
      for (Player player : Bukkit.getOnlinePlayers()) {
         InventoryView view = player.getOpenInventory();
         if (view != null && view.getTopInventory().getHolder() instanceof SettingsMenuHolder) {
            player.closeInventory();
         }
      }
   }

   private static Material material(W2NSMP plugin, String key, Material fallback) {
      return GuiKit.material(plugin, key, fallback);
   }
}
