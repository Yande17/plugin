package me.w2n.w2nsmp.utility;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Pembungkus aman {@code PersistentDataContainer} untuk metadata item (v1.4.0).
 *
 * <p>Dipakai fitur gear history dan custom fishing: semua identitas & progres item disimpan
 * sebagai PDC - <b>bukan</b> display name/lore - sehingga rename anvil, perpindahan chest,
 * penjualan, dan restart server tidak pernah menghapus datanya.
 *
 * <p>Setiap pemanggilan dibungkus {@code try/catch}: PDC yang berisi tipe tak terduga (mis.
 * plugin lain memakai kunci yang sama, atau berkas item rusak) tidak boleh melempar exception
 * ke listener - nilai bawaan yang dikembalikan.
 */
public final class ItemTags {
   private ItemTags() {
   }

   public static String getString(ItemMeta meta, NamespacedKey key) {
      if (meta == null || key == null) {
         return null;
      }

      try {
         PersistentDataContainer container = meta.getPersistentDataContainer();
         Object value = container == null ? null : container.get(key, PersistentDataType.STRING);
         return value instanceof String text ? text : null;
      } catch (Throwable throwable) {
         return null;
      }
   }

   public static int getInt(ItemMeta meta, NamespacedKey key, int fallback) {
      if (meta == null || key == null) {
         return fallback;
      }

      try {
         PersistentDataContainer container = meta.getPersistentDataContainer();
         Object value = container == null ? null : container.get(key, PersistentDataType.INTEGER);
         return value instanceof Integer number ? number.intValue() : fallback;
      } catch (Throwable throwable) {
         return fallback;
      }
   }

   public static long getLong(ItemMeta meta, NamespacedKey key, long fallback) {
      if (meta == null || key == null) {
         return fallback;
      }

      try {
         PersistentDataContainer container = meta.getPersistentDataContainer();
         Object value = container == null ? null : container.get(key, PersistentDataType.LONG);
         return value instanceof Long number ? number.longValue() : fallback;
      } catch (Throwable throwable) {
         return fallback;
      }
   }

   public static double getDouble(ItemMeta meta, NamespacedKey key, double fallback) {
      if (meta == null || key == null) {
         return fallback;
      }

      try {
         PersistentDataContainer container = meta.getPersistentDataContainer();
         Object value = container == null ? null : container.get(key, PersistentDataType.DOUBLE);
         return value instanceof Double number ? number.doubleValue() : fallback;
      } catch (Throwable throwable) {
         return fallback;
      }
   }

   public static boolean has(ItemMeta meta, NamespacedKey key) {
      if (meta == null || key == null) {
         return false;
      }

      try {
         PersistentDataContainer container = meta.getPersistentDataContainer();
         return container != null
            && (container.has(key, PersistentDataType.STRING)
               || container.has(key, PersistentDataType.INTEGER)
               || container.has(key, PersistentDataType.LONG)
               || container.has(key, PersistentDataType.DOUBLE));
      } catch (Throwable throwable) {
         return false;
      }
   }

   public static boolean setString(ItemMeta meta, NamespacedKey key, String value) {
      if (meta == null || key == null || value == null) {
         return false;
      }

      try {
         meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
         return true;
      } catch (Throwable throwable) {
         return false;
      }
   }

   public static boolean setInt(ItemMeta meta, NamespacedKey key, int value) {
      if (meta == null || key == null) {
         return false;
      }

      try {
         meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, Integer.valueOf(value));
         return true;
      } catch (Throwable throwable) {
         return false;
      }
   }

   public static boolean setLong(ItemMeta meta, NamespacedKey key, long value) {
      if (meta == null || key == null) {
         return false;
      }

      try {
         meta.getPersistentDataContainer().set(key, PersistentDataType.LONG, Long.valueOf(value));
         return true;
      } catch (Throwable throwable) {
         return false;
      }
   }

   public static boolean setDouble(ItemMeta meta, NamespacedKey key, double value) {
      if (meta == null || key == null) {
         return false;
      }

      try {
         meta.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, Double.valueOf(value));
         return true;
      } catch (Throwable throwable) {
         return false;
      }
   }

   public static boolean remove(ItemMeta meta, NamespacedKey key) {
      if (meta == null || key == null) {
         return false;
      }

      try {
         meta.getPersistentDataContainer().remove(key);
         return true;
      } catch (Throwable throwable) {
         return false;
      }
   }
}
