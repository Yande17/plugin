package me.w2n.w2nsmp.skill;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import me.w2n.w2nsmp.W2NSMP;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Pemeriksa ketersediaan API + jembatan refleksi untuk buff yang bergantung pada API
 * yang bentuknya bisa berbeda antar versi server (efek potion, kecepatan jalan).
 *
 * <p>Kenapa perlu: W2NSMP menargetkan Paper 26.2 dan semua fitur lama hanya memakai API yang
 * sudah terbukti ada di bytecode plugin. Fitur skill memakai beberapa API tambahan; bila salah
 * satunya tidak ada di versi server tempat plugin dijalankan, pemanggilan langsung akan
 * melempar {@code NoSuchMethodError} dan bisa menjatuhkan listener. Dengan probe ini:
 *
 * <ul>
 *   <li>saat enable, setiap API yang dibutuhkan dicek lewat refleksi (sekali, hasilnya di-cache);</li>
 *   <li>buff/sumber XP yang API-nya tidak ada dinonaktifkan sendiri dan dilaporkan di log;</li>
 *   <li>plugin tetap jalan normal untuk semua fitur lain - tidak ada exception ke server.</li>
 * </ul>
 *
 * <p>Ringkasan probe bisa dilihat lewat {@code /w2nsmp skillinfo} (admin).
 */
public final class SkillApiProbe {
   private final W2NSMP plugin;
   private final List<String> notes = new ArrayList<>();
   private boolean damageApi;
   private boolean damageCauseApi;
   private boolean healthApi;
   private boolean walkSpeedApi;
   private boolean blockApi;
   private boolean blockDropsApi;
   private boolean fishingEventApi;
   private boolean mobLootApi;
   private boolean blockExpApi;
   private Object hasteType;
   private Class<?> potionTypeClass;
   /** Cache kunci efek ("haste", "absorption", ...) -> PotionEffectType (null = tidak ada di server ini). */
   private final Map<String, Object> potionTypes = new HashMap<>();
   /** Kunci efek yang diminta config tetapi tidak ada di server ini (untuk /skill check). */
   private final Set<String> missingPotions = new LinkedHashSet<>();
   private Class<?> potionEffectClass;
   private Constructor<?> potionEffectConstructor;
   private Method addPotionEffect;
   private Method removePotionEffect;
   private Method setWalkSpeed;
   private Method getWalkSpeed;
   private boolean probed;

   public SkillApiProbe(W2NSMP plugin) {
      this.plugin = plugin;
   }

