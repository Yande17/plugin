package me.w2n.w2nsmp.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.bounty.BountyEntry;
import me.w2n.w2nsmp.config.GuiConfig;
import me.w2n.w2nsmp.utility.GuiKit;
import me.w2n.w2nsmp.utility.Items;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public final class BountyMenu {
   public static final int SIZE = 54;
   public static final int ENTRY_START = 0;
   public static final int SLOT_PREV = 45;
   public static final int SLOT_TARGETS = 47;
   public static final int SLOT_INFO = 49;
   public static final int SLOT_CLOSE = 51;
   public static final int SLOT_NEXT = 53;

   private BountyMenu() {
   }

   public static GuiConfig gui(W2NSMP plugin) {
      return plugin.guiConfigs().bounty();
   }

   public static int size(W2NSMP plugin) {
      int configured = gui(plugin).size(54);
      if (configured < 27 || configured % 9 != 0) {
         plugin.getLogger().warning("gui/bounty.yml: size=" + configured + " tidak wajar (harus kelipatan 9, minimal 27); memakai 54.");
         configured = 54;
      }

      return configured;
   }

   public static int entryStart(W2NSMP plugin) {
      int start = gui(plugin).slot("entries-start", 0);
      return start >= 0 && start < size(plugin) ? start : 0;
   }

   public static int pageSize(W2NSMP plugin) {
      int rows = size(plugin) / 9 - 1;
      int capacity = Math.max(1, rows * 9 - entryStart(plugin));
      int slots = gui(plugin).slot("entries-per-page", 0);
      int wanted = slots > 0 ? slots : plugin.bounty().pageSize();
      return Math.max(1, Math.min(capacity, wanted));
   }

   public static int slot(W2NSMP plugin, String key, int fallback) {
      int configured = gui(plugin).slot("buttons." + key + ".slot", fallback);
      if (configured >= 0 && configured < size(plugin)) {
         return configured;
      }

      plugin.getLogger()
         .warning("gui/bounty.yml: slot tombol " + key + "=" + configured + " di luar ukuran GUI " + size(plugin) + "; memakai " + fallback + ".");
      return fallback;
   }

   public static int slotPrev(W2NSMP plugin) {
      return slot(plugin, "prev", 45);
   }

   public static int slotTargets(W2NSMP plugin) {
      return slot(plugin, "targets", 47);
   }

   public static int slotInfo(W2NSMP plugin) {
      return slot(plugin, "info", 49);
   }

   public static int slotClose(W2NSMP plugin) {
      return slot(plugin, "close", 51);
   }

   public static int slotNext(W2NSMP plugin) {
      return slot(plugin, "next", 53);
   }

   public static void open(W2NSMP plugin, Player player) {
      open(plugin, player, BountyMenuHolder.Mode.TOP);
   }

   public static void open(W2NSMP plugin, Player player, BountyMenuHolder.Mode mode) {
      BountyMenuHolder holder = new BountyMenuHolder(player.getUniqueId(), mode);
      Inventory inventory = Bukkit.createInventory(holder, size(plugin), title(plugin, holder));
      holder.setInventory(inventory);
      refresh(plugin, player, inventory);
      player.openInventory(inventory);
      plugin.guiSounds().play(player, gui(plugin), "open");
   }

   public static void refresh(W2NSMP plugin, Player player, Inventory inventory) {
      if (inventory.getHolder() instanceof BountyMenuHolder holder) {
         decorate(plugin, inventory);
         switch (holder.mode()) {
            case TARGETS:
               renderTargets(plugin, inventory, holder);
               break;
            case AMOUNT:
               renderAmounts(plugin, inventory, holder);
               break;
            default:
               renderTop(plugin, inventory, holder);
         }

         renderButtons(plugin, inventory, holder);
      }
   }

   public static Component title(W2NSMP plugin, BountyMenuHolder holder) {
      String key = switch (holder.mode()) {
         case TARGETS -> "bounty.gui-targets-title";
         case AMOUNT -> "bounty.gui-amount-title";
         default -> "bounty.gui-title";
      };
      return gui(plugin)
         .title(
            key,
            "page",
            Integer.toString(holder.page()),
            "pages",
            Integer.toString(pages(plugin, holder)),
            "player",
            holder.subjectName() == null ? "-" : holder.subjectName(),
            "total",
            plugin.economy() == null ? Long.toString(plugin.bounty().total()) : plugin.economy().format(plugin.bounty().total()),
            "bounty",
            plugin.economy() == null ? Long.toString(plugin.bounty().bounty(holder.subject())) : plugin.bounty().formatted(holder.subject())
         );
   }

   private static void renderTop(W2NSMP plugin, Inventory inventory, BountyMenuHolder holder) {
      List<BountyEntry> entries = plugin.bounty().top();
      int perPage = pageSize(plugin);
      int from = Math.min(entries.size(), (holder.page() - 1) * perPage);
      int start = entryStart(plugin);

      for (int index = 0; index < perPage; index++) {
         int position = from + index;
         if (position >= entries.size()) {
            break;
         }

         BountyEntry entry = entries.get(position);
         String name = entry.displayName(entry.uniqueId().toString());
         String amountText = plugin.economy() != null && plugin.economy().isEnabled() ? plugin.economy().format(entry.amount()) : Long.toString(entry.amount());
         ItemStack item = Items.create(
            material(plugin, "bounty.gui-entry-material", Material.PLAYER_HEAD),
            plugin.messages().raw("bounty.gui-entry-name", "rank", Integer.toString(position + 1), "player", name, "target", name, "amount", amountText),
            plugin.messages()
               .rawList(
                  "bounty.gui-entry-lore",
                  "rank",
                  Integer.toString(position + 1),
                  "player",
                  name,
                  "target",
                  name,
                  "amount",
                  amountText,
                  "total",
                  Integer.toString(entries.size())
               )
         );
         inventory.setItem(start + index, GuiKit.head(item, entry.uniqueId()));
      }

      if (entries.isEmpty()) {
         inventory.setItem(
            start,
            Items.create(
               material(plugin, "bounty.gui-empty-material", Material.LIGHT_GRAY_DYE),
               plugin.messages().raw("bounty.gui-empty-name"),
               plugin.messages().rawList("bounty.gui-empty-lore")
            )
         );
      }
   }

   private static void renderTargets(W2NSMP plugin, Inventory inventory, BountyMenuHolder holder) {
      List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
      players.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
      int perPage = pageSize(plugin);
      int from = Math.min(players.size(), (holder.page() - 1) * perPage);
      int start = entryStart(plugin);

      for (int index = 0; index < perPage; index++) {
         int position = from + index;
         if (position >= players.size()) {
            break;
         }

         Player target = players.get(position);
         long current = plugin.bounty().bounty(target.getUniqueId());
         String currentText = current <= 0L
            ? plugin.messages().raw("bounty.none")
            : (plugin.economy() == null ? Long.toString(current) : plugin.economy().format(current));
         ItemStack item = Items.create(
            material(plugin, "bounty.gui-target-material", Material.PLAYER_HEAD),
            plugin.messages().raw("bounty.gui-target-name", "player", target.getName(), "target", target.getName()),
            plugin.messages()
               .rawList("bounty.gui-target-lore", "player", target.getName(), "target", target.getName(), "current", currentText, "bounty", currentText)
         );
         inventory.setItem(start + index, head(item, target));
      }

      if (players.isEmpty()) {
         inventory.setItem(
            start,
            Items.create(
               material(plugin, "bounty.gui-empty-material", Material.LIGHT_GRAY_DYE),
               plugin.messages().raw("bounty.gui-empty-name"),
               plugin.messages().rawList("bounty.gui-empty-lore")
            )
         );
      }
   }

   private static void renderAmounts(W2NSMP plugin, Inventory inventory, BountyMenuHolder holder) {
      List<Long> presets = plugin.config().bountyPresets();
      int start = entryStart(plugin);
      String name = holder.subjectName() == null ? "-" : holder.subjectName();

      for (int index = 0; index < presets.size() && index < pageSize(plugin); index++) {
         long amount = presets.get(index);
         String amountText = plugin.economy() == null ? Long.toString(amount) : plugin.economy().format(amount);
         String currentText = plugin.bounty().formatted(holder.subject());
         inventory.setItem(
            start + index,
            Items.create(
               material(plugin, "bounty.gui-amount-material", Material.GOLD_INGOT),
               plugin.messages().raw("bounty.gui-amount-name", "amount", amountText, "player", name, "target", name),
               plugin.messages()
                  .rawList(
                     "bounty.gui-amount-lore",
                     "amount",
                     amountText,
                     "player",
                     name,
                     "target",
                     name,
                     "current",
                     currentText,
                     "bounty",
                     currentText,
                     "balance",
                     plugin.economy() == null ? "-" : plugin.economy().format(plugin.economy().balance(Bukkit.getOfflinePlayer(holder.owner())))
                  )
            )
         );
      }
   }

   private static void renderButtons(W2NSMP plugin, Inventory inventory, BountyMenuHolder holder) {
      int pages = pages(plugin, holder);
      boolean hasPrev = holder.page() > 1;
      boolean hasNext = holder.page() < pages;
      boolean amountMode = holder.mode() == BountyMenuHolder.Mode.AMOUNT;
      inventory.setItem(
         slotPrev(plugin),
         hasPrev && !amountMode
            ? GuiKit.nav(
               plugin,
               gui(plugin),
               "prev",
               Material.ARROW,
               "bounty.gui-prev-name",
               "bounty.gui-prev-lore",
               "page",
               Integer.toString(holder.page() - 1),
               "pages",
               Integer.toString(pages)
            )
            : null
      );
      inventory.setItem(
         slotTargets(plugin),
         Items.create(
            material(plugin, "bounty.gui-targets-material", Material.PLAYER_HEAD),
            plugin.messages()
               .raw(amountMode ? "bounty.gui-back-name" : (holder.mode() == BountyMenuHolder.Mode.TARGETS ? "bounty.gui-top-name" : "bounty.gui-targets-name")),
            plugin.messages()
               .rawList(
                  amountMode ? "bounty.gui-back-lore" : (holder.mode() == BountyMenuHolder.Mode.TARGETS ? "bounty.gui-top-lore" : "bounty.gui-targets-lore")
               )
         )
      );
      inventory.setItem(
         slotInfo(plugin),
         Items.create(
            material(plugin, "bounty.gui-info-material", Material.WRITABLE_BOOK),
            plugin.messages()
               .raw(
                  "bounty.gui-info-name",
                  "count",
                  Integer.toString(plugin.bounty().count()),
                  "total",
                  plugin.economy() == null ? Long.toString(plugin.bounty().total()) : plugin.economy().format(plugin.bounty().total())
               ),
            plugin.messages()
               .rawList(
                  "bounty.gui-info-lore",
                  "count",
                  Integer.toString(plugin.bounty().count()),
                  "total",
                  plugin.economy() == null ? Long.toString(plugin.bounty().total()) : plugin.economy().format(plugin.bounty().total()),
                  "own",
                  plugin.bounty().formatted(holder.owner()),
                  "minimum",
                  plugin.economy() == null ? Long.toString(plugin.config().bountyMinimum()) : plugin.economy().format(plugin.config().bountyMinimum()),
                  "maximum",
                  plugin.economy() == null ? Long.toString(plugin.config().bountyMaximum()) : plugin.economy().format(plugin.config().bountyMaximum())
               )
         )
      );
      inventory.setItem(slotClose(plugin), GuiKit.nav(plugin, gui(plugin), "close", Material.BARRIER, "bounty.gui-close-name", "bounty.gui-close-lore"));
      inventory.setItem(
         slotNext(plugin),
         hasNext && !amountMode
            ? GuiKit.nav(
               plugin,
               gui(plugin),
               "next",
               Material.ARROW,
               "bounty.gui-next-name",
               "bounty.gui-next-lore",
               "page",
               Integer.toString(holder.page() + 1),
               "pages",
               Integer.toString(pages)
            )
            : null
      );
   }

   public static int pages(W2NSMP plugin, BountyMenuHolder holder) {
      int perPage = pageSize(plugin);

      int total = switch (holder.mode()) {
         case TARGETS -> Bukkit.getOnlinePlayers().size();
         case AMOUNT -> plugin.config().bountyPresets().size();
         default -> plugin.bounty().top().size();
      };
      return Math.max(1, (int)Math.ceil((double)total / perPage));
   }

   public static void closeAll(W2NSMP plugin) {
      for (Player player : Bukkit.getOnlinePlayers()) {
         InventoryView view = player.getOpenInventory();
         if (view != null && view.getTopInventory().getHolder() instanceof BountyMenuHolder) {
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
         ItemStack filler = GuiKit.filler(plugin, gui, Material.GRAY_STAINED_GLASS_PANE, "bounty.gui-filler-material");

         for (int slot = 0; slot < size(plugin); slot++) {
            inventory.setItem(slot, filler);
         }
      }
   }

   public static ItemStack head(ItemStack item, Player target) {
      if (item != null && item.getType() == Material.PLAYER_HEAD && target != null) {
         if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(target);
            item.setItemMeta(meta);
         }

         return item;
      } else {
         return item;
      }
   }

   private static Material material(W2NSMP plugin, String key, Material fallback) {
      return GuiKit.material(plugin, key, fallback);
   }
}
