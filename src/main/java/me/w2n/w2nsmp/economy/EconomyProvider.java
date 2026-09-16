package me.w2n.w2nsmp.economy;

import org.bukkit.OfflinePlayer;

public interface EconomyProvider {
   String name();

   boolean hasAccount(OfflinePlayer var1);

   double balance(OfflinePlayer var1);

   boolean has(OfflinePlayer var1, double var2);

   boolean withdraw(OfflinePlayer var1, double var2);

   boolean deposit(OfflinePlayer var1, double var2);
}
