package me.w2n.w2nsmp.utility;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.config.GuiConfig;
import me.w2n.w2nsmp.player.PlayerSettingsService;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.Sound.Source;
import org.bukkit.entity.Player;

public final class GuiSounds {
   private static final String DEFAULT_CLICK = "minecraft:ui.button.click";
   private static final String DEFAULT_OPEN = "minecraft:block.chest.open";
   private static final String DEFAULT_SUCCESS = "minecraft:entity.experience_orb.pickup";
   private static final String DEFAULT_ERROR = "minecraft:entity.villager.no";
   private static final String DEFAULT_TOGGLE = "minecraft:ui.button.click";
   private final W2NSMP plugin;
   private final Set<String> warned = new HashSet<>();

   public GuiSounds(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   public void play(Player player, GuiConfig gui, String action) {
      if (player != null && action != null && this.plugin.config().soundsEnabled()) {
         PlayerSettingsService settings = this.plugin.settings();
         if (settings == null || settings.sounds(player)) {
            String fallback = switch (action) {
               case "open" -> "minecraft:block.chest.open";
               case "success" -> "minecraft:entity.experience_orb.pickup";
               case "error" -> "minecraft:entity.villager.no";
               case "toggle" -> "minecraft:ui.button.click";
               default -> "minecraft:ui.button.click";
            };
            String name = gui == null ? fallback : gui.sound(action, fallback);
            Sound sound = this.parse(name, gui == null ? 1.0F : gui.soundVolume(), gui == null ? 1.0F : gui.soundPitch(), action);
            if (sound != null) {
               player.playSound(sound);
            }
         }
      }
   }

   public void play(Player player, String action) {
      this.play(player, this.plugin.guiConfigs() == null ? null : this.plugin.guiConfigs().settings(), action);
   }

   private Sound parse(String raw, float volume, float pitch, String action) {
      if (raw != null && !raw.isBlank() && !raw.equalsIgnoreCase("none")) {
         String key = toKey(raw);

         try {
            return Sound.sound(Key.key(key), Source.MASTER, volume, pitch);
         } catch (IllegalArgumentException | NullPointerException exception) {
            if (this.warned.add(key)) {
               this.plugin
                  .getLogger()
                  .warning(
                     "Nama bunyi tidak dikenali: '"
                        + raw
                        + "' (aksi "
                        + action
                        + "). Pakai kunci seperti minecraft:ui.button.click, atau none untuk mematikan."
                  );
            }

            return null;
         }
      } else {
         return null;
      }
   }

   public static String toKey(String raw) {
      String value = raw.trim().toLowerCase(Locale.ROOT);
      if (value.contains(":")) {
         return value;
      }

      if (value.contains("_") || value.indexOf(46) < 0) {
         value = value.replace('_', '.');
      }

      return "minecraft:" + value;
   }
}