   /** Jalankan semua pemeriksaan. Aman dipanggil berulang (hasilnya di-cache). */
   public synchronized void probe() {
      if (this.probed) {
         return;
      }

      this.probed = true;
      this.notes.clear();
      this.damageApi = hasMethod(EntityDamageEvent.class, "getDamage") && hasMethod(EntityDamageEvent.class, "setDamage", double.class);
      this.damageCauseApi = this.damageApi && hasMethod(EntityDamageEvent.class, "getCause");
      this.healthApi = hasMethod(LivingEntity.class, "getHealth") && hasMethod(LivingEntity.class, "setHealth", double.class);
      this.walkSpeedApi = hasMethod(Player.class, "setWalkSpeed", float.class) && hasMethod(Player.class, "getWalkSpeed");
      this.blockApi = hasMethod(BlockBreakEvent.class, "getBlock");
      this.blockDropsApi = this.blockApi && hasMethod(Block.class, "getDrops");
      this.fishingEventApi = hasClass("org.bukkit.event.player.PlayerFishEvent");
      this.mobLootApi = hasClass("org.bukkit.event.entity.EntityDeathEvent")
         && hasMethod(EntityDeathEvent.class, "getDrops");
      this.blockExpApi = this.blockApi && hasMethod(BlockBreakEvent.class, "getExpToDrop")
         && hasMethod(BlockBreakEvent.class, "setExpToDrop", int.class);
      if (this.walkSpeedApi) {
         this.setWalkSpeed = method(Player.class, "setWalkSpeed", float.class);
         this.getWalkSpeed = method(Player.class, "getWalkSpeed");
      }

      this.probePotion();
      if (!this.damageApi) {
         this.notes.add("damage buff (fighting/defense/archery/endurance) mati: EntityDamageEvent.getDamage/setDamage tidak ada");
      }

      if (!this.damageCauseApi) {
         this.notes.add("buff endurance mati: EntityDamageEvent.getCause tidak ada");
      }

      if (!this.healthApi) {
         this.notes.add("buff vitality/recovery mati: LivingEntity.getHealth/setHealth tidak ada");
      }

      if (!this.walkSpeedApi) {
         this.notes.add("buff agility mati: Player.setWalkSpeed tidak ada");
      }

      if (!this.blockApi) {
         this.notes.add("XP mining/woodcutting/farming mati: BlockBreakEvent.getBlock tidak ada");
      }

      if (!this.blockDropsApi) {
         this.notes.add("drop tambahan woodcutting/farming mati: Block.getDrops tidak ada");
      }

      if (!this.fishingEventApi) {
         this.notes.add("skill fishing mati: org.bukkit.event.player.PlayerFishEvent tidak ada");
      }

      if (this.hasteType == null) {
         this.notes.add("buff haste (mining) mati: PotionEffectType 'haste' tidak bisa ditemukan di server ini");
      }

      if (!this.mobLootApi) {
         this.notes.add("buff loot mob (fighting/archery) mati: EntityDeathEvent.getDrops tidak ada");
      }

      if (!this.blockExpApi) {
         this.notes.add("buff XP vanilla (mining) mati: BlockBreakEvent.getExpToDrop/setExpToDrop tidak ada");
      }

      if (!this.notes.isEmpty()) {
         this.plugin.getLogger().warning("Skill: beberapa buff/XP dinonaktifkan otomatis karena API server tidak tersedia:");

         for (String note : this.notes) {
            this.plugin.getLogger().warning("Skill:   - " + note);
         }
      } else {
         this.plugin.getLogger().info("Skill: semua API buff tersedia (damage, health, walk-speed, block drops, fishing, potion haste).");
      }
   }

   /** Cari PotionEffectType 'haste' + constructor PotionEffect lewat refleksi (cascade antar versi). */
   private void probePotion() {
      try {
         Class<?> typeClass = Class.forName("org.bukkit.potion.PotionEffectType");
         this.potionTypeClass = typeClass;
         this.potionEffectClass = Class.forName("org.bukkit.potion.PotionEffect");
         this.potionEffectConstructor = this.potionEffectClass.getConstructor(typeClass, int.class, int.class, boolean.class, boolean.class);
         this.addPotionEffect = LivingEntity.class.getMethod("addPotionEffect", this.potionEffectClass);
         this.removePotionEffect = LivingEntity.class.getMethod("removePotionEffect", typeClass);
         this.hasteType = findPotionType(typeClass, "haste", "HASTE");
         this.potionTypes.put("haste", this.hasteType);
      } catch (Throwable throwable) {
         this.hasteType = null;
         this.potionTypeClass = null;
         this.potionEffectClass = null;
         this.potionEffectConstructor = null;
         this.addPotionEffect = null;
         this.removePotionEffect = null;
         this.potionTypes.clear();
         this.plugin.debug("Skill: API potion tidak tersedia (" + throwable + ").");
      }
   }

   /**
    * Cari {@code PotionEffectType} untuk kunci efek apa pun ("haste", "regeneration", "absorption",
    * "slow_falling", ...) memakai cascade yang sama dengan Haste, lalu simpan hasilnya. Mengembalikan
    * {@code null} bila server versi ini tidak punya efek tersebut - pemanggil cukup melewatinya.
    */
   public synchronized Object potionType(String rawKey) {
      if (rawKey == null || this.potionTypeClass == null) {
         return null;
      }

      String key = rawKey.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
      if (key.isEmpty()) {
         return null;
      }

      if (this.potionTypes.containsKey(key)) {
         return this.potionTypes.get(key);
      }

      Object type = findPotionType(this.potionTypeClass, key, key.toUpperCase(Locale.ROOT));
      this.potionTypes.put(key, type);
      if (type == null) {
         this.missingPotions.add(key);
         this.plugin.debug("Skill: efek potion '" + key + "' tidak ada di server ini - buff dilewati.");
      }

      return type;
   }

