package me.w2n.w2nsmp.config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.scoreboard.ScoreboardLine;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class ConfigManager {
   private final W2NSMP plugin;
   private final Messages messages;
   private static final String CONFIG_FILE = "config.yml";
   private static final String MESSAGES_FILE = "messages.yml";

   public ConfigManager(W2NSMP plugin) {
      super();
      this.plugin = plugin;
      plugin.saveDefaultConfig();
      ResourceMerger merger = new ResourceMerger(plugin);
      File folder = plugin.getDataFolder();
      int addedConfigKeys = merger.merge("config.yml", new File(folder, "config.yml"));
      int addedMessageKeys = merger.merge("messages.yml", new File(folder, "messages.yml"));
      if (addedConfigKeys > 0 || addedMessageKeys > 0) {
         plugin.getLogger()
            .info(
               "Key baru ditambahkan ke file lama: config.yml="
                  + Math.max(addedConfigKeys, 0)
                  + " blok, messages.yml="
                  + Math.max(addedMessageKeys, 0)
                  + " blok."
            );
         plugin.reloadConfig();
      }

      this.messages = new Messages(plugin);
   }

   public void reload() {
      this.plugin.reloadConfig();
      this.messages.reload();
   }

   public FileConfiguration raw() {
      return this.plugin.getConfig();
   }

   public Messages messages() {
      return this.messages;
   }

   public String prefix() {
      return this.raw().getString("plugin.prefix", "&8[&bW2NSMP&8] &r");
   }

   public boolean debug() {
      return this.raw().getBoolean("plugin.debug", false);
   }

   public boolean isCommandEnabled(String name) {
      return this.raw().getBoolean("commands." + name + ".enabled", true);
   }

   public List<ScoreboardLine> scoreboardLineObjects() {
      List<ScoreboardLine> result = new ArrayList<>();
      ConfigurationSection section = this.raw().getConfigurationSection("scoreboard.lines");
      if (section != null) {
         for (String key : section.getKeys(false)) {
            ConfigurationSection line = section.getConfigurationSection(key);
            if (line != null) {
               result.add(ScoreboardLine.parse(key, line));
            }
         }

         return result;
      } else {
         List<String> legacy = this.scoreboardLines();

         for (int index = 0; index < legacy.size(); index++) {
            String text = legacy.get(index);
            if (text != null && !text.isEmpty()) {
               result.add(ScoreboardLine.legacy(text, index));
            }
         }

         return result;
      }
   }

   public boolean skipConflictingCommands() {
      return this.plainLabelMode() == ConfigManager.PlainLabelMode.AUTO;
   }

   public ConfigManager.PlainLabelMode plainLabelMode() {
      String raw = this.raw().getString("commands.plain-labels", "").trim().toUpperCase(Locale.ROOT);
      switch (raw) {
         case "PREFER":
         case "CLAIM":
         case "FORCE":
            return ConfigManager.PlainLabelMode.PREFER;
         case "AUTO":
         case "FREE":
            return ConfigManager.PlainLabelMode.AUTO;
         case "NAMESPACED":
         case "NONE":
         case "OFF":
            return ConfigManager.PlainLabelMode.NAMESPACED;
         default:
            return this.raw().getBoolean("commands.skip-if-conflict", true) ? ConfigManager.PlainLabelMode.AUTO : ConfigManager.PlainLabelMode.PREFER;
      }
   }

   public boolean mayClaimLabel(String name) {
      return this.plainLabelMode() == ConfigManager.PlainLabelMode.NAMESPACED
         ? false
         : !this.raw().getStringList("commands.never-claim").stream().anyMatch(entry -> entry.equalsIgnoreCase(name));
   }

   public boolean economyEnabled() {
      return this.raw().getBoolean("economy.enabled", true);
   }

   public String currencySymbol() {
      return this.raw().getString("economy.currency-symbol", "$");
   }

   public int displayDecimals() {
      return Math.max(0, Math.min(4, this.raw().getInt("economy.display-decimals", 0)));
   }

   public boolean groupThousands() {
      return this.raw().getBoolean("economy.group-thousands", true);
   }

   public boolean moneyFormatShort() {
      String mode = this.raw().getString("economy.money-format", "short");
      return mode == null || !mode.equalsIgnoreCase("full");
   }

   public int moneyShortDecimals() {
      return Math.max(0, Math.min(2, this.raw().getInt("economy.short-decimals", 2)));
   }

   public boolean balanceAllowOthers() {
      return this.raw().getBoolean("economy.balance.allow-others", true);
   }

   public boolean payEnabled() {
      return this.raw().getBoolean("economy.pay.enabled", true);
   }

   public double payMinimum() {
      return this.raw().getDouble("economy.pay.minimum", 1.0);
   }

   public long payCooldownMillis() {
      return Math.max(0L, this.raw().getLong("economy.pay.cooldown-millis", 750L));
   }

   public boolean payAllowOfflineTarget() {
      return this.raw().getBoolean("economy.pay.allow-offline-target", false);
   }

   public boolean rtpEnabled() {
      return this.raw().getBoolean("rtp.enabled", true);
   }

   public String rtpWorld() {
      String world = this.raw().getString("rtp.world", "world");
      return world != null && !world.isBlank() ? world.trim() : "world";
   }

   public int rtpMinRadius() {
      return Math.max(0, this.raw().getInt("rtp.min-radius", 500));
   }

   public int rtpMaxRadius() {
      return Math.max(this.rtpMinRadius(), this.raw().getInt("rtp.max-radius", 10000));
   }

   public int rtpCooldownSeconds() {
      return Math.max(0, this.raw().getInt("rtp.cooldown", 60));
   }

   public int rtpTeleportDelay() {
      return Math.max(0, this.raw().getInt("rtp.teleport-delay", 5));
   }

   public int rtpMaxAttempts() {
      return Math.max(1, this.raw().getInt("rtp.max-attempts", 40));
   }

   public boolean rtpActionBar() {
      return this.raw().getBoolean("rtp.action-bar", true);
   }

   public boolean rtpCancelOnMove() {
      return this.raw().getBoolean("rtp.cancel-on-move", true);
   }

   public boolean rtpCancelOnDamage() {
      return this.raw().getBoolean("rtp.cancel-on-damage", true);
   }

   public boolean rtpCancelOnDeath() {
      return this.raw().getBoolean("rtp.cancel-on-death", true);
   }

   public int homeFreeSlots() {
      return Math.max(0, this.raw().getInt("home.free-slots", 3));
   }

   public int homeMaxSlots() {
      return Math.max(1, this.raw().getInt("home.max-slots", 8));
   }

   public int homePrice(int slotNumber) {
      return Math.max(0, this.raw().getInt("home.prices." + slotNumber, 0));
   }

   public int homeTeleportDelay() {
      return Math.max(0, this.raw().getInt("home.teleport-delay", 3));
   }

   public boolean homeTeleportActionBar() {
      return this.raw().getBoolean("home.teleport-action-bar", true);
   }

   public boolean homeCancelOnMove() {
      return this.raw().getBoolean("home.cancel-on-move", true);
   }

   public boolean homeCancelOnDamage() {
      return this.raw().getBoolean("home.cancel-on-damage", true);
   }

   public boolean homeCancelOnDeath() {
      return this.raw().getBoolean("home.cancel-on-death", true);
   }

   public boolean homeCancelOnCombat() {
      return this.raw().getBoolean("home.cancel-on-combat", true);
   }

   public boolean homeConfirmPurchase() {
      return this.raw().getBoolean("home.confirm-purchase", true);
   }

   public boolean homeConfirmDelete() {
      return this.raw().getBoolean("home.confirm-delete", true);
   }

   public boolean sellEnabled() {
      return this.raw().getBoolean("sell.enabled", true);
   }

   public long sellMaxTotal() {
      return Math.max(0L, this.raw().getLong("sell.max-total-per-transaction", 0L));
   }

   public boolean sellActionBar() {
      return this.raw().getBoolean("sell.action-bar", true);
   }

   public boolean combatEnabled() {
      return this.raw().getBoolean("combat.enabled", true);
   }

   public int combatDurationSeconds() {
      return Math.max(1, this.raw().getInt("combat.duration", 15));
   }

   public boolean combatTagOnAttack() {
      return this.raw().getBoolean("combat.tag-on-attack", true);
   }

   public boolean combatTagMobs() {
      return this.raw().getBoolean("combat.tag-mobs", false);
   }

   public boolean combatBlockTeleport() {
      return this.raw().getBoolean("combat.block-teleport", true);
   }

   public boolean combatCancelTeleportOnTag() {
      return this.raw().getBoolean("combat.cancel-teleport-on-tag", true);
   }

   public List<String> combatBlockedCommands() {
      List<String> labels = new ArrayList<>();

      for (String entry : this.raw().getStringList("combat.block-commands")) {
         String label = entry == null ? "" : entry.trim().toLowerCase(Locale.ROOT);
         if (!label.isEmpty() && !label.startsWith("#")) {
            labels.add(label.startsWith("/") ? label.substring(1) : label);
         }
      }

      return labels;
   }

   public boolean combatActionBar() {
      return this.raw().getBoolean("combat.actionbar", true);
   }

   public boolean combatPenaltyEnabled() {
      return this.raw().getBoolean("combat.penalty.enabled", true);
   }

   public boolean combatPenaltyKill() {
      String mode = this.raw().getString("combat.penalty.mode", "KILL");
      return mode == null || !mode.trim().equalsIgnoreCase("NONE");
   }

   public boolean combatPenaltyDropInventory() {
      return this.raw().getBoolean("combat.penalty.drop-inventory", true);
   }

   public boolean combatPenaltyBroadcast() {
      return this.raw().getBoolean("combat.penalty.broadcast", true);
   }

   public boolean auctionEnabled() {
      return this.raw().getBoolean("auction.enabled", true);
   }

   public int auctionTaxPercent() {
      return Math.clamp(this.raw().getInt("auction.tax", 5), 0, 100);
   }

   public long auctionListingFee() {
      return Math.max(0L, this.raw().getLong("auction.listing-fee", 0L));
   }

   public int auctionExpirationHours() {
      return Math.max(1, this.raw().getInt("auction.expiration-hours", 48));
   }

   public int auctionMaxListings() {
      return Math.max(1, this.raw().getInt("auction.max-listings", 10));
   }

   public long auctionMinPrice() {
      return Math.max(1L, this.raw().getLong("auction.min-price", 10L));
   }

   public long auctionMaxPrice() {
      return Math.max(0L, this.raw().getLong("auction.max-price", 1000000L));
   }

   public boolean auctionAllowSelfPurchase() {
      return this.raw().getBoolean("auction.allow-self-purchase", false);
   }

   public int auctionRows() {
      return Math.clamp(this.raw().getInt("auction.rows", 6), 3, 6);
   }

   public int auctionPageSize() {
      return Math.max(0, this.raw().getInt("auction.page-size", 0));
   }

   public int auctionExpiryCheckSeconds() {
      return Math.max(5, this.raw().getInt("auction.expiry-check-seconds", 60));
   }

   public int auctionMailboxLimit() {
      return Math.max(1, this.raw().getInt("auction.mailbox-limit", 100));
   }

   public List<Material> auctionBlacklist() {
      List<Material> materials = new ArrayList<>();

      for (String entry : this.raw().getStringList("auction.blacklist")) {
         if (entry != null && !entry.isBlank() && !entry.trim().startsWith("#")) {
            Material material = Material.matchMaterial(entry.trim());
            if (material == null) {
               this.plugin.getLogger().warning("auction.blacklist memuat nama material tidak dikenal: " + entry);
            } else {
               materials.add(material);
            }
         }
      }

      return materials;
   }

   public boolean worthEnabled() {
      return this.raw().getBoolean("worth.enabled", true);
   }

   public boolean worthInventoryLore() {
      return this.worthEnabled() && this.raw().getBoolean("worth.inventory-lore", true);
   }

   public boolean worthShowTotal() {
      return this.raw().getBoolean("worth.lore.show-total", false);
   }

   public boolean worthSellGui() {
      return this.worthEnabled() && this.raw().getBoolean("worth.sell-gui", true);
   }

   public boolean worthMergeStacks() {
      return this.raw().getBoolean("worth.merge-stacks", true);
   }

   public boolean worthShowUnsellable() {
      return this.raw().getBoolean("worth.show-unsellable", true);
   }

   public int worthRefreshSeconds() {
      return Math.max(0, this.raw().getInt("worth.refresh-seconds", 3));
   }

   public boolean worthPersonalToggle() {
      return this.raw().getBoolean("worth.personal-toggle", true);
   }

   public boolean worthSkipItemsWithCustomLore() {
      return this.raw().getBoolean("worth.skip-items-with-custom-lore", false);
   }

   public boolean worthLoreAtTop() {
      return "TOP".equalsIgnoreCase(this.raw().getString("worth.lore-position", "BOTTOM"));
   }

   public List<Material> worthBlacklist() {
      List<Material> materials = new ArrayList<>();

      for (String entry : this.raw().getStringList("worth.blacklist")) {
         if (entry != null && !entry.isBlank() && !entry.trim().startsWith("#")) {
            Material material = Material.matchMaterial(entry.trim());
            if (material == null) {
               this.plugin.getLogger().warning("worth.blacklist memuat nama material tidak dikenal: " + entry);
            } else {
               materials.add(material);
            }
         }
      }

      return materials;
   }

   public boolean statisticsEnabled() {
      return this.raw().getBoolean("statistics.enabled", true);
   }

   public String statisticsStorage() {
      return this.raw().getString("statistics.storage", "YAML");
   }

   public int statisticsAutosaveMinutes() {
      return Math.max(0, this.raw().getInt("statistics.autosave-minutes", 5));
   }

   public boolean statisticsTrackBlocks() {
      return this.statisticsEnabled() && this.raw().getBoolean("statistics.track-blocks", true);
   }

   public boolean statisticsTrackPlaytime() {
      return this.statisticsEnabled() && this.raw().getBoolean("statistics.track-playtime", true);
   }

   /**
    * Interval penyimpanan playtime sesi berjalan ke data (statistics.playtime-flush-seconds).
    * Tanpa flush berkala, playtime hanya tercatat saat pemain keluar sehingga hilang bila
    * server mati paksa. 0 = hanya saat keluar/shutdown.
    */
   public int statisticsPlaytimeFlushSeconds() {
      return Math.max(0, this.raw().getInt("statistics.playtime-flush-seconds", 60));
   }

   public int leaderboardEntriesPerPage() {
      return Math.min(45, Math.max(9, this.raw().getInt("leaderboard.entries-per-page", 28)));
   }

   public String leaderboardDefaultCategory() {
      return this.raw().getString("leaderboard.default-category", "money");
   }

   public int leaderboardChatLines() {
      return Math.min(50, Math.max(1, this.raw().getInt("leaderboard.chat-lines", 10)));
   }

   /**
    * Umur cache papan peringkat (detik). Sebelumnya cache dibuang setiap ada satu statistik
    * bertambah (mis. satu blok ditambang), sehingga peringkat dihitung ulang terus-menerus
    * di main thread. Kini cache hanya kedaluwarsa berdasarkan waktu.
    */
   public int leaderboardCacheSeconds() {
      return Math.max(1, this.raw().getInt("leaderboard.cache-seconds", 15));
   }

   /** Jumlah maksimum baris yang disimpan per kategori papan peringkat. */
   public int leaderboardMaxEntries() {
      return Math.max(10, this.raw().getInt("leaderboard.max-entries", 1000));
   }

   /**
    * Bolehkah saldo pemain offline di-cache? Membaca saldo offline lewat Vault/Essentials bisa
    * menyentuh disk, jadi hasilnya disimpan dan disegarkan saat pemain masuk/keluar.
    */
   public boolean leaderboardCacheOfflineBalance() {
      return this.raw().getBoolean("leaderboard.cache-offline-balance", true);
   }

   /** Teks untuk pemain yang belum masuk peringkat (dipakai placeholder %rank-*%). */
   public String leaderboardUnrankedText() {
      String text = this.raw().getString("leaderboard.unranked-text", "-");
      return text == null || text.isEmpty() ? "-" : text;
   }

   public boolean scoreboardEnabled() {
      return this.raw().getBoolean("scoreboard.enabled", true);
   }

   public String scoreboardTitle() {
      return this.raw().getString("scoreboard.title", "&b&lW2NSMP");
   }

   public int scoreboardUpdateTicks() {
      return Math.max(5, this.raw().getInt("scoreboard.update-ticks", 20));
   }

   public List<String> scoreboardLines() {
      return this.raw().getStringList("scoreboard.lines");
   }

   public boolean scoreboardPersonalToggle() {
      return this.raw().getBoolean("scoreboard.personal-toggle", true);
   }

   public boolean compatibilityReportOnStartup() {
      return this.raw().getBoolean("compatibility.report-on-startup", true);
   }

   public boolean compatibilityLiveAudit() {
      return this.raw().getBoolean("compatibility.live-audit", true);
   }

   public boolean guiConfigEnabled() {
      return this.raw().getBoolean("gui.enabled", true);
   }

   public boolean profileEnabled() {
      return this.raw().getBoolean("profile.enabled", true);
   }

   public boolean settingsEnabled() {
      return this.raw().getBoolean("setting.enabled", true);
   }

   public boolean soundsEnabled() {
      return this.raw().getBoolean("sounds.enabled", true);
   }

   public boolean soundsDefault() {
      return this.raw().getBoolean("sounds.default", true);
   }

   public boolean notificationsDefault() {
      return this.raw().getBoolean("notifications.default", true);
   }

   public boolean notificationsBountyDefault() {
      return this.raw().getBoolean("notifications.bounty-default", true);
   }

   public boolean notificationsAuctionDefault() {
      return this.raw().getBoolean("notifications.auction-default", true);
   }

   public boolean notificationsTpaDefault() {
      return this.raw().getBoolean("notifications.tpa-default", true);
   }

   public boolean teleportCountdownDefault() {
      return this.raw().getBoolean("teleport.countdown-default", true);
   }

   /** v1.5.1: gate admin Night Vision /setting (false = toggle dikunci untuk semua pemain). */
   public boolean nightVisionEnabled() {
      return this.raw().getBoolean("night-vision.enabled", true);
   }

   /** v1.5.1: pilihan bawaan Night Vision untuk pemain yang belum pernah mengubahnya. */
   public boolean nightVisionDefaultOn() {
      return this.raw().getBoolean("night-vision.default", false);
   }

   public boolean nametagMoneyEnabled() {
      return this.raw().getBoolean("nametag.money.enabled", true);
   }

   public boolean nametagMoneyDefaultOn() {
      return this.raw().getBoolean("nametag.money.default-on", true);
   }

   public String nametagMoneyFormat() {
      return this.raw().getString("nametag.money.format", " &7| &a%balance%");
   }

   public boolean nametagMoneyNewLine() {
      return this.raw().getBoolean("nametag.money.new-line", false);
   }

   /** v1.6.2: tinggi teks uang di atas kepala (blok, relatif ke titik dudukan passenger). */
   public double nametagMoneyHeight() {
      return Math.max(0.0, Math.min(3.0, this.raw().getDouble("nametag.money.height", 0.72)));
   }

   public boolean nametagMoneyRespectTeams() {
      return this.raw().getBoolean("nametag.money.respect-existing-teams", true);
   }

   public int nametagMoneyUpdateSeconds() {
      return Math.max(0, this.raw().getInt("nametag.money.update-seconds", 30));
   }

   public int statisticsHighestMoneySeconds() {
      return Math.max(0, this.raw().getInt("statistics.highest-money-seconds", 60));
   }

   public boolean tpaEnabled() {
      return this.raw().getBoolean("tpa.enabled", true);
   }

   public int tpaTeleportDelay() {
      return Math.max(0, this.raw().getInt("tpa.teleport-delay", 3));
   }

   public int tpaRequestTimeoutSeconds() {
      return Math.max(5, this.raw().getInt("tpa.request-timeout-seconds", 30));
   }

   public int tpaCooldownSeconds() {
      return Math.max(0, this.raw().getInt("tpa.cooldown-seconds", 5));
   }

   public int tpaMaxIncoming() {
      return Math.max(1, this.raw().getInt("tpa.max-incoming", 5));
   }

   public int tpaMaxOutgoing() {
      return Math.max(1, this.raw().getInt("tpa.max-outgoing", 5));
   }

   public boolean tpaCancelOnMove() {
      return this.raw().getBoolean("tpa.cancel-on-move", true);
   }

   public boolean tpaCancelOnDamage() {
      return this.raw().getBoolean("tpa.cancel-on-damage", true);
   }

   public boolean tpaCancelOnDeath() {
      return this.raw().getBoolean("tpa.cancel-on-death", true);
   }

   public boolean tpaBlockInCombat() {
      return this.raw().getBoolean("tpa.block-in-combat", true);
   }

   public boolean tpaActionBar() {
      return this.raw().getBoolean("tpa.action-bar", true);
   }

   public boolean bountyEnabled() {
      return this.raw().getBoolean("bounty.enabled", true);
   }

   public long bountyMinimum() {
      return Math.max(1L, this.raw().getLong("bounty.minimum", 1000L));
   }

   public long bountyMaximum() {
      return Math.max(this.bountyMinimum(), this.raw().getLong("bounty.maximum", 10000000L));
   }

   public long bountyMaxTotal() {
      return Math.max(this.bountyMaximum(), this.raw().getLong("bounty.max-total", 10000000L));
   }

   public double bountyTaxPercent() {
      return Math.max(0.0, Math.min(100.0, this.raw().getDouble("bounty.tax-percent", 0.0)));
   }

   public boolean bountyPreventSelf() {
      return this.raw().getBoolean("bounty.prevent-self", true);
   }

   public boolean bountyPreventRepeatedKills() {
      return this.raw().getBoolean("bounty.prevent-repeated-kills", true);
   }

   public int bountyRepeatKillSeconds() {
      return Math.max(0, this.raw().getInt("bounty.repeat-kill-cooldown-seconds", 600));
   }

   public int bountyMinVictimPlaytimeMinutes() {
      return Math.max(0, this.raw().getInt("bounty.min-victim-playtime-minutes", 0));
   }

   public boolean bountyBlockSameIp() {
      return this.raw().getBoolean("bounty.block-same-ip", true);
   }

   public long bountyAnnounceMinimum() {
      return Math.max(0L, this.raw().getLong("bounty.announce-minimum", 5000L));
   }

   public int bountyPageSize() {
      return Math.max(1, Math.min(45, this.raw().getInt("bounty.page-size", 28)));
   }

   public boolean dynamicEconomyEnabled() {
      return this.raw().getBoolean("dynamic-economy.enabled", true);
   }

   public double dynamicMinMultiplier() {
      double configured = this.raw().getDouble("dynamic-economy.min-multiplier", 0.5);
      return Math.max(0.01, Math.min(1.0, configured));
   }

   public double dynamicMaxMultiplier() {
      double configured = this.raw().getDouble("dynamic-economy.max-multiplier", 2.0);
      return Math.max(this.dynamicMinMultiplier(), Math.min(100.0, configured));
   }

   public int dynamicUpdateIntervalSeconds() {
      return Math.max(1, this.raw().getInt("dynamic-economy.update-interval", 60));
   }

   public double dynamicSellImpactStrength() {
      return Math.max(0.0, this.raw().getDouble("dynamic-economy.sell-impact.strength", 0.01));
   }

   public double dynamicRecoveryRate() {
      double configured = this.raw().getDouble("dynamic-economy.recovery-rate", 0.05);
      return Math.max(0.0, Math.min(1.0, configured));
   }

   public List<Long> bountyPresets() {
      List<Long> configured = new ArrayList<>();

      for (Object raw : this.raw().getList("bounty.presets", List.of(1000, 5000, 10000, 25000, 50000, 100000))) {
         if (raw instanceof Number number) {
            long value = number.longValue();
            if (value > 0L && !configured.contains(value)) {
               configured.add(value);
            }
         }
      }

      if (configured.isEmpty()) {
         configured.addAll(List.of(1000L, 5000L, 10000L, 25000L, 50000L, 100000L));
      }

      configured.sort(Long::compareTo);
      return configured;
   }

   public enum PlainLabelMode {
      PREFER,
      AUTO,
      NAMESPACED;
   }
}
