package me.w2n.w2nsmp.auction;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuctionStorage {
   public static final String FILE_NAME = "auctions.yml";
   private final JavaPlugin plugin;
   private final File file;

   public AuctionStorage(JavaPlugin plugin) {
      super();
      this.plugin = plugin;
      this.file = new File(plugin.getDataFolder(), "auctions.yml");
   }

   public File file() {
      return this.file;
   }

   public AuctionStorage.Snapshot load() {
      Map<Integer, AuctionListing> listings = new HashMap<>();
      Map<UUID, List<ItemStack>> mailbox = new HashMap<>();
      if (!this.file.isFile()) {
         return new AuctionStorage.Snapshot(listings, mailbox, 1);
      }

      YamlConfiguration yaml = new YamlConfiguration();

      try {
         yaml.load(this.file);
      } catch (IOException | InvalidConfigurationException exception) {
         this.plugin.getLogger().log(Level.WARNING, "Gagal membaca auctions.yml (data auction TIDAK dimuat, file TIDAK ditimpa): " + exception.getMessage());
         return new AuctionStorage.Snapshot(listings, mailbox, 1);
      }

      ConfigurationSection listingSection = yaml.getConfigurationSection("listings");
      int highestId = 0;
      if (listingSection != null) {
         Iterator mailboxSection = listingSection.getKeys(false).iterator();

         label94:
         while (true) {
            String rawId;
            int id;
            while (true) {
               if (!mailboxSection.hasNext()) {
                  break label94;
               }

               rawId = (String)mailboxSection.next();

               try {
                  id = Integer.parseInt(rawId);
                  break;
               } catch (NumberFormatException exception) {
                  this.plugin.getLogger().warning("Lewati listing dengan id tidak valid: " + rawId);
               }
            }

            ConfigurationSection section = listingSection.getConfigurationSection(rawId);
            if (section != null) {
               ItemStack item = decode(section.getString("item"));
               if (item == null) {
                  this.plugin.getLogger().warning("Lewati listing #" + id + ": data item tidak terbaca.");
               } else {
                  String rawSeller = section.getString("seller");

                  UUID sellerId;
                  try {
                     sellerId = UUID.fromString(rawSeller == null ? "" : rawSeller);
                  } catch (IllegalArgumentException exception) {
                     this.plugin.getLogger().warning("Lewati listing #" + id + ": UUID penjual tidak valid.");
                     continue;
                  }

                  long now = System.currentTimeMillis();
                  listings.put(
                     id,
                     new AuctionListing(
                        id,
                        sellerId,
                        section.getString("seller-name", "?"),
                        item,
                        Math.max(0L, section.getLong("price", 0L)),
                        section.getLong("created-at", now),
                        section.getLong("expires-at", now)
                     )
                  );
                  highestId = Math.max(highestId, id);
               }
            }
         }
      }

      ConfigurationSection mailboxSection = yaml.getConfigurationSection("mailbox");
      if (mailboxSection != null) {
         Iterator var20 = mailboxSection.getKeys(false).iterator();

         label70:
         while (true) {
            String rawUuid;
            UUID uniqueId;
            while (true) {
               if (!var20.hasNext()) {
                  break label70;
               }

               rawUuid = (String)var20.next();

               try {
                  uniqueId = UUID.fromString(rawUuid);
                  break;
               } catch (IllegalArgumentException exception) {
                  this.plugin.getLogger().warning("Lewati kotak (mailbox) dengan UUID tidak valid: " + rawUuid);
               }
            }

            List<ItemStack> items = new ArrayList<>();

            for (String rawItem : mailboxSection.getStringList(rawUuid)) {
               ItemStack item = decode(rawItem);
               if (item != null) {
                  items.add(item);
               }
            }

            if (!items.isEmpty()) {
               mailbox.put(uniqueId, items);
            }
         }
      }

      int storedNextId = yaml.getInt("next-id", 0);
      return new AuctionStorage.Snapshot(listings, mailbox, Math.max(storedNextId, highestId + 1));
   }

   public String serialize(Map<Integer, AuctionListing> listings, Map<UUID, List<ItemStack>> mailbox, int nextId) {
      YamlConfiguration yaml = new YamlConfiguration();
      yaml.options()
         .setHeader(
            List.of(
               "Data auction house W2NSMP - jangan diedit saat server berjalan.",
               "Kunci data adalah UUID pemain; nama penjual hanya keterangan.",
               "Item disimpan sebagai Base64 dari serialisasi biner Paper (enchant & NBT ikut aman)."
            )
         );
      yaml.set("next-id", nextId);

      for (AuctionListing listing : listings.values()) {
         String base = "listings." + listing.id() + ".";
         yaml.set(base + "seller", listing.sellerId().toString());
         yaml.set(base + "seller-name", listing.sellerName());
         yaml.set(base + "price", listing.price());
         yaml.set(base + "created-at", listing.createdAt());
         yaml.set(base + "expires-at", listing.expiresAt());
         yaml.set(base + "item", encode(listing.item()));
      }

      for (Entry<UUID, List<ItemStack>> entry : mailbox.entrySet()) {
         if (!entry.getValue().isEmpty()) {
            List<String> encoded = new ArrayList<>(entry.getValue().size());

            for (ItemStack item : entry.getValue()) {
               encoded.add(encode(item));
            }

            yaml.set("mailbox." + entry.getKey(), encoded);
         }
      }

      return yaml.saveToString();
   }

   public boolean write(String content) {
      try {
         if (!this.plugin.getDataFolder().isDirectory() && !this.plugin.getDataFolder().mkdirs()) {
            this.plugin.getLogger().warning("Tidak bisa membuat folder data plugin.");
            return false;
         } else {
            Files.writeString(this.file.toPath(), content, StandardCharsets.UTF_8);
            return true;
         }
      } catch (IOException exception) {
         this.plugin.getLogger().log(Level.WARNING, "Gagal menulis auctions.yml: " + exception.getMessage());
         return false;
      }
   }

   public static String encode(ItemStack stack) {
      return Base64.getEncoder().encodeToString(stack.serializeAsBytes());
   }

   public static ItemStack decode(String text) {
      if (text != null && !text.isBlank()) {
         try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(text));
         } catch (RuntimeException exception) {
            return null;
         }
      } else {
         return null;
      }
   }

   public static final class Snapshot {
      private final Map<Integer, AuctionListing> listings;
      private final Map<UUID, List<ItemStack>> mailbox;
      private final int nextId;

      Snapshot(Map<Integer, AuctionListing> listings, Map<UUID, List<ItemStack>> mailbox, int nextId) {
         super();
         this.listings = listings;
         this.mailbox = mailbox;
         this.nextId = nextId;
      }

      public Map<Integer, AuctionListing> listings() {
         return this.listings;
      }

      public Map<UUID, List<ItemStack>> mailbox() {
         return this.mailbox;
      }

      public int nextId() {
         return this.nextId;
      }
   }
}