   /** Kunci efek yang diminta config tetapi tidak dikenal server ini (kosong bila semuanya ada). */
   public Set<String> missingPotions() {
      return Collections.unmodifiableSet(this.missingPotions);
   }

   private Object findPotionType(Class<?> typeClass, String key, String constantName) {
      // 1) Registry.EFFECT (Paper/Spigot baru): iterasi lalu cocokkan kunci namespacenya.
      try {
         Field field = Class.forName("org.bukkit.Registry").getField("EFFECT");
         Object registry = field.get(null);
         if (registry instanceof Iterable<?> iterable) {
            for (Object candidate : iterable) {
               if (candidate == null) {
                  continue;
               }

               Object namespacedKey = candidate.getClass().getMethod("getKey").invoke(candidate);
               if (namespacedKey != null) {
                  Object id = namespacedKey.getClass().getMethod("getKey").invoke(namespacedKey);
                  if (key.equalsIgnoreCase(String.valueOf(id))) {
                     return candidate;
                  }
               }
            }
         }
      } catch (Throwable ignored) {
         // registry tidak ada / bentuknya lain -> coba cara berikutnya
      }

      // 2) PotionEffectType.getByKey(NamespacedKey) - API 1.13 sampai sekitar 1.20.
      try {
         Class<?> keyClass = Class.forName("org.bukkit.NamespacedKey");
         Method minecraft = keyClass.getMethod("minecraft", String.class);
         Method byKey = typeClass.getMethod("getByKey", keyClass);
         Object result = byKey.invoke(null, minecraft.invoke(null, key));
         if (result != null) {
            return result;
         }
      } catch (Throwable ignored) {
         // lanjut ke cara berikutnya
      }

      // 3) PotionEffectType.getByName(String) - API lama.
      try {
         Object result = typeClass.getMethod("getByName", String.class).invoke(null, constantName);
         if (result != null) {
            return result;
         }
      } catch (Throwable ignored) {
         // lanjut ke cara berikutnya
      }

      // 4) Konstanta statis PotionEffectType.HASTE (masih ada di banyak versi, deprecated).
      try {
         return typeClass.getField(constantName).get(null);
      } catch (Throwable ignored) {
         return null;
      }
   }

   /**
    * Pasang efek potion apa pun (Haste, Regeneration, Absorption, ...) selama {@code seconds}.
    * Mengembalikan false bila API potion tidak tersedia atau efek itu tidak ada di server ini -
    * buff dilewati diam-diam, tidak pernah melempar exception ke server.
    */
   public boolean applyPotion(Player player, String key, int amplifier, int seconds) {
      Object type = this.potionType(key);
      if (player == null || type == null || this.potionEffectConstructor == null || this.addPotionEffect == null || amplifier < 0) {
         return false;
      }

      try {
         Object effect = this.potionEffectConstructor.newInstance(type, Math.max(20, seconds * 20), amplifier, Boolean.TRUE, Boolean.FALSE);
         this.addPotionEffect.invoke(player, effect);
         return true;
      } catch (Throwable throwable) {
         this.plugin.debug("Skill: gagal memasang efek '" + key + "' untuk " + player.getName() + " (" + throwable + ").");
         this.potionTypes.put(key.trim().toLowerCase(Locale.ROOT), null);
         return false;
      }
   }

   /** Hapus efek potion (dipakai saat skill dimatikan, buff turun tingkat, reload, atau disable). */
   public boolean removePotion(Player player, String key) {
      Object type = this.potionType(key);
      if (player == null || type == null || this.removePotionEffect == null) {
         return false;
      }

      try {
         this.removePotionEffect.invoke(player, type);
         return true;
      } catch (Throwable throwable) {
         this.plugin.debug("Skill: gagal menghapus efek '" + key + "' " + player.getName() + " (" + throwable + ").");
         return false;
      }
   }

