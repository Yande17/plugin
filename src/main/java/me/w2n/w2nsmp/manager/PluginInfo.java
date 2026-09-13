package me.w2n.w2nsmp.manager;

import me.w2n.w2nsmp.W2NSMP;

public final class PluginInfo {
   public static final String NAME = "W2NSMP";
   public static final String PREFIX = "&8[&bW2NSMP&8] &r";
   public static final String API_TARGET = "26.2";
   public static final int REQUIRED_JAVA = 25;

   private PluginInfo() {
   }

   public static String version() {
      W2NSMP plugin = W2NSMP.get();
      return plugin == null ? "unknown" : plugin.getPluginMeta().getVersion();
   }
}
