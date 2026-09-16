package me.w2n.w2nsmp.gui;

import java.util.UUID;
import me.w2n.w2nsmp.skill.SkillType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Pemegang inventory menu <b>jalur progres (snake path)</b> satu skill (v1.4.0).
 *
 * <p>Menyimpan skill dan halaman yang sedang dilihat. Pola {@code beginProcessing}/{@code pending}
 * meniru {@link SkillMenuHolder} untuk menahan klik ganda; halaman bisa diubah tanpa membuat
 * inventory baru (isi digambar ulang di tempat).
 */
public final class SkillPathMenuHolder implements InventoryHolder {
   private final UUID owner;
   private final SkillType type;
   private int page;
   private Inventory inventory;
   private boolean processing;
   private String pending;

   public SkillPathMenuHolder(UUID owner, SkillType type, int page) {
      this.owner = owner;
      this.type = type;
      this.page = Math.max(0, page);
   }

   public UUID owner() {
      return this.owner;
   }

   /** Skill yang sedang ditampilkan menu ini. */
   public SkillType type() {
      return this.type;
   }

   /** Halaman jalur yang sedang terlihat (0-based). */
   public int page() {
      return this.page;
   }

   public void page(int page) {
      this.page = Math.max(0, page);
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
