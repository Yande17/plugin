package me.w2n.w2nsmp.gui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.config.GuiConfig;
import me.w2n.w2nsmp.stats.StatType;
import me.w2n.w2nsmp.utility.GuiKit;
import me.w2n.w2nsmp.utility.Items;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public final class ProfileMenu {
   public static final int SLOT_HEAD = 13;
   public static final int SLOT_MONEY = 19;
   public static final int SLOT_HIGHEST_MONEY = 20;
   public static final int SLOT_KILLS = 21;
   public static final int SLOT_DEATHS = 22;
   public static final int SLOT_PLAYTIME = 23;
   public static final int SLOT_BOUNTY = 24;
   public static final int SLOT_HOMES = 25;
   public static final int SLOT_ITEMS_SOLD = 30;
   public static final int SLOT_SETTINGS = 49;
   public static final int SLOT_TOP = 51;
   public static final int SLOT_CLOSE = 53;

   private ProfileMenu() {
   }

   public static GuiConfig gui(W2NSMP plugin) {
      return plugin.guiConfigs().profile();
   }

   public static int size(W2NSMP plugin) {
      return gui(plugin).size(54);
   }

   public static int slot(W2NSMP plugin, String path, int fallback) {
      int slot = gui(plugin).slot("slots." + path, fallback);
      return slot < size(plugin) ? slot : fallback;
   }

   public static void open(W2NSMP plugin, Player viewer, Player target) {
      Player subject = target == null ? viewer : target;
      Inventory inventory = build(plugin, viewer, subject);
      viewer.openInventory(inventory);
      plugin.guiSounds().play(viewer, gui(plugin), "open");
   }

   public static Inventory build(W2NSMP plugin, Player viewer, Player subject) {
      ProfileMenuHolder holder = new ProfileMenuHolder(viewer.getUniqueId());
      holder.subject(subject.getUniqueId());
      Inventory inventory = Bukkit.createInventory(holder, size(plugin), gui(plugin).title("profile.gui-title", "player", subject.getName()));
      holder.setInventory(inventory);
      render(plugin, inventory, subject);
      return inventory;
   }

   public static void render(W2NSMP plugin, Inventory inventory, Player subject) {
      GuiConfig gui = gui(plugin);
      int size = size(plugin);
      GuiKit.shell(
         plugin,
         gui,
         inventory,
         Material.GRAY_STAINED_GLASS_PANE,
         "profile.filler-material",
         Material.BLACK_STAINED_GLASS_PANE,
         slot -> slot == 13
            || slot >= 19 && slot <= 25
            || slot == 30
            || slot == slot(plugin, "settings", 49)
            || slot == slot(plugin, "top", 51)
            || slot == slot(plugin, "close", 53)
      );
      Map<String, String> values = values(plugin, subject);
      inventory.setItem(slot(plugin, "head", 13), head(plugin, subject, values));
      entry(plugin, inventory, "money", 19, "profile.money", Material.GOLD_INGOT, subject, values);
      entry(plugin, inventory, "highest-money", 20, "profile.highest-money", Material.GOLD_BLOCK, subject, values);
      entry(plugin, inventory, "kills", 21, "profile.kills", Material.IRON_SWORD, subject, values);
      entry(plugin, inventory, "deaths", 22, "profile.deaths", Material.SKELETON_SKULL, subject, values);
      entry(plugin, inventory, "playtime", 23, "profile.playtime", Material.CLOCK, subject, values);
      entry(plugin, inventory, "bounty", 24, "profile.bounty", Material.PLAYER_HEAD, subject, values);
      entry(plugin, inventory, "homes", 25, "profile.homes", Material.RED_BED, subject, values);
      entry(plugin, inventory, "items-sold", 30, "profile.items-sold", Material.DIAMOND, subject, values);
      inventory.setItem(
         slot(plugin, "settings", 49),
         Items.create(
            material(plugin, "profile.settings-material", Material.COMPARATOR),
            plugin.messages().raw("profile.settings-name", "player", subject.getName()),
            plugin.messages().rawList("profile.settings-lore", "player", subject.getName())
         )
      );
      inventory.setItem(
         slot(plugin, "top", 51),
         Items.create(
            material(plugin, "profile.top-material", Material.NETHER_STAR),
            plugin.messages().raw("profile.top-name"),
            plugin.messages().rawList("profile.top-lore")
         )
      );
      inventory.setItem(slot(plugin, "close", 53), GuiKit.nav(plugin, gui, "close", Material.BARRIER, "profile.close-name", "profile.close-lore"));
   }

   public static Map<String, String> values(W2NSMP plugin, Player subject) {
      Map<String, String> values = new HashMap<>();
      values.put("name", subject.getName());
      double balance = plugin.economy() != null && plugin.economy().isEnabled() ? plugin.economy().balance(subject) : 0.0;
      values.put("money", plugin.economy() != null && plugin.economy().isEnabled() ? plugin.economy().format(balance) : "-");
      if (plugin.stats() == null) {
         values.put("highest-money", "-");
         values.put("kills", "0");
         values.put("deaths", "0");
         values.put("playtime", "-");
         values.put("items-sold", "0");
      } else {
         long highest = plugin.stats().value(subject.getUniqueId(), StatType.HIGHEST_MONEY);
         long shown = Math.max(highest, (long)Math.floor(Math.max(0.0, balance)));
         values.put("highest-money", plugin.economy() == null ? Long.toString(shown) : plugin.economy().format(shown));
         values.put("kills", Long.toString(plugin.stats().value(subject.getUniqueId(), StatType.KILLS)));
         values.put("deaths", Long.toString(plugin.stats().value(subject.getUniqueId(), StatType.DEATHS)));
         values.put("playtime", plugin.stats().formatPlaytime(plugin.stats().value(subject.getUniqueId(), StatType.PLAYTIME)));
         values.put("items-sold", Long.toString(plugin.stats().value(subject.getUniqueId(), StatType.ITEMS_SOLD)));
      }

      values.put("bounty", bountyText(plugin, subject));
      values.put("homes", plugin.homes() == null ? "0" : plugin.homes().data(subject).used() + "/" + plugin.homes().data(subject).unlocked());
      return values;
   }

   private static String bountyText(W2NSMP plugin, Player subject) {
      if (plugin.stats() != null) {
         String[] pairs = plugin.stats().scoreboardPlaceholders(subject);

         for (int index = 0; index + 1 < pairs.length; index += 2) {
            if ("bounty".equals(pairs[index])) {
               return pairs[index + 1];
            }
         }
      }

      return "-";
   }

   private static ItemStack head(W2NSMP plugin, Player subject, Map<String, String> values) {
      Material material = material(plugin, "profile.head-material", Material.PLAYER_HEAD);
      ItemStack stack = Items.create(
         material,
         plugin.messages().raw("profile.head-name", "player", subject.getName(), "name", subject.getName()),
         plugin.messages()
            .rawList(
               "profile.head-lore", "player", subject.getName(), "money", values.getOrDefault("money", "-"), "playtime", values.getOrDefault("playtime", "-")
            )
      );
      if (material == Material.PLAYER_HEAD || material == Material.PLAYER_WALL_HEAD) {
         SkullMeta meta = (SkullMeta)stack.getItemMeta();
         if (meta != null) {
            meta.setOwningPlayer(subject);
            stack.setItemMeta(meta);
         }
      }

      return stack;
   }

   private static void entry(
      W2NSMP plugin, Inventory inventory, String path, int fallback, String message, Material icon, Player subject, Map<String, String> values
   ) {
      GuiConfig gui = gui(plugin);
      int slot = slot(plugin, path, fallback);
      String[] placeholders = new String[]{
         "player", subject.getName(), "value", values.getOrDefault(path, "-"), "money", values.getOrDefault("money", "-"), "name", subject.getName()
      };
      String name = gui.name("buttons." + path + ".name");
      List<String> lore = gui.lore("buttons." + path + ".lore");
      inventory.setItem(
         slot,
         Items.create(
            gui.material("buttons." + path + ".material", icon),
            name == null ? plugin.messages().raw(message + "-name", placeholders) : plugin.messages().apply(name, placeholders),
            lore.isEmpty() ? plugin.messages().rawList(message + "-lore", placeholders) : plugin.messages().applyList(lore, placeholders)
         )
      );
   }

   public static boolean canOpenSettings(Player viewer, Player subject) {
      return subject != null && viewer.getUniqueId().equals(subject.getUniqueId());
   }

   public static void closeAll(W2NSMP plugin) {
      for (Player player : Bukkit.getOnlinePlayers()) {
         InventoryView view = player.getOpenInventory();
         if (view != null && view.getTopInventory().getHolder() instanceof ProfileMenuHolder) {
            player.closeInventory();
         }
      }
   }

   private static Material material(W2NSMP plugin, String key, Material fallback) {
      return GuiKit.material(plugin, key, fallback);
   }
}
