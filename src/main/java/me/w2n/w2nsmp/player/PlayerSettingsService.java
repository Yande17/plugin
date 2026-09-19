package me.w2n.w2nsmp.player;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import me.w2n.w2nsmp.W2NSMP;
import org.bukkit.entity.Player;

public final class PlayerSettingsService {
   private final W2NSMP plugin;
   private final SettingsStorage storage;
   private final Map<UUID, PlayerSettings> cache = new HashMap<>();
   private boolean loaded;

   public PlayerSettingsService(W2NSMP plugin) {
      super();
      this.plugin = plugin;
      this.storage = new SettingsStorage(plugin);
   }

   public boolean load() {
      this.cache.clear();
      this.cache.putAll(this.storage.load());
      this.loaded = true;
      return true;
   }

   public void reload() {
      if (this.isDirty() && !this.storage.loadFailed()) {
         this.saveNow();
      }

      this.load();
   }

   public void saveNow() {
      this.storage.saveNow(this.cache);
   }

   public boolean isDirty() {
      return this.storage.isDirty();
   }

   public File file() {
      return this.storage.file();
   }

   public boolean loadFailed() {
      return this.storage.loadFailed();
   }

   public boolean isLoaded() {
      return this.loaded;
   }

   public int playerCount() {
      return this.cache.size();
   }

   public PlayerSettings of(UUID uniqueId) {
      return uniqueId == null ? new PlayerSettings(null) : this.cache.computeIfAbsent(uniqueId, PlayerSettings::new);
   }

   public PlayerSettings of(Player player) {
      return player == null ? new PlayerSettings(null) : this.of(player.getUniqueId());
   }

   public boolean set(Player player, String key, boolean value) {
      if (player != null && key != null) {
         PlayerSettings settings = this.of(player.getUniqueId());
         boolean changed = settings.set(key, value);
         if (changed) {
            this.storage.saveAsync(this.cache);
         }

         return changed;
      } else {
         return false;
      }
   }

   public boolean get(Player player, String key, boolean defaultValue) {
      return player == null ? defaultValue : this.of(player.getUniqueId()).get(key, defaultValue);
   }

   public boolean sounds(Player player) {
      return this.get(player, "sounds", this.plugin.config().soundsDefault());
   }

   public boolean notifications(Player player) {
      return this.get(player, "notifications", true);
   }

   public boolean bountyNotifications(Player player) {
      return this.notifications(player) && this.get(player, "notify-bounty", true);
   }

   public boolean auctionNotifications(Player player) {
      return this.notifications(player) && this.get(player, "notify-auction", true);
   }

   public boolean tpaNotifications(Player player) {
      return this.notifications(player) && this.get(player, "notify-tpa", true);
   }

   public boolean teleportCountdown(Player player) {
      return this.get(player, "teleport-countdown", true);
   }

   public boolean nametagMoney(Player player) {
      return this.get(player, "nametag-money", this.plugin.config().nametagMoneyDefaultOn());
   }

   /** Night Vision pribadi (v1.5.1). Bawaan mati; persist per UUID seperti setting lain. */
   public boolean nightVision(Player player) {
      return this.get(player, PlayerSettings.KEY_NIGHT_VISION, this.plugin.config().nightVisionDefaultOn());
   }

   public boolean forget(UUID uniqueId) {
      return uniqueId != null && this.cache.remove(uniqueId) != null;
   }
}
