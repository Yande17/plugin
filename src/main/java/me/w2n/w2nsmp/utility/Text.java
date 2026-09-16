package me.w2n.w2nsmp.utility;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;

public final class Text {
   private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.legacyAmpersand();

   private Text() {
   }

   public static Component color(String raw) {
      return raw != null && !raw.isEmpty() ? AMPERSAND.deserialize(raw) : Component.empty();
   }

   public static void send(CommandSender sender, String raw) {
      sender.sendMessage(color(raw));
   }

   public static String stripColor(String raw) {
      return raw != null && !raw.isEmpty() ? PlainTextComponentSerializer.plainText().serialize(color(raw)) : "";
   }

   public static Component itemName(String raw) {
      return color(raw).decoration(TextDecoration.ITALIC, false);
   }

   public static Component itemLore(String raw) {
      return color(raw).decoration(TextDecoration.ITALIC, false);
   }
}
