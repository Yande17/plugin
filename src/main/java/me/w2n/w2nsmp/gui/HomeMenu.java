package me.w2n.w2nsmp.gui;

import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.config.GuiConfig;
import me.w2n.w2nsmp.home.Home;
import me.w2n.w2nsmp.home.PlayerHomes;
import me.w2n.w2nsmp.utility.GuiKit;
import me.w2n.w2nsmp.utility.Items;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

public final class HomeMenu {
   public static final int ROWS = 6;
   public static final int SIZE = 54;
   public static final int HOME_SLOT_START = 10;
   public static final int SLOT_INFO = 4;
   public static final int SLOT_CLOSE = 49;

   private HomeMenu() {
   }

   public static GuiConfig gui(W2NSMP plugin) {
      return plugin.guiConfigs().home();
   }

   public static int size(W2NSMP plugin) {
      return gui(plugin).size(54);
   }

   public static int homeSlotStart(W2NSMP plugin) {
      int configured = gui(plugin).slot("slots.home-start", 10);
      if (configured + 8 > size(plugin)) {
         plugin.getLogger().warning("gui/home.yml: slots.home-start=" + configured + " tidak muat untuk 8 slot home; memakai 10.");
         return 10;
      } else {
         return configured;
      }
   }

   public static int slotInfo(W2NSMP plugin) {
      return gui(plugin).slot("buttons.info.slot", 4);
   }

   public static int slotClose(W2NSMP plugin) {
      return gui(plugin).slot("buttons.close.slot", Math.max(0, size(plugin) - 5));
   }

   public static int homeIndexOf(int rawSlot) {
      int index = rawSlot - 10;
      return index >= 0 && index < 8 ? index : -1;
   }

   public static int homeIndexOf(W2NSMP plugin, int rawSlot) {
      int index = rawSlot - homeSlotStart(plugin);
      return index >= 0 && index < 8 ? index : -1;
   }

   public static void open(W2NSMP plugin, Player player) {
      HomeMenuHolder holder = new HomeMenuHolder(player.getUniqueId());
      Inventory inventory = Bukkit.createInventory(
         holder,
         size(plugin),
         gui(plugin)
            .title(
               "home.gui-title",
               "used",
               Integer.toString(plugin.homes().data(player).used()),
               "slots",
               Integer.toString(plugin.homes().data(player).unlocked())
            )
      );
      holder.setInventory(inventory);
      decorate(plugin, inventory);
      refresh(plugin, player, inventory);
      player.openInventory(inventory);
      plugin.guiSounds().play(player, gui(plugin), "open");
   }

   public static void refresh(W2NSMP plugin, Player player, Inventory inventory) {
      PlayerHomes data = plugin.homes().data(player);
      int maxSlots = plugin.homes().maxSlots();

      for (int index = 0; index < 8; index++) {
         int slot = homeSlotStart(plugin) + index;
         Home home = data.homeAt(index);
         boolean unlocked = data.isUnlocked(index);
         boolean withinLimits = index < maxSlots;
         if (!withinLimits) {
            inventory.setItem(
               slot,
               Items.create(
                  material(plugin, "home.slot-unavailable-material", Material.BARRIER),
                  plugin.messages().raw("home.slot-unavailable-name", "slot", Integer.toString(index + 1)),
                  plugin.messages().rawList("home.slot-unavailable-lore", "slot", Integer.toString(index + 1))
               )
            );
         } else {
            String slotNumber = Integer.toString(index + 1);
            String price = plugin.economy().format(plugin.homes().priceFor(index));
            if (!unlocked) {
               inventory.setItem(
                  slot,
                  Items.create(
                     material(plugin, "home.slot-locked-material", Material.BLACK_BED),
                     plugin.messages().raw("home.slot-locked-name", "slot", slotNumber),
                     plugin.messages().rawList("home.slot-locked-lore", "slot", slotNumber, "price", price)
                  )
               );
            } else if (home == null) {
               inventory.setItem(
                  slot,
                  Items.create(
                     material(plugin, "home.slot-empty-material", Material.GRAY_BED),
                     plugin.messages().raw("home.slot-empty-name", "slot", slotNumber),
                     plugin.messages().rawList("home.slot-empty-lore", "slot", slotNumber)
                  )
               );
            } else {
               inventory.setItem(
                  slot,
                  Items.create(
                     material(plugin, "home.slot-home-material", Material.RED_BED),
                     plugin.messages().raw("home.slot-home-name", "home", home.name(), "slot", slotNumber),
                     plugin.messages()
                        .rawList(
                           "home.slot-home-lore",
                           "home",
                           home.name(),
                           "slot",
                           slotNumber,
                           "world",
                           String.valueOf(home.world()),
                           "coords",
                           home.coordinateText()
                        )
                  )
               );
            }
         }
      }

      inventory.setItem(
         slotInfo(plugin),
         Items.create(
            material(plugin, "home.info-material", Material.BOOK),
            plugin.messages()
               .raw("home.info-name", "used", Integer.toString(data.used()), "slots", Integer.toString(data.unlocked()), "max", Integer.toString(maxSlots)),
            plugin.messages()
               .rawList("home.info-lore", "used", Integer.toString(data.used()), "slots", Integer.toString(data.unlocked()), "max", Integer.toString(maxSlots))
         )
      );
      inventory.setItem(slotClose(plugin), GuiKit.nav(plugin, gui(plugin), "close", Material.BARRIER, "home.close-name", "home.close-lore"));
   }

   private static void decorate(W2NSMP plugin, Inventory inventory) {
      int start = homeSlotStart(plugin);
      GuiKit.shell(
         plugin,
         gui(plugin),
         inventory,
         Material.GRAY_STAINED_GLASS_PANE,
         "home.filler-material",
         Material.BLACK_STAINED_GLASS_PANE,
         slot -> slot >= start && slot < start + 8 || slot == slotInfo(plugin) || slot == slotClose(plugin)
      );
   }

   public static void closeAll(W2NSMP plugin) {
      for (Player player : Bukkit.getOnlinePlayers()) {
         InventoryView view = player.getOpenInventory();
         if (view != null && view.getTopInventory().getHolder() instanceof HomeMenuHolder) {
            player.closeInventory();
         }
      }
   }

   private static Material material(W2NSMP plugin, String key, Material fallback) {
      return GuiKit.material(plugin, key, fallback);
   }
}
