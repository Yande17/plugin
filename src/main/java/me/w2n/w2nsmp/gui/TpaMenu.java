package me.w2n.w2nsmp.gui;

import java.util.List;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.config.GuiConfig;
import me.w2n.w2nsmp.teleport.TpaRequest;
import me.w2n.w2nsmp.utility.GuiKit;
import me.w2n.w2nsmp.utility.Items;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

public final class TpaMenu {
   public static final int SIZE = 27;
   public static final int INCOMING_START = 0;
   public static final int OUTGOING_START = 9;
   public static final int PER_ROW = 9;
   public static final int SLOT_ACCEPT = 18;
   public static final int SLOT_DENY = 20;
   public static final int SLOT_CANCEL = 22;
   public static final int SLOT_REFRESH = 24;
   public static final int SLOT_CLOSE = 26;

   private TpaMenu() {
   }

   public static GuiConfig gui(W2NSMP plugin) {
      return plugin.guiConfigs().tpa();
   }

   public static int size(W2NSMP plugin) {
      return gui(plugin).size(27);
   }

   public static int incomingStart(W2NSMP plugin) {
      int start = gui(plugin).slot("incoming-start", 0);
      return start >= 0 && start + 9 <= size(plugin) ? start : 0;
   }

   public static int outgoingStart(W2NSMP plugin) {
      int start = gui(plugin).slot("outgoing-start", 9);
      return start >= 0 && start + 9 <= size(plugin) ? start : 9;
   }

   public static int slot(W2NSMP plugin, String key, int fallback) {
      int configured = gui(plugin).slot("buttons." + key + ".slot", fallback);
      if (configured >= 0 && configured < size(plugin)) {
         return configured;
      }

      plugin.getLogger().warning("gui/tpa.yml: slot tombol " + key + "=" + configured + " di luar ukuran GUI " + size(plugin) + "; memakai " + fallback + ".");
      return fallback;
   }

   public static int slotAccept(W2NSMP plugin) {
      return slot(plugin, "accept", 18);
   }

   public static int slotDeny(W2NSMP plugin) {
      return slot(plugin, "deny", 20);
   }

   public static int slotCancel(W2NSMP plugin) {
      return slot(plugin, "cancel", 22);
   }

   public static int slotRefresh(W2NSMP plugin) {
      return slot(plugin, "refresh", 24);
   }

   public static int slotClose(W2NSMP plugin) {
      return slot(plugin, "close", 26);
   }

   public static void open(W2NSMP plugin, Player player) {
      TpaMenuHolder holder = new TpaMenuHolder(player.getUniqueId());
      Inventory inventory = Bukkit.createInventory(
         holder,
         size(plugin),
         gui(plugin)
            .title(
               "tpa.gui-title",
               "incoming",
               Integer.toString(plugin.tpa().incomingCount(player.getUniqueId())),
               "outgoing",
               Integer.toString(plugin.tpa().outgoingCount(player.getUniqueId()))
            )
      );
      holder.setInventory(inventory);
      refresh(plugin, player, inventory);
      player.openInventory(inventory);
      plugin.guiSounds().play(player, gui(plugin), "open");
   }

