package me.w2n.w2nsmp.skill;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Penyimpanan data skill ke {@code plugins/W2NSMP/skills.yml}.
 *
 * <p>Sengaja memakai berkas sendiri - bukan {@code statistics.yml} atau {@code w2nsmp.db} -
 * supaya fitur skill tidak pernah menyentuh/mengubah format data statistik yang sudah ada.
 * Menghapus {@code skills.yml} hanya mereset skill; statistik, ekonomi, dan data lain utuh.
 *
 * <p>Pola bacanya mengikuti {@code SettingsStorage}: serialisasi dikerjakan di thread utama
 * (murah, hanya menyusun string), penulisan berkas di thread async, dan bila berkas lama tidak
 * bisa dibaca maka berkas itu TIDAK ditimpa (data pemain dilindungi).
 *
 * <p>Format:
 * <pre>
 * players:
 *   &lt;uuid&gt;:
 *     name: Steve
 *     updated: 1712345678901
 *     xp:
 *       fighting: 1234.5
 *       mining: 980.0
 * </pre>
 * Hanya XP yang disimpan; level selalu dihitung ulang dari kurva di config.yml.
 */
public final class SkillStorage {
   public static final String FILE_NAME = "skills.yml";
   private final JavaPlugin plugin;
   private final File file;
   private final AtomicLong version = new AtomicLong();
   private final AtomicLong writtenVersion = new AtomicLong();
   private final Object writeLock = new Object();
   private boolean saveQueued;
   private boolean loadFailed;
   private boolean warnedBroken;

   public SkillStorage(JavaPlugin plugin) {
      this.plugin = plugin;
      this.file = new File(plugin.getDataFolder(), FILE_NAME);
   }

   public File file() {
      return this.file;
   }

   public boolean loadFailed() {
      return this.loadFailed;
   }

   public Map<UUID, SkillProfile> load() {
      this.loadFailed = false;
      this.warnedBroken = false;
      Map<UUID, SkillProfile> loaded = new LinkedHashMap<>();
      if (!this.file.isFile()) {
         return loaded;
      }

      YamlConfiguration yaml = new YamlConfiguration();

      try {
         yaml.load(this.file);
      } catch (IOException | InvalidConfigurationException exception) {
         this.loadFailed = true;
         this.plugin
            .getLogger()
            .warning(
               "skills.yml tidak bisa dibaca ("
                  + exception.getMessage()
                  + "). File TIDAK ditimpa; semua pemain mulai dari level 1. Perbaiki atau pindahkan berkas itu lalu jalankan /w2nsmp reload."
            );
         return loaded;
      }

      ConfigurationSection players = yaml.getConfigurationSection("players");
      if (players == null) {
         // Format lama/tanpa pembungkus "players": UUID langsung di akar berkas.
         players = yaml;
      }

      for (String rawId : players.getKeys(false)) {
         UUID uniqueId;
         try {
            uniqueId = UUID.fromString(rawId.trim());
         } catch (IllegalArgumentException exception) {
            this.plugin.getLogger().warning("skills.yml memuat UUID tidak valid: " + rawId);
            continue;
         }

         ConfigurationSection section = players.getConfigurationSection(rawId);
         if (section == null) {
            continue;
         }

         SkillProfile profile = new SkillProfile(uniqueId);
         profile.name(section.getString("name"));
         profile.updated(section.getLong("updated", 0L));
         ConfigurationSection xp = section.getConfigurationSection("xp");
         if (xp != null) {
            for (String key : xp.getKeys(false)) {
               SkillType type = SkillType.fromKey(key);
               if (type == null) {
                  // Kunci dari versi lain/dihapus admin: dilewati diam-diam, data lain tetap aman.
                  continue;
               }

               profile.xp(type, xp.getDouble(key));
            }
         }

         if (!profile.isEmpty() || profile.name() != null) {
            loaded.put(uniqueId, profile);
         }
      }

      return loaded;
   }

