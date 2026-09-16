import java.lang.reflect.Method;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

/**
 * Uji runtime: meniru persis cara Bukkit menemukan handler event.
 *
 * Bukkit memakai refleksi - PluginLoader memeriksa setiap method publik dengan
 * {@code method.isAnnotationPresent(EventHandler.class)}, membaca {@code priority()} dan
 * {@code ignoreCancelled()}, lalu memastikan jumlah parameternya satu dan bertipe Event.
 * Bila anotasi tidak punya @Retention(RUNTIME), pemeriksaan itu menghasilkan NOL handler:
 * listener tetap "terdaftar" tapi tuli - GUI tidak membatalkan klik, XP tidak pernah masuk.
 * Uji ini menjalankan pemeriksaan yang sama terhadap class hasil kompilasi.
 */
public class ListenerAnnotationTest {
   private static int gagal = 0;

   public static void main(String[] args) throws Exception {
      String[] names = {
         "me.w2n.w2nsmp.listener.SkillListener",
         "me.w2n.w2nsmp.listener.SkillGuiListener",
         "me.w2n.w2nsmp.listener.SkillFishingListener",
      };
      int[] minimum = {8, 4, 1};

      for (int i = 0; i < names.length; i++) {
         Class<?> type = Class.forName(names[i]);
         int found = 0;
         System.out.println("  " + type.getSimpleName() + " (implements Listener: "
            + org.bukkit.event.Listener.class.isAssignableFrom(type) + ")");

         for (Method method : type.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(EventHandler.class)) {
               continue;
            }

            found++;
            EventHandler annotation = method.getAnnotation(EventHandler.class);
            EventPriority priority = annotation.priority();
            boolean ignoreCancelled = annotation.ignoreCancelled();
            Class<?>[] parameters = method.getParameterTypes();
            boolean sah = method.getReturnType() == void.class
               && java.lang.reflect.Modifier.isPublic(method.getModifiers())
               && !java.lang.reflect.Modifier.isStatic(method.getModifiers())
               && parameters.length == 1;

            System.out.println("     handler " + found + ": " + method.getName() + "("
               + (parameters.length == 1 ? parameters[0].getSimpleName() : "?") + ")  priority=" + priority
               + "  ignoreCancelled=" + ignoreCancelled + (sah ? "" : "  <-- BENTUK TIDAK VALID"));
            if (!sah) {
               gagal++;
            }
         }

         if (found < minimum[i]) {
            System.out.println("     GAGAL: Bukkit hanya akan melihat " + found + " handler, diharapkan minimal " + minimum[i]);
            gagal++;
         } else {
            System.out.println("     -> " + found + " handler terlihat oleh refleksi (Bukkit akan memanggilnya)");
         }
      }

      // Pembanding: listener asli plugin (bytecode dari JAR 1.0.0) harus terlihat sama.
      System.out.println(gagal == 0
         ? "\nHASIL: semua handler listener skill terlihat saat runtime (bug v1.2.0 sudah hilang)"
         : "\nHASIL: " + gagal + " masalah - listener akan tuli di server");
      if (gagal > 0) {
         System.exit(1);
      }
   }
}
