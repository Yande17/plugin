package me.w2n.w2nsmp.worth;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.utility.Items;
import me.w2n.w2nsmp.utility.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

public final class WorthService {
   private static final int PLAYER_INVENTORY_SLOTS = 36;
   private final W2NSMP plugin;
   private final WorthLore lore;
   private final WorthPreferences preferences;
   private final Map<UUID, long[]> fingerprints = new HashMap<>();
   private final Map<UUID, Boolean> loreStates = new HashMap<>();
   private final Map<UUID, Long> lastRefresh = new HashMap<>();
   private BukkitTask task;
   private long appliedTotal;
   private long strippedTotal;
   private long skippedTotal;
   private long mergedTotal;

   public WorthService(W2NSMP plugin) {
      super();
      this.plugin = plugin;
      this.lore = new WorthLore(plugin);
      this.preferences = new WorthPreferences(plugin);
   }

   public void load() {
      this.preferences.load();
      this.plugin
         .getLogger()
         .info(
            "Worth: lore harga "
               + (this.plugin.config().worthInventoryLore() ? "aktif" : "nonaktif")
               + ", "
               + this.preferences.disabledCount()
               + " pemain mematikannya, pemeriksaan tiap "
               + this.plugin.config().worthRefreshSeconds()
               + " detik."
         );
      this.startTask();
   }

   public void reload() {
      if (this.preferences.isDirty()) {
         this.preferences.saveNow();
      }

      this.preferences.load();
      this.fingerprints.clear();
      this.loreStates.clear();
      this.lastRefresh.clear();
      this.startTask();

      for (Player player : Bukkit.getOnlinePlayers()) {
         try {
            if (this.plugin.config().worthInventoryLore()) {
               this.refresh(player);
            } else {
               this.clear(player);
            }
         } catch (RuntimeException exception) {
            this.plugin.getLogger().warning("Gagal menyiapkan lore harga " + player.getName() + ": " + exception);
         }
      }
   }

   public void shutdown() {
      if (this.task != null) {
         this.task.cancel();
         this.task = null;
      }

      this.preferences.saveNow();
      this.fingerprints.clear();
      this.loreStates.clear();
      this.lastRefresh.clear();
   }

