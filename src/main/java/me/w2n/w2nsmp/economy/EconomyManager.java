package me.w2n.w2nsmp.economy;

import me.w2n.w2nsmp.W2NSMP;
import me.w2n.w2nsmp.config.ConfigManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public final class EconomyManager {
   private final W2NSMP plugin;
   private final ConfigManager config;
   private EconomyProvider provider;
   private MoneyFormatter formatter;
   private String currencySymbol;

   public EconomyManager(W2NSMP plugin, ConfigManager config, EconomyProvider provider) {
      super();
      this.plugin = plugin;
      this.config = config;
      this.setProvider(provider);
      this.reloadFormat();
   }

   public void setProvider(EconomyProvider provider) {
      this.provider = provider;
   }

   public void reloadFormat() {
      this.currencySymbol = this.config.currencySymbol();
      this.formatter = new MoneyFormatter(
         this.currencySymbol, this.config.moneyFormatShort(), this.config.displayDecimals(), this.config.moneyShortDecimals(), this.config.groupThousands()
      );
   }

   public boolean isEnabled() {
      return this.provider != null && this.config.economyEnabled();
   }

   public String providerName() {
      return this.provider == null ? null : this.provider.name();
   }

   public boolean hasAccount(OfflinePlayer player) {
      return this.isEnabled() && this.provider.hasAccount(player);
   }

   public double balance(OfflinePlayer player) {
      return this.isEnabled() ? this.provider.balance(player) : 0.0;
   }

   public boolean has(OfflinePlayer player, double amount) {
      return this.isEnabled() && this.provider.has(player, amount);
   }

   public boolean deposit(OfflinePlayer player, double amount) {
      if (this.isEnabled() && !(amount <= 0.0)) {
         boolean success = this.provider.deposit(player, amount);
         if (success) {
            this.afterBalanceChange(player);
         }

         return success;
      } else {
         return false;
      }
   }

   public boolean withdraw(OfflinePlayer player, double amount) {
      if (this.isEnabled() && !(amount <= 0.0)) {
         boolean success = this.provider.withdraw(player, amount);
         if (success) {
            this.afterBalanceChange(player);
         }

         return success;
      } else {
         return false;
      }
   }

   private void afterBalanceChange(OfflinePlayer player) {
      if (player instanceof Player online) {
         if (this.plugin.stats() != null) {
            this.plugin.stats().trackHighestMoney(online);
         }

         if (this.plugin.nametag() != null) {
            this.plugin.nametag().refresh(online);
         }
      }
   }

   public TransferResult transfer(OfflinePlayer from, OfflinePlayer to, double amount) {
      if (!this.isEnabled()) {
         return TransferResult.DISABLED;
      }

      if (!(amount > 0.0) || !Double.isFinite(amount)) {
         return TransferResult.INVALID_AMOUNT;
      }

      if (!this.provider.hasAccount(from) || !this.provider.hasAccount(to)) {
         return TransferResult.ACCOUNT_NOT_FOUND;
      }

      if (!this.provider.has(from, amount)) {
         return TransferResult.INSUFFICIENT_FUNDS;
      }

      if (!this.provider.withdraw(from, amount)) {
         return TransferResult.FAILED;
      }

      if (!this.provider.deposit(to, amount)) {
         boolean refunded = this.provider.deposit(from, amount);
         if (refunded) {
            this.plugin.getLogger().warning("Transfer gagal saat kredit ke " + to.getUniqueId() + "; dana dikembalikan ke " + from.getUniqueId() + ".");
         } else {
            this.plugin
               .getLogger()
               .severe("ROLLBACK GAGAL! " + amount + " dari " + from.getUniqueId() + " ke " + to.getUniqueId() + " perlu dikoreksi manual (/eco).");
         }

         return TransferResult.FAILED;
      } else {
         this.afterBalanceChange(from);
         this.afterBalanceChange(to);
         return TransferResult.SUCCESS;
      }
   }

   public String format(double amount) {
      return this.formatter.format(amount);
   }

   public String formatExact(double amount) {
      return this.formatter.exact(amount);
   }

   public String formatShort(double amount) {
      return MoneyFormatter.abbreviate(amount);
   }

   public MoneyFormatter moneyFormatter() {
      return this.formatter;
   }
}
