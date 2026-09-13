package me.w2n.w2nsmp.sell;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import me.w2n.w2nsmp.W2NSMP;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

public final class PriceRegistry {
   private static final String FILE_NAME = "prices.yml";
   private final W2NSMP plugin;
   private final File file;
   private final Map<Material, Integer> prices = new EnumMap<>(Material.class);

   public PriceRegistry(W2NSMP plugin) {
      super();
      this.plugin = plugin;
      this.file = new File(plugin.getDataFolder(), "prices.yml");
   }

   public void load() {
      this.prices.clear();
      if (!this.file.exists()) {
         this.plugin.saveResource("prices.yml", false);
      }

      if (!this.file.exists()) {
         this.plugin.getLogger().warning("prices.yml tidak ditemukan dan gagal dibuat - daftar harga kosong.");
      } else {
         YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.file);
         int invalid = 0;

         for (String key : yaml.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material != null && material.isItem()) {
               int price = this.readPrice(yaml, key);
               if (price <= 0) {
                  this.plugin.getLogger().warning("prices.yml: harga untuk " + key + " harus angka bulat > 0 - dilewati.");
                  invalid++;
               } else {
                  this.prices.put(material, price);
               }
            } else {
               this.plugin.getLogger().warning("prices.yml: '" + key + "' bukan material item yang valid - dilewati.");
               invalid++;
            }
         }

         this.plugin
            .getLogger()
            .info("Memuat " + this.prices.size() + " harga item dari prices.yml" + (invalid > 0 ? " (" + invalid + " entri dilewati)" : "") + ".");
      }
   }

   public int price(Material material) {
      Integer price = this.prices.get(material);
      return price == null ? 0 : price;
   }

   public boolean hasPrice(Material material) {
      return this.prices.containsKey(material);
   }

   public Set<Material> materials() {
      return Collections.unmodifiableSet(this.prices.keySet());
   }

   public int size() {
      return this.prices.size();
   }

   public boolean setPrice(Material material, int price) {
      if (price < 0) {
         return false;
      }

      YamlConfiguration yaml = this.file.exists() ? YamlConfiguration.loadConfiguration(this.file) : new YamlConfiguration();
      if (price == 0) {
         yaml.set(material.name(), null);
         this.prices.remove(material);
      } else {
         yaml.set(material.name() + ".sell", price);
         this.prices.put(material, price);
      }

      try {
         yaml.save(this.file);
         return true;
      } catch (IOException exception) {
         this.plugin.getLogger().severe("Gagal menyimpan prices.yml: " + exception.getMessage());
         this.load();
         return false;
      }
   }

   private int readPrice(YamlConfiguration yaml, String key) {
      return yaml.isConfigurationSection(key) ? yaml.getInt(key + ".sell", 0) : yaml.getInt(key, 0);
   }
}
