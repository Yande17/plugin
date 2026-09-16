package me.w2n.w2nsmp.skill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Peringkat (top) skill: total level semua skill, atau level satu skill tertentu.
 *
 * <p>Dihitung dari data yang sudah tersimpan di {@link SkillService} - termasuk pemain offline,
 * jadi papan peringkat tetap berisi nama pemain yang belum masuk. Urutannya: level tertinggi lebih
 * dulu, lalu XP sebagai pemecah seri (dua pemain sama level tetapi XP-nya beda tetap punya urutan
 * yang pasti). Profil yang nilainya nol dilewati supaya daftar tidak penuh nama tanpa kemajuan.
 *
 * <p>Kelas ini hanya membaca dan mengurutkan; tidak ada berkas atau tugas berkala tambahan, jadi
 * biaya satu panggilan {@code /skill top} sebanding dengan jumlah profil yang tersimpan.
 */
public final class SkillTop {
   /** Batas jumlah baris yang boleh diminta sekali (menjaga chat tidak banjir). */
   public static final int MAX_LIMIT = 50;

   private final SkillService service;

   public SkillTop(SkillService service) {
      this.service = service;
   }

   /** Peringkat total level (semua skill dijumlahkan). */
   public List<SkillTop.Entry> total(int limit, int offset) {
      List<SkillTop.Entry> entries = new ArrayList<>();
      SkillCurve curve = this.service.curve();

      for (Map.Entry<UUID, SkillProfile> entry : this.service.profilesSnapshot().entrySet()) {
         SkillProfile profile = entry.getValue();
         if (profile == null) {
            continue;
         }

         double totalXp = profile.totalXp();
         int totalLevel = profile.totalLevel(curve);
         if (totalXp <= 0.0D || totalLevel <= 0) {
            continue;
         }

         entries.add(new SkillTop.Entry(entry.getKey(), displayName(entry.getKey(), profile),
            totalLevel, totalXp, totalLevel, totalXp));
      }

      return page(entries, Comparator.comparingInt(SkillTop.Entry::level).reversed()
         .thenComparing(Comparator.comparingDouble(SkillTop.Entry::xp).reversed()), limit, offset);
   }

   /** Peringkat satu skill tertentu (level skill itu; XP skill itu sebagai pemecah seri). */
   public List<SkillTop.Entry> skill(SkillType type, int limit, int offset) {
      List<SkillTop.Entry> entries = new ArrayList<>();
      if (type == null) {
         return entries;
      }

      SkillCurve curve = this.service.curve();

      for (Map.Entry<UUID, SkillProfile> entry : this.service.profilesSnapshot().entrySet()) {
         SkillProfile profile = entry.getValue();
         if (profile == null) {
            continue;
         }

         double xp = profile.xp(type);
         if (xp <= 0.0D) {
            continue;
         }

         int level = Math.max(1, curve.levelFor(xp));
         entries.add(new SkillTop.Entry(entry.getKey(), displayName(entry.getKey(), profile),
            level, xp, profile.totalLevel(curve), profile.totalXp()));
      }

      return page(entries, Comparator.comparingInt(SkillTop.Entry::level).reversed()
         .thenComparing(Comparator.comparingDouble(SkillTop.Entry::xp).reversed()), limit, offset);
   }

   /** Posisi pemain dalam peringkat total (1 = teratas); 0 bila pemain tidak masuk daftar. */
   public int rankOfTotal(UUID uniqueId) {
      if (uniqueId == null) {
         return 0;
      }

      List<SkillTop.Entry> entries = this.total(SkillTop.MAX_LIMIT * 20, 0);

      for (int index = 0; index < entries.size(); index++) {
         if (uniqueId.equals(entries.get(index).uniqueId())) {
            return index + 1;
         }
      }

      return 0;
   }

   /** Posisi pemain dalam peringkat satu skill (1 = teratas); 0 bila tidak masuk daftar. */
   public int rankOfSkill(UUID uniqueId, SkillType type) {
      if (uniqueId == null || type == null) {
         return 0;
      }

      List<SkillTop.Entry> entries = this.skill(type, SkillTop.MAX_LIMIT * 20, 0);

      for (int index = 0; index < entries.size(); index++) {
         if (uniqueId.equals(entries.get(index).uniqueId())) {
            return index + 1;
         }
      }

      return 0;
   }

   private static List<SkillTop.Entry> page(List<SkillTop.Entry> entries, Comparator<SkillTop.Entry> order,
      int limit, int offset) {
      entries.sort(order);
      int size = entries.size();
      int from = Math.max(0, Math.min(offset, size));
      int to = Math.min(size, from + Math.max(1, Math.min(MAX_LIMIT, limit)));
      return new ArrayList<>(entries.subList(from, to));
   }

   /** Nama terbaru pemain bila sedang online, selain itu nama yang tersimpan di profil. */
   private static String displayName(UUID uniqueId, SkillProfile profile) {
      Player player = Bukkit.getPlayer(uniqueId);
      if (player != null && player.getName() != null && !player.getName().isBlank()) {
         return player.getName();
      }

      String stored = profile.name();
      return stored == null || stored.isBlank() ? "pemain" : stored;
   }

   /** Satu baris peringkat. */
   public static final class Entry {
      private final UUID uniqueId;
      private final String name;
      private final int level;
      private final double xp;
      private final int totalLevel;
      private final double totalXp;

      private Entry(UUID uniqueId, String name, int level, double xp, int totalLevel, double totalXp) {
         this.uniqueId = uniqueId;
         this.name = name == null || name.isBlank() ? "pemain" : name.trim();
         this.level = level;
         this.xp = xp;
         this.totalLevel = totalLevel;
         this.totalXp = totalXp;
      }

      public UUID uniqueId() {
         return this.uniqueId;
      }

      /** Nama tampilan baris ini; tidak pernah null/kosong (jadi "pemain" bila datanya hilang). */
      public String name() {
         return this.name;
      }

      /** Level yang dipakai mengurutkan daftar ini (total level, atau level satu skill). */
      public int level() {
         return this.level;
      }

      /** XP yang dipakai mengurutkan daftar ini (pemecah seri). */
      public double xp() {
         return this.xp;
      }

      public int totalLevel() {
         return this.totalLevel;
      }

      public double totalXp() {
         return this.totalXp;
      }

      @Override
      public String toString() {
         return this.name.toLowerCase(Locale.ROOT) + ":lv" + this.level;
      }
   }
}