   /** Antrikan penyimpanan async; permintaan berulang sebelum tulis selesai digabung jadi satu. */
   public void saveAsync(Map<UUID, SkillProfile> data) {
      if (!this.saveQueued) {
         this.saveQueued = true;
         long queued = this.version.incrementAndGet();
         Map<UUID, SkillProfile> copy = copy(data);
         Bukkit.getScheduler().runTask(this.plugin, () -> {
            this.saveQueued = false;
            String content = this.serialize(copy);
            Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> this.write(queued, content));
         });
      }
   }

   public void saveNow(Map<UUID, SkillProfile> data) {
      this.write(this.version.incrementAndGet(), this.serialize(copy(data)));
   }

   public boolean isDirty() {
      return this.version.get() > this.writtenVersion.get();
   }

   private static Map<UUID, SkillProfile> copy(Map<UUID, SkillProfile> data) {
      Map<UUID, SkillProfile> copy = new LinkedHashMap<>(Math.max(16, data.size() * 2));

      for (Entry<UUID, SkillProfile> entry : data.entrySet()) {
         copy.put(entry.getKey(), entry.getValue().copy());
      }

      return copy;
   }

   private String serialize(Map<UUID, SkillProfile> data) {
      StringBuilder builder = new StringBuilder(Math.max(256, data.size() * 128));
      builder.append("# Data skill pemain W2NSMP - dikelola otomatis oleh fitur /skill.\n");
      builder.append("# Yang disimpan hanya XP total per skill; level dihitung dari kurva di config.yml\n");
      builder.append("# (skills.curve.base-xp / exponent / skills.max-level). Menghapus berkas ini mereset\n");
      builder.append("# semua skill ke level 1 dan TIDAK memengaruhi statistik, ekonomi, atau data lain.\n\n");
      if (data.isEmpty()) {
         builder.append("players: {}\n");
         return builder.toString();
      }

      builder.append("players:\n");

      for (Entry<UUID, SkillProfile> entry : data.entrySet().stream().sorted(Entry.comparingByKey()).toList()) {
         SkillProfile profile = entry.getValue();
         builder.append("  ").append(entry.getKey()).append(":\n");
         if (profile.name() != null && !profile.name().isBlank()) {
            builder.append("    name: '").append(profile.name().replace("'", "''")).append("'\n");
         }

         builder.append("    updated: ").append(profile.updated()).append('\n');
         builder.append("    xp:\n");

         for (SkillType type : SkillType.values()) {
            double xp = profile.xp(type);
            if (xp > 0.0D) {
               builder.append("      ").append(type.key()).append(": ").append(String.format(Locale.ROOT, "%.2f", Double.valueOf(xp))).append('\n');
            }
         }
      }

      return builder.toString();
   }

   private boolean write(long writeVersion, String content) {
      synchronized (this.writeLock) {
         if (this.loadFailed) {
            if (this.warnedBroken) {
               return false;
            }

            this.warnedBroken = true;
            this.plugin
               .getLogger()
               .warning("skills.yml tidak ditulis karena berkas lama tidak bisa dibaca. Perbaiki/rename berkas itu, lalu /w2nsmp reload.");
            return false;
         }

         if (writeVersion < this.writtenVersion.get()) {
            return true;
         }

         try {
            if (!this.plugin.getDataFolder().isDirectory() && !this.plugin.getDataFolder().mkdirs()) {
               this.plugin.getLogger().warning("Tidak bisa membuat folder data plugin.");
               return false;
            }

            Files.writeString(this.file.toPath(), content, StandardCharsets.UTF_8);
            this.writtenVersion.set(writeVersion);
            return true;
         } catch (IOException exception) {
            this.plugin.getLogger().log(Level.WARNING, "Gagal menulis skills.yml: " + exception.getMessage());
            return false;
         }
      }
   }
}
