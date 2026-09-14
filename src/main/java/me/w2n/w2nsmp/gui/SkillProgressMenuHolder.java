package me.w2n.w2nsmp.gui;

import java.util.UUID;
import me.w2n.w2nsmp.skill.SkillType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Pemegang inventory menu <b>progres skill</b> (dibuka saat ikon skill di {@code /skill} diklik).
 *
 * <p>Holder ini menandai "inventory ini milik W2NSMP" sekaligus menyimpan skill mana yang sedang
 * dilihat, jadi {@code SkillGuiListener} tahu item mana yang boleh diklik dan tidak pernah
 * membatalkan klik di inventory plugin lain. Pola {@code beginProcessing}/{@code pending} meniru
 * {@link SkillMenuHolder} untuk menahan klik ganda.
 */
public final class SkillProgressMenuHolder implements InventoryHolder {
   private final UUID owner;
   private final SkillType type;
   private Inventory inventory;
   private boolean processing;
   private String pending;

   public SkillProgressMenuHolder(UUID owner, SkillType type) {
      this.owner = owner;
      this.type = type;
   }

   public UUID owner() {
      return this.owner;
   }

   /** Skill yang sedang ditampilkan menu ini. */
   public SkillType type() {
      return this.type;
   }

   public boolean isOwner(Player player) {
      return player != null && player.getUniqueId().equals(this.owner);
   }

   @Override
   public Inventory getInventory() {
      return this.inventory;
   }

   public void setInventory(Inventory inventory) {
      this.inventory = inventory;
   }

   public boolean beginProcessing() {
      if (this.processing) {
         return false;
      }

      this.processing = true;
      return true;
   }

   public void endProcessing() {
      this.processing = false;
   }

   public void pending(String key) {
      this.pending = key;
   }

   public String pending() {
      return this.pending;
   }
}
