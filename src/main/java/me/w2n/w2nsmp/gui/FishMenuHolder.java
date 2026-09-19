package me.w2n.w2nsmp.gui;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Pemegang inventory Fishing Hub /fish (v1.4.1).
 *
 * <p>Satu holder untuk dua halaman: {@code MODE_HUB} (menu utama /fish) dan
 * {@code MODE_GALLERY} (galeri ikan, berhalaman). Pola pemilik + beginProcessing/pending
 * sama dengan holder GUI lain agar dupe-klik dan klik pemain lain tertahan.
 */
public final class FishMenuHolder implements InventoryHolder {
   public static final String MODE_HUB = "hub";
   public static final String MODE_GALLERY = "gallery";
   /** v1.8.0 (PHASE 4): halaman Fish Storage - isi Fish Inventory pemain. */
   public static final String MODE_STORAGE = "storage";

   private final UUID owner;
   private Inventory inventory;
   private boolean processing;
   private String pending;
   private String mode = MODE_HUB;
   private int page;
   /** Batas waktu konfirmasi upgrade kapasitas (0 = tidak ada konfirmasi tertunda). */
   private long confirmUntil;

   public FishMenuHolder(UUID owner) {
      this.owner = owner;
   }

   public UUID owner() {
      return this.owner;
   }

   public boolean isOwner(Player player) {
      return player != null && player.getUniqueId().equals(this.owner);
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

   public String mode() {
      return this.mode;
   }

   public void mode(String mode) {
      if (MODE_GALLERY.equals(mode) || MODE_STORAGE.equals(mode)) {
         this.mode = mode;
      } else {
         this.mode = MODE_HUB;
      }
   }

   public long confirmUntil() {
      return this.confirmUntil;
   }

   public void confirmUntil(long confirmUntil) {
      this.confirmUntil = Math.max(0L, confirmUntil);
   }

   public int page() {
      return this.page;
   }

   public void page(int page) {
      this.page = Math.max(0, page);
   }

   public void setInventory(Inventory inventory) {
      this.inventory = inventory;
   }

   @Override
   public Inventory getInventory() {
      return this.inventory;
   }
}
