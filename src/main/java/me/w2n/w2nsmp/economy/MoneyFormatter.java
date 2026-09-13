package me.w2n.w2nsmp.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class MoneyFormatter {
   private static final String[] SUFFIXES = new String[]{"", "k", "M", "B", "T"};
   public static final int SHORT_DECIMALS = 2;
   public static final double SHORT_THRESHOLD = 1000.0;
   private static final Map<String, BigDecimal> INPUT_SUFFIXES = new LinkedHashMap<>();
   public static final double MAX_INPUT = 1.0E15;
   private final String symbol;
   private final boolean shortMode;
   private final int decimals;
   private final int shortDecimals;
   private final boolean groupThousands;

   public MoneyFormatter(String symbol, boolean shortMode, int decimals, boolean groupThousands) {
      this(symbol, shortMode, decimals, 2, groupThousands);
   }

   public MoneyFormatter(String symbol, boolean shortMode, int decimals, int shortDecimals, boolean groupThousands) {
      super();
      this.symbol = symbol == null ? "" : symbol;
      this.shortMode = shortMode;
      this.decimals = Math.max(0, Math.min(4, decimals));
      this.shortDecimals = Math.max(0, Math.min(2, shortDecimals));
      this.groupThousands = groupThousands;
   }

   public String symbol() {
      return this.symbol;
   }

   public boolean shortMode() {
      return this.shortMode;
   }

   public String format(double amount) {
      return this.withSymbol(this.shortMode ? shorten(amount, this.shortDecimals) : plain(amount, this.decimals, this.groupThousands));
   }

   public String exact(double amount) {
      return this.withSymbol(plain(amount, this.decimals, this.groupThousands));
   }

   public String withSymbol(String number) {
      return this.symbol + number;
   }

   public static String abbreviate(double amount) {
      return shorten(amount, 2);
   }

   private static String shorten(double amount, int decimals) {
      boolean negative = amount < 0.0 || amount == 0.0 && 1.0 / amount < 0.0;
      double value = Math.abs(amount);
      if (value < 1000.0) {
         String small = trimmed(value, decimals);
         return negative ? "-" + small : small;
      }

      int tier = 0;

      double scaled;
      for (scaled = value; scaled >= 1000.0 && tier < SUFFIXES.length - 1; tier++) {
         scaled /= 1000.0;
      }

      String text = trimmed(scaled, decimals) + SUFFIXES[tier];
      return negative ? "-" + text : text;
   }

   public static String plain(double amount, int decimals, boolean groupThousands) {
      int scale = Math.max(0, Math.min(4, decimals));
      BigDecimal rounded = BigDecimal.valueOf(amount).setScale(scale, RoundingMode.HALF_UP);
      String digits = rounded.toPlainString();
      if (!groupThousands) {
         return digits;
      }

      int dot = digits.lastIndexOf(46);
      String whole = dot < 0 ? digits : digits.substring(0, dot);
      String fraction = dot < 0 ? "" : digits.substring(dot);
      boolean negative = whole.startsWith("-");
      String body = negative ? whole.substring(1) : whole;
      StringBuilder grouped = new StringBuilder();

      for (int index = 0; index < body.length(); index++) {
         if (index > 0 && (body.length() - index) % 3 == 0) {
            grouped.append(',');
         }

         grouped.append(body.charAt(index));
      }

      return (negative ? "-" : "") + grouped + fraction;
   }

   public static BigDecimal parse(String raw) {
      if (raw == null) {
         return null;
      }

      String text = raw.trim().toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "");
      if (text.startsWith("$")) {
         text = text.substring(1);
      }

      if (text.isEmpty()) {
         return null;
      }

      int end = text.length();

      while (end > 0 && Character.isLetter(text.charAt(end - 1))) {
         end--;
      }

      String suffix = text.substring(end);
      String digits = text.substring(0, end);
      BigDecimal multiplier;
      if (suffix.isEmpty()) {
         multiplier = BigDecimal.ONE;
      } else {
         multiplier = INPUT_SUFFIXES.get(suffix);
         if (multiplier == null) {
            return null;
         }
      }

      if (digits.isEmpty()) {
         return null;
      }

      String normalized = normalizeDigits(digits);
      if (normalized == null) {
         return null;
      }

      BigDecimal value = new BigDecimal(normalized).multiply(multiplier);
      return value.abs().compareTo(BigDecimal.valueOf(1.0E15)) > 0 ? null : value;
   }

   public static long parseLongAmount(String raw) {
      BigDecimal value = parse(raw);
      if (value == null) {
         return -1L;
      }

      BigDecimal whole = value.setScale(0, RoundingMode.DOWN);
      return whole.abs().compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0 ? -1L : whole.longValue();
   }

   public static double parseAmount(String raw) {
      BigDecimal value = parse(raw);
      return value == null ? Double.NaN : value.doubleValue();
   }

   private static String normalizeDigits(String digits) {
      int lastSeparator = Math.max(digits.lastIndexOf(46), digits.lastIndexOf(44));
      if (lastSeparator < 0) {
         return digits.matches("\\d+") ? digits : null;
      }

      String after = digits.substring(lastSeparator + 1);
      boolean grouping = after.length() == 3 && after.chars().allMatch(Character::isDigit);
      StringBuilder cleaned = new StringBuilder(digits.length());

      for (int index = 0; index < digits.length(); index++) {
         char character = digits.charAt(index);
         if (character != '.' && character != ',') {
            if (!Character.isDigit(character)) {
               return null;
            }

            cleaned.append(character);
         } else {
            cleaned.append((char)(!grouping && index == lastSeparator ? '.' : '\u0000'));
         }
      }

      String result = cleaned.toString().replace("\u0000", "");
      if (result.startsWith(".")) {
         result = "0" + result;
      }

      if (result.endsWith(".")) {
         result = result.substring(0, result.length() - 1);
      }

      return result.matches("\\d+(\\.\\d+)?") ? result : null;
   }

   private static String trimmed(double value, int decimals) {
      BigDecimal rounded = BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP);
      BigDecimal stripped = rounded.stripTrailingZeros();
      return stripped.scale() < 0 ? stripped.setScale(0, RoundingMode.UNNECESSARY).toPlainString() : stripped.toPlainString();
   }

   static {
      BigDecimal thousand = BigDecimal.valueOf(1000L);
      BigDecimal million = BigDecimal.valueOf(1000000L);
      BigDecimal billion = BigDecimal.valueOf(1000000000L);
      BigDecimal trillion = BigDecimal.valueOf(1000000000000L);

      for (String suffix : new String[]{"k", "rb", "ribu", "thousand"}) {
         INPUT_SUFFIXES.put(suffix, thousand);
      }

      for (String suffix : new String[]{"m", "jt", "juta", "million"}) {
         INPUT_SUFFIXES.put(suffix, million);
      }

      for (String suffix : new String[]{"b", "miliar", "milyar", "billion"}) {
         INPUT_SUFFIXES.put(suffix, billion);
      }

      for (String suffix : new String[]{"t", "tn", "triliun", "trillion"}) {
         INPUT_SUFFIXES.put(suffix, trillion);
      }
   }
}
