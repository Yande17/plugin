package me.w2n.w2nsmp.hook;

import me.w2n.w2nsmp.W2NSMP;

public final class HookManager {
   private final W2NSMP plugin;
   private final VaultHook vaultHook;
   private final BedrockHook bedrockHook;

   public HookManager(W2NSMP plugin) {
      super();
      this.plugin = plugin;
      this.vaultHook = new VaultHook(plugin);
      this.bedrockHook = new BedrockHook(plugin);
   }

   public void registerAll() {
      this.vaultHook.register();
      this.bedrockHook.register();
   }

   public BedrockHook bedrock() {
      return this.bedrockHook;
   }

   public VaultHook vault() {
      return this.vaultHook;
   }

   public W2NSMP plugin() {
      return this.plugin;
   }
}
