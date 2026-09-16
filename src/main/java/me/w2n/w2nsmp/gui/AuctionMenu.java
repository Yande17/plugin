package me.w2n.w2nsmp.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.auction.AuctionListing;
import me.w2n.w2nsmp.auction.AuctionManager;
import me.w2n.w2nsmp.config.GuiConfig;
import me.w2n.w2nsmp.utility.GuiKit;
import me.w2n.w2nsmp.utility.Items;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class AuctionMenu {
   private AuctionMenu() {
   }

   public static GuiConfig gui(W2NSMP plugin) {
      return plugin.guiConfigs().auction();
   }

   private static int button(W2NSMP plugin, String path, int fallback, int size) {
      int configured = gui(plugin).slot(path, fallback);
      if (configured >= size - 9 && configured < size) {
         return configured;
      }

      plugin.getLogger().warning("gui/auction.yml: slot " + path + "=" + configured + " berada di luar baris tombol; memakai " + fallback + ".");
      return fallback;
   }

   public static int slotPrev(W2NSMP plugin, int size) {
      return button(plugin, "buttons.prev.slot", size - 9, size);
   }

   public static int slotCollect(W2NSMP plugin, int size) {
      return button(plugin, "buttons.collect.slot", size - 7, size);
   }

   public static int slotInfo(W2NSMP plugin, int size) {
      return button(plugin, "buttons.info.slot", size - 5, size);
   }

   public static int slotClose(W2NSMP plugin, int size) {
      return button(plugin, "buttons.close.slot", size - 3, size);
   }

   public static int slotNext(W2NSMP plugin, int size) {
      return button(plugin, "buttons.next.slot", size - 1, size);
   }

   public static int size(W2NSMP plugin) {
      return Math.clamp(plugin.config().auctionRows(), 3, 6) * 9;
   }

   public static int pageSize(W2NSMP plugin) {
      int pageSize = plugin.config().auctionPageSize();
      int maxPageSize = size(plugin) - 9;
      if (pageSize <= 0) {
         pageSize = maxPageSize;
      }

      return Math.clamp(pageSize, 1, maxPageSize);
   }

   public static int slotPrev(int size) {
      return size - 9;
   }

   public static int slotCollect(int size) {
      return size - 7;
   }

   public static int slotInfo(int size) {
      return size - 5;
   }

   public static int slotClose(int size) {
      return size - 3;
   }

   public static int slotNext(int size) {
      return size - 1;
   }

   public static void open(W2NSMP plugin, Player player, int page, UUID sellerFilter) {
      int size = size(plugin);
      int pageSize = pageSize(plugin);
      AuctionMenuHolder holder = new AuctionMenuHolder(player.getUniqueId(), Math.max(1, page), pageSize, sellerFilter);
      int pages = Math.max(1, (int)Math.ceil((double)plugin.auction().listings().size() / pageSize));
      int shown = Math.min(holder.page(), pages);
      Inventory inventory = Bukkit.createInventory(
         holder,
         size,
         gui(plugin)
            .title(
               "auction.gui-title",
               "page",
               Integer.toString(shown),
               "pages",
               Integer.toString(pages),
               "count",
               Integer.toString(plugin.auction().listings().size())
            )
      );
      holder.setInventory(inventory);
      decorate(plugin, inventory, size);
      render(plugin, player, inventory, shown, pageSize, sellerFilter);
      player.openInventory(inventory);
      plugin.guiSounds().play(player, gui(plugin), "open");
   }

   public static void render(W2NSMP plugin, Player player, Inventory inventory, int page, int pageSize, UUID sellerFilter) {
      List<AuctionListing> all = filter(plugin, sellerFilter);
      long now = System.currentTimeMillis();

      for (int index = 0; index < pageSize; index++) {
         inventory.setItem(index, null);
      }

      int start = (page - 1) * pageSize;
      List<Integer> shownIds = new ArrayList<>(pageSize);

      for (int index = 0; index < pageSize; index++) {
         int position = start + index;
         if (position >= all.size()) {
            break;
         }

         AuctionListing listing = all.get(position);
         shownIds.add(listing.id());
         inventory.setItem(index, icon(plugin, player, listing, now));
      }

      if (inventory.getHolder() instanceof AuctionMenuHolder holder) {
         holder.listingIds(shownIds);
      }

      if (shownIds.isEmpty()) {
         inventory.setItem(
            0,
            Items.create(
               material(plugin, "auction.empty-material", Material.BARRIER),
               plugin.messages().raw("auction.empty-name"),
               plugin.messages().rawList("auction.empty-lore")
            )
         );
      }

      int size = inventory.getSize();
      int pages = Math.max(1, (int)Math.ceil((double)all.size() / pageSize));
      inventory.setItem(
         slotInfo(plugin, size),
         Items.create(
            material(plugin, "auction.info-material", Material.BOOK),
            plugin.messages().raw("auction.info-name", "page", Integer.toString(page), "pages", Integer.toString(pages)),
            plugin.messages()
               .rawList(
                  "auction.info-lore",
                  "page",
                  Integer.toString(page),
                  "pages",
                  Integer.toString(pages),
                  "count",
                  Integer.toString(all.size()),
                  "mailbox",
                  Integer.toString(plugin.auction().mailboxCount(player.getUniqueId())),
                  "limit",
                  Integer.toString(plugin.auction().maxListings(player)),
                  "mine",
                  Integer.toString(plugin.auction().countOf(player.getUniqueId())),
                  "tax",
                  Integer.toString(plugin.config().auctionTaxPercent())
               )
         )
      );
      if (page > 1) {
         inventory.setItem(
            slotPrev(plugin, size),
            GuiKit.nav(
               plugin,
               gui(plugin),
               "prev",
               Material.ARROW,
               "auction.prev-name",
               "auction.prev-lore",
               "page",
               Integer.toString(page - 1),
               "pages",
               Integer.toString(pages)
            )
         );
      }

      if (page < pages) {
         inventory.setItem(
            slotNext(plugin, size),
            GuiKit.nav(
               plugin,
               gui(plugin),
               "next",
               Material.ARROW,
               "auction.next-name",
               "auction.next-lore",
               "page",
               Integer.toString(page + 1),
               "pages",
               Integer.toString(pages)
            )
         );
      }

      int mailbox = plugin.auction().mailboxCount(player.getUniqueId());
      inventory.setItem(
         slotCollect(plugin, size),
         Items.create(
            material(plugin, "auction.collect-material", Material.CHEST),
            plugin.messages().raw("auction.collect-name", "amount", Integer.toString(mailbox)),
            plugin.messages().rawList(mailbox > 0 ? "auction.collect-lore" : "auction.collect-lore-empty", "amount", Integer.toString(mailbox))
         )
      );
      inventory.setItem(slotClose(plugin, size), GuiKit.nav(plugin, gui(plugin), "close", Material.BARRIER, "auction.close-name", "auction.close-lore"));
   }

   private static ItemStack icon(W2NSMP plugin, Player player, AuctionListing listing, long now) {
      ItemStack stack = listing.item();
      ItemMeta meta = stack.getItemMeta();
      if (meta == null) {
         return stack;
      }

      boolean own = listing.sellerId().equals(player.getUniqueId());
      String price = plugin.economy().format(listing.price());
      String net = plugin.economy().format(plugin.auction().netFor(listing.price()));
      String tax = plugin.economy().format(plugin.auction().taxFor(listing.price()));
      String[] placeholders = new String[]{
         "id",
         Integer.toString(listing.id()),
         "price",
         price,
         "net",
         net,
         "tax",
         tax,
         "seller",
         listing.sellerName(),
         "amount",
         Integer.toString(stack.getAmount()),
         "item",
         AuctionManager.itemName(stack),
         "time",
         remainingText(listing.remainingMillis(now)),
         "own",
         own ? plugin.messages().raw("auction.own-marker") : ""
      };
      meta.displayName(plugin.messages().component("auction.listing-name", placeholders));
      List<String> lore = new ArrayList<>(plugin.messages().rawList("auction.listing-lore", placeholders));
      if (own) {
         lore.addAll(plugin.messages().rawList("auction.listing-own-lore", placeholders));
      }

      meta.lore(plugin.messages().components(lore, placeholders));
      stack.setItemMeta(meta);
      return stack;
   }

   public static String remainingText(long millis) {
      long totalMinutes = Math.max(0L, millis) / 60000L;
      return totalMinutes / 60L + "j " + String.format("%02d", totalMinutes % 60L) + "m";
   }

   public static List<AuctionListing> filter(W2NSMP plugin, UUID sellerFilter) {
      return sellerFilter == null ? plugin.auction().listings() : plugin.auction().listingsOf(sellerFilter);
   }

   private static void decorate(W2NSMP plugin, Inventory inventory, int size) {
      GuiConfig gui = gui(plugin);
      String fillerName = gui.fillerName();
      ItemStack filler = GuiKit.filler(plugin, gui, Material.GRAY_STAINED_GLASS_PANE, "auction.filler-material");

      for (int slot = size - 9; slot < size; slot++) {
         inventory.setItem(slot, filler);
      }
   }

   public static void closeAll(W2NSMP plugin) {
      for (Player player : Bukkit.getOnlinePlayers()) {
         InventoryView view = player.getOpenInventory();
         if (view != null && view.getTopInventory().getHolder() instanceof AuctionMenuHolder) {
            player.closeInventory();
         }
      }
   }

   private static Material material(W2NSMP plugin, String key, Material fallback) {
      return GuiKit.material(plugin, key, fallback);
   }
}
