package me.w2n.w2nsmp.economy;

import me.w2n.w2nsmp.W2NSMP;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class VaultEconomyProvider implements EconomyProvider {
   private final Economy economy;
   private final String name;

   private VaultEconomyProvider(Economy economy) {
      super();
      this.economy = economy;
      this.name = economy.getName() == null ? "Vault" : economy.getName();
   }

   public static VaultEconomyProvider create(W2NSMP plugin) {
      RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
      if (registration == null) {
         return null;
      }

      Economy provider = (Economy)registration.getProvider();
      if (provider == null) {
         return null;
      }

      if (registration.getPlugin() != null) {
         plugin.getLogger().info("Economy service registered by " + registration.getPlugin().getName() + ".");
      }

      return new VaultEconomyProvider(provider);
   }

   @Override
   public String name() {
      return this.name;
   }

   @Override
   public boolean hasAccount(OfflinePlayer player) {
      try {
         return this.economy.hasAccount(player);
      } catch (RuntimeException exception) {
         this.logFailure("hasAccount", player, exception);
         return false;
      }
   }

   @Override
   public double balance(OfflinePlayer player) {
      try {
         return this.economy.getBalance(player);
      } catch (RuntimeException exception) {
         this.logFailure("getBalance", player, exception);
         return 0.0;
      }
   }

   @Override
   public boolean has(OfflinePlayer player, double amount) {
      try {
         return this.economy.has(player, amount);
      } catch (RuntimeException exception) {
         this.logFailure("has", player, exception);
         return false;
      }
   }

   @Override
   public boolean withdraw(OfflinePlayer player, double amount) {
      EconomyResponse response = this.economy.withdrawPlayer(player, amount);
      return response != null && response.transactionSuccess();
   }

   @Override
   public boolean deposit(OfflinePlayer player, double amount) {
      EconomyResponse response = this.economy.depositPlayer(player, amount);
      return response != null && response.transactionSuccess();
   }

   private void logFailure(String operation, OfflinePlayer player, RuntimeException exception) {
      W2NSMP plugin = W2NSMP.get();
      if (plugin != null) {
         plugin.getLogger().warning("Vault " + operation + " gagal untuk " + player.getUniqueId() + ": " + exception.getMessage());
      }
   }
}
