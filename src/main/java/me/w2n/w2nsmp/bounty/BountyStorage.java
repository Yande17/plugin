package me.w2n.w2nsmp.bounty;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class BountyStorage {
   public static final String FILE_NAME = "bounties.yml";
   private final JavaPlugin plugin;
   private final File file;
   private final AtomicLong version = new AtomicLong();
   private final AtomicLong writtenVersion = new AtomicLong();
   private final Object writeLock = new Object();
   private boolean saveQueued;
   private boolean loadFailed;
   private boolean warnedBroken;

   public BountyStorage(JavaPlugin plugin) {
      super();
      this.plugin = plugin;
      this.file = new File(plugin.getDataFolder(), "bounties.yml");
   }

   public File file() {
      return this.file;
   }

   public boolean loadFailed() {
      return this.loadFailed;
   }

   public BountyStorage.Snapshot load() {
      this.loadFailed = false;
      this.warnedBroken = false;
      Map<UUID, Long> amounts = new LinkedHashMap<>();
      Map<UUID, String> names = new HashMap<>();
      Map<UUID, Map<UUID, Long>> claims = new HashMap<>();
      if (!this.file.isFile()) {
         return new BountyStorage.Snapshot(amounts, names, claims);
      }

      YamlConfiguration yaml = new YamlConfiguration();

      try {
         yaml.load(this.file);
      } catch (IOException | InvalidConfigurationException exception) {
         this.loadFailed = true;
         this.plugin
            .getLogger()
            .warning("bounties.yml tidak bisa dibaca (" + exception.getMessage() + "). Berkas TIDAK ditimpa; bounty dianggap kosong sampai berkas diperbaiki.");
         return new BountyStorage.Snapshot(amounts, names, claims);
      }

      ConfigurationSection bountySection = yaml.getConfigurationSection("bounties");
      if (bountySection != null) {
         for (String rawId : bountySection.getKeys(false)) {
            UUID uniqueId = this.parseUuid(rawId);
            if (uniqueId != null) {
               long amount = Math.max(0L, bountySection.getLong(rawId, 0L));
               if (amount > 0L) {
                  amounts.put(uniqueId, amount);
               }
            }
         }
      }

      ConfigurationSection nameSection = yaml.getConfigurationSection("names");
      if (nameSection != null) {
         for (String rawId : nameSection.getKeys(false)) {
            UUID uniqueId = this.parseUuid(rawId);
            if (uniqueId != null) {
               names.put(uniqueId, String.valueOf(nameSection.getString(rawId, "")));
            }
         }
      }

      ConfigurationSection claimSection = yaml.getConfigurationSection("claims");
      if (claimSection != null) {
         for (String rawVictim : claimSection.getKeys(false)) {
            UUID victim = this.parseUuid(rawVictim);
            if (victim != null) {
               ConfigurationSection perKiller = claimSection.getConfigurationSection(rawVictim);
               if (perKiller != null) {
                  Map<UUID, Long> entries = new HashMap<>();

                  for (String rawKiller : perKiller.getKeys(false)) {
                     UUID killer = this.parseUuid(rawKiller);
                     if (killer != null) {
                        entries.put(killer, perKiller.getLong(rawKiller, 0L));
                     }
                  }

                  if (!entries.isEmpty()) {
                     claims.put(victim, entries);
                  }
               }
            }
         }
      }

      return new BountyStorage.Snapshot(amounts, names, claims);
   }

   public void saveAsync(BountyStorage.Snapshot snapshot) {
      if (!this.saveQueued) {
         this.saveQueued = true;
         long queued = this.version.incrementAndGet();
         Bukkit.getScheduler().runTask(this.plugin, () -> {
            this.saveQueued = false;
            String content = this.serialize(snapshot);
            Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> this.write(queued, content));
         });
      }
   }

   public void saveNow(BountyStorage.Snapshot snapshot) {
      this.write(this.version.incrementAndGet(), this.serialize(snapshot));
   }

   public boolean isDirty() {
      return this.version.get() > this.writtenVersion.get();
   }

   private String serialize(BountyStorage.Snapshot snapshot) {
      StringBuilder builder = new StringBuilder();
      builder.append("# Bounty pemain W2NSMP (PHASE 11) - diatur lewat /bounty.\n");
      builder.append("# Semua kunci adalah UUID pemain. \"claims\" dipakai hanya untuk mencegah\n");
      builder.append("# pengambilan bounty berulang dari korban yang sama (anti-farm).\n\n");
      builder.append("bounties:");
      if (snapshot.amounts().isEmpty()) {
         builder.append(" {}\n");
      } else {
         builder.append('\n');

         for (Entry<UUID, Long> entry : this.sorted(snapshot.amounts())) {
            builder.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
         }
      }

      builder.append("names:");
      if (snapshot.names().isEmpty()) {
         builder.append(" {}\n");
      } else {
         builder.append('\n');

         for (Entry<UUID, String> entry : this.sortedNames(snapshot.names())) {
            builder.append("  ")
               .append(entry.getKey())
               .append(": \"")
               .append(entry.getValue() == null ? "" : entry.getValue().replace("\"", ""))
               .append("\"\n");
         }
      }

      builder.append("claims:");
      if (snapshot.claims().isEmpty()) {
         builder.append(" {}\n");
      } else {
         builder.append('\n');

         for (Entry<UUID, Map<UUID, Long>> victim : this.sortedClaims(snapshot.claims())) {
            builder.append("  ").append(victim.getKey()).append(":\n");

            for (Entry<UUID, Long> killer : this.sorted(victim.getValue())) {
               builder.append("    ").append(killer.getKey()).append(": ").append(killer.getValue()).append('\n');
            }
         }
      }

      return builder.toString();
   }

   private List<Entry<UUID, Long>> sorted(Map<UUID, Long> map) {
      return map.entrySet().stream().sorted(Entry.comparingByKey()).toList();
   }

   private List<Entry<UUID, String>> sortedNames(Map<UUID, String> map) {
      return map.entrySet().stream().sorted(Entry.comparingByKey()).toList();
   }

   private List<Entry<UUID, Map<UUID, Long>>> sortedClaims(Map<UUID, Map<UUID, Long>> map) {
      return map.entrySet().stream().sorted(Entry.comparingByKey()).toList();
   }

   private UUID parseUuid(String raw) {
      try {
         return UUID.fromString(raw.trim());
      } catch (IllegalArgumentException exception) {
         this.plugin.getLogger().warning("bounties.yml memuat UUID tidak valid: " + raw);
         return null;
      }
   }

   private boolean write(long writeVersion, String content) {
      synchronized (this.writeLock) {
         if (this.loadFailed) {
            if (!this.warnedBroken) {
               this.warnedBroken = true;
               this.plugin
                  .getLogger()
                  .warning("bounties.yml tidak ditulis karena berkas lama tidak bisa dibaca. Perbaiki/rename berkas itu, lalu /w2nsmp reload.");
            }

            return false;
         } else {
            if (writeVersion < this.writtenVersion.get()) {
               return true;
            }

            boolean var10000;
            try {
               if (!this.plugin.getDataFolder().isDirectory() && !this.plugin.getDataFolder().mkdirs()) {
                  this.plugin.getLogger().warning("Tidak bisa membuat folder data plugin.");
                  return false;
               }

               Files.writeString(this.file.toPath(), content, StandardCharsets.UTF_8);
               this.writtenVersion.set(writeVersion);
               var10000 = true;
            } catch (IOException exception) {
               this.plugin.getLogger().log(Level.WARNING, "Gagal menulis bounties.yml: " + exception.getMessage());
               return false;
            }

            return var10000;
         }
      }
   }

   public record Snapshot(Map<UUID, Long> amounts, Map<UUID, String> names, Map<UUID, Map<UUID, Long>> claims) {
      public Snapshot {
      }
   }
}
