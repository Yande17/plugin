package me.w2n.w2nsmp.skill;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.w2n.w2nsmp.W2NSMP;

/**
 * Diagnostik fitur skill: membuktikan bahwa listener benar-benar terdaftar di server dan
 * menyimpan kesalahan terakhir supaya tidak ada kegagalan yang terjadi diam-diam.
 *
 * <p>Kenapa kelas ini ada: handler Bukkit ditemukan lewat refleksi
 * ({@code method.isAnnotationPresent(EventHandler.class)}). Bila sebuah listener terdaftar
 * tapi anotasi {@code @EventHandler}-nya tidak terlihat saat runtime, listener itu "tuli":
 * tidak ada error di log, GUI tidak membatalkan klik, dan XP tidak pernah masuk. Karena itu
 * pendaftaran listener diverifikasi langsung ke {@code HandlerList} server (lewat refleksi,
 * tanpa mengandaikan bentuk API-nya), dan hasilnya bisa dilihat lewat {@code /skill check}.
 *
 * <p>Semua pemanggilan refleksi dibungkus {@code try/catch}: bila API-nya berbeda di versi
 * server tertentu, diagnostik melaporkan "tidak bisa diperiksa" - bukan melempar exception.
 */
public final class SkillDiagnostics {
   /** Event yang harus punya handler W2NSMP agar fitur skill hidup, beserta listener-nya. */
   private static final String[][] WATCHED = new String[][]{
      {"org.bukkit.event.inventory.InventoryClickEvent", "SkillGuiListener"},
      {"org.bukkit.event.inventory.InventoryDragEvent", "SkillGuiListener"},
      {"org.bukkit.event.entity.EntityDamageEvent", "SkillListener"},
      {"org.bukkit.event.entity.EntityDamageByEntityEvent", "SkillListener"},
      {"org.bukkit.event.block.BlockBreakEvent", "SkillListener"},
      {"org.bukkit.event.player.PlayerMoveEvent", "SkillListener"},
      {"org.bukkit.event.player.PlayerJoinEvent", "SkillListener"},
      {"org.bukkit.event.entity.EntityDeathEvent", "SkillListener"},
   };

   private static final int MAX_ERRORS = 8;

   private final W2NSMP plugin;
   private final Map<String, String> errors = new LinkedHashMap<>();
   private final Set<String> warned = new LinkedHashSet<>();
   private String listenerReport = "belum diperiksa";
   private Set<String> missingListeners = Set.of();
   private boolean checked;
   private boolean listenersOk;
   private boolean reflectionOk = true;

   public SkillDiagnostics(W2NSMP plugin) {
      this.plugin = plugin;
   }

   /**
    * Periksa HandlerList server: listener W2NSMP mana yang benar-benar terdaftar.
    * Mengembalikan nama kelas listener yang HILANG (kosong bila semua lengkap).
    */
   public synchronized Set<String> inspect() {
      Set<String> missing = new LinkedHashSet<>();
      List<String> lines = new ArrayList<>();
      boolean allOk = true;

      for (String[] entry : WATCHED) {
         String eventName = entry[0];
         String listenerName = entry[1];
         String shortEvent = eventName.substring(eventName.lastIndexOf('.') + 1);
         Set<String> found = registeredListeners(eventName);
         if (found == null) {
            lines.add(shortEvent + ": tidak bisa diperiksa (API HandlerList berbeda)");
            this.reflectionOk = false;
            allOk = false;
            continue;
         }

         if (found.contains(listenerName)) {
            lines.add(shortEvent + ": " + listenerName + " terdaftar (" + found.size() + " listener W2NSMP)");
         } else {
            lines.add(shortEvent + ": " + listenerName + " TIDAK TERDAFTAR");
            missing.add(listenerName);
            allOk = false;
         }
      }

      this.checked = true;
      this.listenersOk = allOk;
      this.missingListeners = Set.copyOf(missing);
      this.listenerReport = String.join(" | ", lines);
      return this.missingListeners;
   }

   /** Nama kelas sederhana listener W2NSMP yang terdaftar pada sebuah event; null bila gagal. */
   private Set<String> registeredListeners(String eventClassName) {
      try {
         Class<?> eventClass = Class.forName(eventClassName, false, SkillDiagnostics.class.getClassLoader());
         Method getHandlerList = eventClass.getMethod("getHandlerList");
         Object handlerList = getHandlerList.invoke(null);
         if (handlerList == null) {
            return null;
         }

         Method getRegistered = handlerList.getClass().getMethod("getRegisteredListeners");
         Object result = getRegistered.invoke(handlerList);
         if (!(result instanceof Object[] registered)) {
            return null;
         }

         Set<String> found = new LinkedHashSet<>();

         for (Object item : registered) {
            if (item == null) {
               continue;
            }

            try {
               Method getPlugin = item.getClass().getMethod("getPlugin");
               Object owner = getPlugin.invoke(item);
               if (owner != this.plugin) {
                  continue;
               }

               Method getListener = item.getClass().getMethod("getListener");
               Object listener = getListener.invoke(item);
               if (listener != null) {
                  found.add(listener.getClass().getSimpleName());
               }
            } catch (Throwable ignored) {
               // satu entri gagal dibaca bukan berarti seluruh pemeriksaan gagal
            }
         }

         return found;
      } catch (Throwable throwable) {
         this.plugin.debug("Skill: tidak bisa memeriksa HandlerList " + eventClassName + " (" + throwable + ").");
         return null;
      }
   }

   /**
    * Catat kegagalan di jalur skill dan tulis WARNING sekali per lokasi. Kesalahan tidak boleh
    * hilang diam-diam: admin perlu melihatnya di konsol tanpa harus menyalakan mode debug.
    */
   public synchronized void noteError(String where, Throwable throwable) {
      String text = throwable == null ? "unknown" : throwable.getClass().getName() + ": " + throwable.getMessage();
      this.errors.put(where, text);
      while (this.errors.size() > MAX_ERRORS) {
         this.errors.remove(this.errors.keySet().iterator().next());
      }

      if (this.warned.add(where)) {
         this.plugin.getLogger().warning("Skill: gangguan di " + where + " -> " + text + " (selanjutnya tidak diulang; lihat /skill check)");
      }
   }

   public synchronized List<String> errorLines() {
      if (this.errors.isEmpty()) {
         return List.of();
      }

      List<String> lines = new ArrayList<>(this.errors.size());

      for (Map.Entry<String, String> entry : this.errors.entrySet()) {
         lines.add(entry.getKey() + " -> " + entry.getValue());
      }

      return lines;
   }

   public synchronized String listenerReport() {
      return this.listenerReport;
   }

   public synchronized Set<String> missingListeners() {
      return this.missingListeners;
   }

   public synchronized boolean listenersOk() {
      return this.listenersOk;
   }

   public synchronized boolean isChecked() {
      return this.checked;
   }

   public synchronized boolean reflectionOk() {
      return this.reflectionOk;
   }

   /** Ringkasan satu baris untuk log startup & /w2nsmp debug. */
   public synchronized String summary() {
      if (!this.checked) {
         return "listener: belum diperiksa";
      }

      return (this.listenersOk ? "listener: terdaftar semua" : "listener: HILANG " + this.missingListeners)
         + (this.reflectionOk ? "" : " (sebagian tidak bisa diperiksa)")
         + (this.errors.isEmpty() ? " | error: 0" : " | error terakhir: " + this.errors.size());
   }
}