   private void startTask() {
      if (this.task != null) {
         this.task.cancel();
         this.task = null;
      }

      if (this.plugin.config().worthEnabled() && this.plugin.config().worthInventoryLore()) {
         int seconds = this.plugin.config().worthRefreshSeconds();
         if (seconds > 0) {
            this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tickRefresh, 40L, 20L);
         }
      }
   }

   private void tickRefresh() {
      if (this.plugin.config().worthInventoryLore()) {
         long now = System.currentTimeMillis();
         long interval = Math.max(1, this.plugin.config().worthRefreshSeconds()) * 1000L;

         for (Player player : Bukkit.getOnlinePlayers()) {
            Long last = this.lastRefresh.get(player.getUniqueId());
            if (last == null || now - last >= interval) {
               this.lastRefresh.put(player.getUniqueId(), now);

               try {
                  this.refresh(player);
               } catch (RuntimeException exception) {
                  this.plugin.getLogger().warning("Gagal memperbarui lore harga " + player.getName() + ": " + exception);
               }
            }
         }
      }
   }

   public WorthLore lore() {
      return this.lore;
   }

   public WorthPreferences preferences() {
      return this.preferences;
   }

   public boolean enabled() {
      return this.plugin.config().worthEnabled();
   }

   public boolean isMarked(ItemStack stack) {
      return this.lore.isMarked(stack);
   }

   public long markedPrice(ItemStack stack) {
      return this.lore.markedPrice(stack);
   }

   public int unitPrice(Material material) {
      if (material == null || material.isAir()) {
         return 0;
      } else if (!this.plugin.config().worthEnabled()) {
         return 0;
      } else {
         return this.plugin.dynamic() == null ? this.plugin.sell().prices().price(material) : this.plugin.dynamic().currentPrice(material);
      }
   }

   public long stackTotal(ItemStack stack) {
      return Items.isEmpty(stack) ? 0L : (long)this.unitPrice(stack.getType()) * stack.getAmount();
   }

   public boolean isSellable(ItemStack stack) {
      return !Items.isEmpty(stack) && this.unitPrice(stack.getType()) > 0;
   }

   public List<Component> loreLines(ItemStack stack) {
      if (!Items.isEmpty(stack) && this.enabled()) {
         int unit = this.unitPrice(stack.getType());
         if (unit > 0) {
            List<Component> lines = new ArrayList<>(2);
            lines.add(Text.color(this.plugin.messages().raw("worth.lore-unit", "unit", this.plugin.economy().format(unit))));
            if (this.plugin.config().worthShowTotal() && stack.getAmount() > 1) {
               lines.add(
                  Text.color(
                     this.plugin
                        .messages()
                        .raw("worth.lore-total", "amount", Integer.toString(stack.getAmount()), "total", this.plugin.economy().format(this.stackTotal(stack)))
                  )
               );
            }

            return lines;
         } else {
            return !this.plugin.config().worthShowUnsellable() ? List.of() : List.of(Text.color(this.plugin.messages().raw("worth.lore-unsellable")));
         }
      } else {
         return List.of();
      }
   }

   public ItemStack decorate(ItemStack stack) {
      return this.decorate(stack, null);
   }

   public ItemStack decorate(ItemStack stack, Player player) {
      if (Items.isEmpty(stack) || !this.enabled()) {
         return stack;
      } else if (player != null && !this.isLoreEnabledFor(player)) {
         return this.lore.isMarked(stack) ? this.lore.strip(stack) : stack;
      } else if (this.plugin.config().worthBlacklist().contains(stack.getType())) {
         return stack;
      } else {
         long price = this.unitPrice(stack.getType());
         List<Component> lines = this.loreLines(stack);
         if (lines.isEmpty()) {
            return this.lore.isMarked(stack) ? this.lore.strip(stack) : stack;
         } else {
            return this.lore.matches(stack, price, lines) ? stack : this.lore.apply(stack, price, lines, this.plugin.config().worthLoreAtTop());
         }
      }
   }

   public int refresh(Player player) {
      Inventory inventory = player.getInventory();
      UUID uniqueId = player.getUniqueId();
      if (this.mergeStacks(player) > 0) {
         this.fingerprints.remove(uniqueId);
      }

      long[] previous = this.fingerprints.get(uniqueId);
      long[] current = new long[inventory.getSize()];
      boolean loreWanted = this.isLoreEnabledFor(player);
      Boolean previousState = this.loreStates.get(uniqueId);
      boolean force = previousState == null || previousState != loreWanted;
      int changed = 0;

      for (int slot = 0; slot < inventory.getSize(); slot++) {
         ItemStack stack = inventory.getItem(slot);
         long fingerprint = fingerprint(stack);
         current[slot] = fingerprint;
         if (force || previous == null || previous[slot] != fingerprint) {
            WorthService.SlotResult result = this.applyToSlot(player, inventory, slot, stack);
            if (result == WorthService.SlotResult.APPLIED || result == WorthService.SlotResult.STRIPPED) {
               changed++;
               current[slot] = fingerprint(inventory.getItem(slot));
            }
         }
      }

      this.fingerprints.put(uniqueId, current);
      this.loreStates.put(uniqueId, loreWanted);
      return changed;
   }

   public int mergeStacks(Player player) {
      if (player != null && this.enabled() && this.plugin.config().worthMergeStacks()) {
         Inventory inventory = player.getInventory();
         int limit = Math.min(inventory.getSize(), 36);
         Map<Material, List<Integer>> slotsByType = new EnumMap<>(Material.class);

         for (int slot = 0; slot < limit; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!Items.isEmpty(stack) && stack.getMaxStackSize() > 1) {
               slotsByType.computeIfAbsent(stack.getType(), material -> new ArrayList<>(4)).add(slot);
            }
         }

         int merged = 0;

         for (List<Integer> slots : slotsByType.values()) {
            if (slots.size() >= 2) {
               for (int left = 0; left < slots.size(); left++) {
                  for (int right = left + 1; right < slots.size(); right++) {
                     if (this.mergePair(player, inventory, slots.get(left), slots.get(right))) {
                        merged++;
                     }
                  }
               }
            }
         }

         if (merged > 0) {
            this.mergedTotal += merged;
         }

         return merged;
      } else {
         return 0;
      }
   }

   private boolean mergePair(Player player, Inventory inventory, int targetSlot, int sourceSlot) {
      ItemStack target = inventory.getItem(targetSlot);
      ItemStack source = inventory.getItem(sourceSlot);
      if (!Items.isEmpty(target) && !Items.isEmpty(source)) {
         int free = target.getMaxStackSize() - target.getAmount();
         if (free <= 0) {
            return false;
         }

         if (!this.mergeable(target, source)) {
            return false;
         }

         int moved = Math.min(free, source.getAmount());
         if (moved <= 0) {
            return false;
         }

         target.setAmount(target.getAmount() + moved);
         inventory.setItem(targetSlot, target);
         int remainder = source.getAmount() - moved;
         inventory.setItem(sourceSlot, remainder <= 0 ? null : source.clone().asQuantity(remainder));
         this.plugin.debug("WORTH gabung " + player.getName() + ": " + moved + "x " + target.getType() + " slot " + sourceSlot + " -> " + targetSlot);
         return true;
      } else {
         return false;
      }
   }

   private boolean mergeable(ItemStack first, ItemStack second) {
      boolean firstMarked = this.lore.isMarked(first);
      boolean secondMarked = this.lore.isMarked(second);
      return firstMarked == secondMarked ? false : this.lore.strip(first).isSimilar(this.lore.strip(second));
   }

   public WorthService.SlotResult applyToSlot(Player player, Inventory inventory, int slot, ItemStack stack) {
      if (Items.isEmpty(stack)) {
         return WorthService.SlotResult.EMPTY;
      }

      boolean loreEnabled = this.isLoreEnabledFor(player)
         && !this.plugin.config().worthBlacklist().contains(stack.getType())
         && (!this.plugin.config().worthSkipItemsWithCustomLore() || !this.hasForeignLore(stack));
      long price = loreEnabled ? this.unitPrice(stack.getType()) : 0L;
      List<Component> lines = loreEnabled ? this.loreLines(stack) : List.of();
      if (lines.isEmpty()) {
         if (this.lore.isMarked(stack)) {
            inventory.setItem(slot, this.lore.strip(stack));
            this.strippedTotal++;
            return WorthService.SlotResult.STRIPPED;
         } else {
            this.skippedTotal++;
            return WorthService.SlotResult.UNCHANGED;
         }
      } else {
         if (this.lore.matches(stack, price, lines)) {
            return WorthService.SlotResult.UNCHANGED;
         }

         inventory.setItem(slot, this.lore.apply(stack, price, lines, this.plugin.config().worthLoreAtTop()));
         this.appliedTotal++;
         return WorthService.SlotResult.APPLIED;
      }
   }

   public boolean isLoreEnabledFor(Player player) {
      return this.enabled() && this.plugin.config().worthInventoryLore()
         ? !this.plugin.config().worthPersonalToggle() || !this.preferences.isDisabled(player.getUniqueId())
         : false;
   }

   public boolean setPersonal(Player player, boolean enabled) {
      if (!this.plugin.config().worthPersonalToggle()) {
         return false;
      }

      if (!this.preferences.setDisabled(player.getUniqueId(), !enabled)) {
         return false;
      }

      this.refresh(player);
      return true;
   }

   public int clear(Player player) {
      Inventory inventory = player.getInventory();
      int cleared = 0;

      for (int slot = 0; slot < inventory.getSize(); slot++) {
         ItemStack stack = inventory.getItem(slot);
         if (!Items.isEmpty(stack) && this.lore.isMarked(stack)) {
            inventory.setItem(slot, this.lore.strip(stack));
            cleared++;
            this.strippedTotal++;
         }
      }

      long[] fingerprint = new long[inventory.getSize()];

      for (int slot = 0; slot < inventory.getSize(); slot++) {
         fingerprint[slot] = fingerprint(inventory.getItem(slot));
      }

      this.fingerprints.put(player.getUniqueId(), fingerprint);
      this.loreStates.put(player.getUniqueId(), this.isLoreEnabledFor(player));
      return cleared;
   }

   public int clearAll() {
      int cleared = 0;

      for (Player player : Bukkit.getOnlinePlayers()) {
         cleared += this.clear(player);
      }

      return cleared;
   }

   public int markedSlots(Player player) {
      Inventory inventory = player.getInventory();
      int marked = 0;

      for (int slot = 0; slot < inventory.getSize(); slot++) {
         if (this.lore.isMarked(inventory.getItem(slot))) {
            marked++;
         }
      }

      return marked;
   }

   public long appliedTotal() {
      return this.appliedTotal;
   }

   public long strippedTotal() {
      return this.strippedTotal;
   }

   public long mergedTotal() {
      return this.mergedTotal;
   }

   public long skippedTotal() {
      return this.skippedTotal;
   }

   public boolean taskRunning() {
      return this.task != null && !this.task.isCancelled();
   }

   public int trackedPlayers() {
      return this.fingerprints.size();
   }

   public void forget(UUID uniqueId) {
      this.fingerprints.remove(uniqueId);
      this.loreStates.remove(uniqueId);
      this.lastRefresh.remove(uniqueId);
   }

   private boolean hasForeignLore(ItemStack stack) {
      ItemMeta meta = stack.getItemMeta();
      if (meta != null && meta.lore() != null && !meta.lore().isEmpty()) {
         Set<String> ours = new HashSet<>(this.lore.markedLines(stack));
         LegacyComponentSerializer serializer = LegacyComponentSerializer.legacySection();

         for (Component line : meta.lore()) {
            if (!ours.contains(serializer.serialize(line))) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static long fingerprint(ItemStack stack) {
      if (Items.isEmpty(stack)) {
         return 0L;
      }

      long value = stack.getType().ordinal();
      value = value * 128L + Math.min(stack.getAmount(), 127);
      return value * 2L + (stack.hasItemMeta() ? 1L : 0L);
   }

   public enum SlotResult {
      APPLIED,
      STRIPPED,
      UNCHANGED,
      EMPTY;
   }
}