   /** Pasang Haste. Mengembalikan false bila API potion tidak tersedia (buff dilewati diam-diam). */
   public boolean applyHaste(Player player, int amplifier, int seconds) {
      return this.applyPotion(player, "haste", amplifier, seconds);
   }

   /** Hapus Haste (dipakai saat skill dimatikan, reload, atau plugin disable). */
   public boolean removeHaste(Player player) {
      return this.removePotion(player, "haste");
   }

   public float walkSpeed(Player player) {
      if (player == null || this.getWalkSpeed == null) {
         return 0.2F;
      }

      try {
         Object value = this.getWalkSpeed.invoke(player);
         return value instanceof Float speed ? speed.floatValue() : 0.2F;
      } catch (Throwable throwable) {
         return 0.2F;
      }
   }

   public boolean walkSpeed(Player player, float speed) {
      if (player == null || this.setWalkSpeed == null) {
         return false;
      }

      try {
         this.setWalkSpeed.invoke(player, Float.valueOf(speed));
         return true;
      } catch (Throwable throwable) {
         this.plugin.debug("Skill: gagal mengatur kecepatan jalan " + player.getName() + " (" + throwable + ").");
         return false;
      }
   }

   public boolean damageApi() {
      return this.damageApi;
   }

   public boolean damageCauseApi() {
      return this.damageCauseApi;
   }

   public boolean healthApi() {
      return this.healthApi;
   }

   public boolean walkSpeedApi() {
      return this.walkSpeedApi;
   }

   public boolean blockApi() {
      return this.blockApi;
   }

   public boolean blockDropsApi() {
      return this.blockDropsApi;
   }

   public boolean fishingEventApi() {
      return this.fishingEventApi;
   }

   /** Apakah mesin efek potion (constructor + add/remove) tersedia di server ini. */
   /** Apakah daftar drop kematian mob bisa dibaca/diubah (buff MOB_LOOT). */
   public boolean mobLootApi() {
      return this.mobLootApi;
   }

   /** Apakah XP vanilla dari block bisa diubah (buff VANILLA_XP mining). */
   public boolean blockExpApi() {
      return this.blockExpApi;
   }

   public boolean potionApi() {
      return this.potionEffectConstructor != null && this.addPotionEffect != null;
   }

   /** Apakah efek Haste khusus bisa dipakai (buff mining). */
   public boolean hasteApi() {
      return this.hasteType != null;
   }

   public List<String> notes() {
      return List.copyOf(this.notes);
   }

   /** Ringkasan untuk /w2nsmp skillinfo. */
   public String summary() {
      return "damage="
         + yesNo(this.damageApi)
         + " cause="
         + yesNo(this.damageCauseApi)
         + " health="
         + yesNo(this.healthApi)
         + " walk-speed="
         + yesNo(this.walkSpeedApi)
         + " block="
         + yesNo(this.blockApi)
         + " drops="
         + yesNo(this.blockDropsApi)
         + " fishing="
         + yesNo(this.fishingEventApi)
         + " potion="
         + yesNo(this.potionApi())
         + " mobloot="
         + yesNo(this.mobLootApi)
         + " blockexp="
         + yesNo(this.blockExpApi);
   }

   private static String yesNo(boolean value) {
      return value ? "&aok" : "&cmissing";
   }

   private static boolean hasClass(String name) {
      try {
         Class.forName(name, false, SkillApiProbe.class.getClassLoader());
         return true;
      } catch (Throwable throwable) {
         return false;
      }
   }

   private static boolean hasMethod(Class<?> type, String name, Class<?>... parameters) {
      return method(type, name, parameters) != null;
   }

   private static Method method(Class<?> type, String name, Class<?>... parameters) {
      try {
         return type.getMethod(name, parameters);
      } catch (Throwable throwable) {
         return null;
      }
   }

   /** Nama efek potion yang dicari (bisa diubah admin lewat config bila perlu). */
   public String hasteKeyName() {
      return "haste".toUpperCase(Locale.ROOT);
   }
}
