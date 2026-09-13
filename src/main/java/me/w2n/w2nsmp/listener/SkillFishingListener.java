package me.w2n.w2nsmp.listener;

import java.util.HashMap;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.skill.SkillService;
import me.w2n.w2nsmp.skill.SkillSettings;
import me.w2n.w2nsmp.skill.SkillType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

/**
 * XP memancing + buff "hasil ganda".
 *
 * <p>Dipisahkan dari {@link SkillListener} karena {@code PlayerFishEvent} adalah satu-satunya
 * kelas event yang tidak dipakai fitur lama plugin ini. {@code ListenerManager} hanya
 * mendaftarkan listener ini bila kelas event itu benar-benar ada di server
 * ({@link SkillApiProbe#fishingEventApi()}). Dengan begitu, seandainya event itu tidak ada di
 * versi server tertentu, yang mati hanya skill fishing - plugin tidak gagal dimuat.
 */
public final class SkillFishingListener implements Listener {
   private final W2NSMP plugin;

   public SkillFishingListener(W2NSMP plugin) {
      this.plugin = plugin;
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onFish(PlayerFishEvent event) {
      SkillService service = this.plugin.skills();
      if (service == null || !service.enabled() || !service.probe().fishingEventApi()) {
         return;
      }

      SkillSettings settings = service.settings(SkillType.FISHING);
      if (!settings.enabled()) {
         return;
      }

      Object caught;
      Player player;
      try {
         caught = event.getCaught();
         player = event.getPlayer();
      } catch (Throwable throwable) {
         return;
      }

      // getCaught() hanya berisi entity saat ada yang tersangkut; tangkapan ikan/harta berupa Item.
      // Kail yang menancap di tanah, tarikan gagal, atau entity yang terkait (mis. pemain lain)
      // tidak memberi XP memancing.
      if (!(caught instanceof Item item) || player == null) {
         return;
      }

      if (!service.worldAllowed(player.getWorld()) || !service.gameModeAllowed(player)) {
         return;
      }

      if (settings.xpPerCatch() > 0.0D) {
         service.addXp(player, SkillType.FISHING, settings.xpPerCatch());
      }

      if (service.rollChance(player, SkillType.FISHING)) {
         this.extraCatch(player, item, service);
      }
   }

   private void extraCatch(Player player, Item item, SkillService service) {
      try {
         ItemStack stack = item.getItemStack();
         if (stack == null || stack.getType() == null || stack.getType().isAir()) {
            return;
         }

         ItemStack copy = stack.clone();
         if (copy == null) {
            return;
         }

         HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(copy);
         if (leftover != null) {
            for (ItemStack rest : leftover.values()) {
               if (rest != null && player.getWorld() != null) {
                  player.getWorld().dropItemNaturally(player.getLocation(), rest);
               }
            }
         }

         if (service.notifyExtraDrop()) {
            this.plugin.messages().send(player, "skill.extra-drop", "skill", service.label(SkillType.FISHING));
         }
      } catch (Throwable throwable) {
         this.plugin.debug("Skill: gagal memberi hasil pancingan tambahan (" + throwable + ").");
      }
   }
}
