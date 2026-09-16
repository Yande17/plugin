package me.w2n.w2nsmp.hook;

import java.util.Locale;
import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.economy.EconomyProvider;
import me.w2n.w2nsmp.economy.VaultEconomyProvider;
import org.bukkit.Bukkit;

public final class VaultHook {
   private final W2NSMP plugin;
   private EconomyProvider provider;
   private String providerName;

   public VaultHook(W2NSMP plugin) {
      super();
      this.plugin = plugin;
   }

   public void register() {
      this.provider = null;
      this.providerName = null;
      if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
         this.plugin.getLogger().info("Vault not found. Economy features disabled.");
      } else {
         EconomyProvider detected = VaultEconomyProvider.create(this.plugin);
         if (detected == null) {
            this.plugin.getLogger().warning("Vault ditemukan, tetapi belum ada provider ekonomi (mis. EssentialsX Economy). Economy features disabled.");
         } else {
            this.provider = detected;
            this.providerName = detected.name();
            this.plugin.getLogger().info("Vault detected.");
            this.plugin.getLogger().info("Economy provider: " + this.providerName);
            if (this.providerName.toLowerCase(Locale.ROOT).contains("essentials")) {
               this.plugin.getLogger().info("EssentialsX economy detected.");
            }
         }
      }
   }

   public EconomyProvider provider() {
      return this.provider;
   }

   public boolean isAvailable() {
      return this.provider != null;
   }

   public String providerName() {
      return this.providerName;
   }
}