   public static void refresh(W2NSMP plugin, Player player, Inventory inventory) {
      List<TpaRequest> incoming = plugin.tpa().incoming(player.getUniqueId());
      List<TpaRequest> outgoing = plugin.tpa().outgoing(player.getUniqueId());
      decorate(plugin, inventory);

      for (int index = 0; index < 9; index++) {
         int slot = incomingStart(plugin) + index;
         inventory.setItem(
            slot, index < incoming.size() ? incomingItem(plugin, incoming.get(index)) : (incoming.isEmpty() && index == 0 ? emptyItem(plugin) : null)
         );
      }

      for (int index = 0; index < 9; index++) {
         int slot = outgoingStart(plugin) + index;
         inventory.setItem(
            slot, index < outgoing.size() ? outgoingItem(plugin, outgoing.get(index)) : (outgoing.isEmpty() && index == 0 ? emptyItem(plugin) : null)
         );
      }

      inventory.setItem(
         slotAccept(plugin),
         Items.create(
            material(plugin, "tpa.gui-accept-material", Material.LIME_DYE),
            plugin.messages().raw("tpa.gui-accept-name", "count", Integer.toString(incoming.size())),
            plugin.messages()
               .rawList("tpa.gui-accept-lore", "count", Integer.toString(incoming.size()), "player", incoming.isEmpty() ? "-" : incoming.get(0).senderName())
         )
      );
      inventory.setItem(
         slotDeny(plugin),
         Items.create(
            material(plugin, "tpa.gui-deny-material", Material.RED_DYE),
            plugin.messages().raw("tpa.gui-deny-name", "count", Integer.toString(incoming.size())),
            plugin.messages()
               .rawList("tpa.gui-deny-lore", "count", Integer.toString(incoming.size()), "player", incoming.isEmpty() ? "-" : incoming.get(0).senderName())
         )
      );
      inventory.setItem(
         slotCancel(plugin),
         Items.create(
            material(plugin, "tpa.gui-cancel-material", Material.BARRIER),
            plugin.messages().raw("tpa.gui-cancel-name", "count", Integer.toString(outgoing.size())),
            plugin.messages()
               .rawList("tpa.gui-cancel-lore", "count", Integer.toString(outgoing.size()), "player", outgoing.isEmpty() ? "-" : outgoing.get(0).targetName())
         )
      );
      inventory.setItem(slotRefresh(plugin), GuiKit.nav(plugin, gui(plugin), "refresh", Material.COMPASS, "tpa.gui-refresh-name", "tpa.gui-refresh-lore"));
      inventory.setItem(slotClose(plugin), GuiKit.nav(plugin, gui(plugin), "close", Material.BARRIER, "tpa.gui-close-name", "tpa.gui-close-lore"));
   }

   public static ItemStack incomingItem(W2NSMP plugin, TpaRequest request) {
      return Items.create(
         material(plugin, "tpa.gui-incoming-material", Material.PLAYER_HEAD),
         plugin.messages().raw("tpa.gui-incoming-name", "player", request.senderName(), "target", request.targetName()),
         plugin.messages()
            .rawList(
               "tpa.gui-incoming-lore",
               "player",
               request.senderName(),
               "target",
               request.targetName(),
               "seconds",
               Integer.toString(request.remainingSeconds(System.currentTimeMillis())),
               "type",
               request.here() ? "tpahere" : "tpa"
            )
      );
   }

   public static ItemStack outgoingItem(W2NSMP plugin, TpaRequest request) {
      return Items.create(
         material(plugin, "tpa.gui-outgoing-material", Material.PAPER),
         plugin.messages().raw("tpa.gui-outgoing-name", "player", request.targetName(), "target", request.targetName()),
         plugin.messages()
            .rawList(
               "tpa.gui-outgoing-lore",
               "player",
               request.senderName(),
               "target",
               request.targetName(),
               "seconds",
               Integer.toString(request.remainingSeconds(System.currentTimeMillis())),
               "type",
               request.here() ? "tpahere" : "tpa"
            )
      );
   }

   public static ItemStack emptyItem(W2NSMP plugin) {
      return Items.create(
         material(plugin, "tpa.gui-empty-material", Material.LIGHT_GRAY_DYE),
         plugin.messages().raw("tpa.gui-empty-name"),
         plugin.messages().rawList("tpa.gui-empty-lore")
      );
   }

   public static void closeAll(W2NSMP plugin) {
      for (Player player : Bukkit.getOnlinePlayers()) {
         InventoryView view = player.getOpenInventory();
         if (view != null && view.getTopInventory().getHolder() instanceof TpaMenuHolder) {
            player.closeInventory();
         }
      }
   }

   private static void decorate(W2NSMP plugin, Inventory inventory) {
      GuiConfig gui = gui(plugin);
      if (!gui.fillerEnabled()) {
         for (int slot = 0; slot < size(plugin); slot++) {
            inventory.setItem(slot, null);
         }
      } else {
         ItemStack filler = GuiKit.filler(plugin, gui, Material.GRAY_STAINED_GLASS_PANE, "tpa.gui-filler-material");

         for (int slot = 0; slot < size(plugin); slot++) {
            inventory.setItem(slot, filler);
         }
      }
   }

   private static Material material(W2NSMP plugin, String key, Material fallback) {
      return GuiKit.material(plugin, key, fallback);
   }
}
