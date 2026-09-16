package me.w2n.w2nsmp.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import org.bukkit.plugin.java.JavaPlugin;

public final class ResourceMerger {
   private final JavaPlugin plugin;

   public ResourceMerger(JavaPlugin plugin) {
      super();
      this.plugin = plugin;
   }

   public int merge(String resourceName, File target) {
      if (!target.isFile()) {
         this.plugin.saveResource(resourceName, false);
         return -1;
      }

      List<String> bundled = readLines(this.plugin.getResource(resourceName));
      List<String> user = readFile(target);
      if (!bundled.isEmpty() && !user.isEmpty()) {
         List<ResourceMerger.Insert> inserts = new ArrayList<>();
         collectMissing(parse(bundled), parse(user), bundled, user, true, inserts);
         if (inserts.isEmpty()) {
            return 0;
         }

         inserts.sort(Comparator.comparingInt(ResourceMerger.Insert::index).reversed());
         List<String> result = new ArrayList<>(user);
         int added = 0;

         for (ResourceMerger.Insert insert : inserts) {
            int index = Math.max(0, Math.min(insert.index(), result.size()));
            List<String> text = new ArrayList<>(insert.lines());
            if (!text.isEmpty() && text.get(0).isEmpty() && index > 0 && result.get(index - 1).isBlank()) {
               text.remove(0);
            }

            result.addAll(index, text);
            added++;
         }

         this.write(target, result);
         return added;
      } else {
         return 0;
      }
   }

   private static void collectMissing(
      ResourceMerger.Node bundled,
      ResourceMerger.Node user,
      List<String> bundledLines,
      List<String> userLines,
      boolean topLevel,
      List<ResourceMerger.Insert> inserts
   ) {
      List<ResourceMerger.Node> children = bundled.children();

      for (int index = 0; index < children.size(); index++) {
         ResourceMerger.Node child = children.get(index);
         ResourceMerger.Node match = user.child(child.key());
         if (match == null) {
            int anchor = topLevel ? userLines.size() : insertFollowingBundledOrder(user, children, index, userLines);
            inserts.add(new ResourceMerger.Insert(anchor, blockWithSeparator(blockOf(bundledLines, child), topLevel)));
         } else {
            collectMissing(child, match, bundledLines, userLines, false, inserts);
         }
      }
   }

   private static int insertFollowingBundledOrder(ResourceMerger.Node parent, List<ResourceMerger.Node> bundledKids, int missingIndex, List<String> userLines) {
      for (int index = missingIndex - 1; index >= 0; index--) {
         ResourceMerger.Node previous = parent.child(bundledKids.get(index).key());
         if (previous != null) {
            return endOfBlock(previous, userLines);
         }
      }

      return Math.min(parent.lineIndex() + 1, userLines.size());
   }

   private static int endOfBlock(ResourceMerger.Node node, List<String> userLines) {
      int index;
      for (index = Math.min(node.end(), userLines.size()); index > 0; index--) {
         String line = userLines.get(index - 1).trim();
         if (!line.isEmpty() && !line.startsWith("#")) {
            break;
         }
      }

      return index;
   }

   private static List<String> blockWithSeparator(List<String> block, boolean separator) {
      if (!separator) {
         return new ArrayList<>(block);
      }

      List<String> text = new ArrayList<>(block.size() + 1);
      text.add("");
      text.addAll(block);
      return text;
   }

   private static List<String> blockOf(List<String> lines, ResourceMerger.Node node) {
      int start;
      for (start = node.lineIndex(); start > 0; start--) {
         String previous = lines.get(start - 1).trim();
         if (!previous.isEmpty() && !previous.startsWith("#")) {
            break;
         }
      }

      List<String> block = new ArrayList<>(lines.subList(start, Math.min(node.end(), lines.size())));

      while (!block.isEmpty() && block.get(0).isBlank()) {
         block.remove(0);
      }

      while (!block.isEmpty() && block.get(block.size() - 1).isBlank()) {
         block.remove(block.size() - 1);
      }

      return block;
   }

   private static ResourceMerger.Node parse(List<String> lines) {
      ResourceMerger.Node root = new ResourceMerger.Node("", -1, -1);
      Deque<ResourceMerger.Node> stack = new ArrayDeque<>();
      stack.push(root);

      for (int index = 0; index < lines.size(); index++) {
         String line = lines.get(index);
         String trimmed = line.trim();
         if (!trimmed.isEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("-")) {
            int colon = trimmed.indexOf(58);
            if (colon > 0) {
               int indent = indentOf(line);
               String key = trimmed.substring(0, colon).trim();

               while (stack.size() > 1 && stack.peek().indent() >= indent) {
                  stack.pop().end(index);
               }

               ResourceMerger.Node node = new ResourceMerger.Node(key, indent, index);
               stack.peek().children().add(node);
               stack.push(node);
            }
         }
      }

      while (!stack.isEmpty()) {
         stack.pop().end(lines.size());
      }

      return root;
   }

   private static int indentOf(String line) {
      int indent = 0;

      while (indent < line.length() && line.charAt(indent) == ' ') {
         indent++;
      }

      return indent;
   }

   private static List<String> readLines(InputStream stream) {
      if (stream == null) {
         return List.of();
      }

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
         List<String> lines = new ArrayList<>();

         String line;
         while ((line = reader.readLine()) != null) {
            lines.add(line);
         }

         return lines;
      } catch (IOException exception) {
         return List.of();
      }
   }

   private static List<String> readFile(File file) {
      try {
         return new ArrayList<>(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
      } catch (IOException exception) {
         return List.of();
      }
   }

   private void write(File target, List<String> lines) {
      try {
         Files.write(target.toPath(), lines, StandardCharsets.UTF_8);
      } catch (IOException exception) {
         this.plugin.getLogger().warning("Gagal menulis " + target.getName() + ": " + exception.getMessage());
      }
   }

   private record Insert(int index, List<String> lines) {
      private Insert {
      }
   }

   private static final class Node {
      private final String key;
      private final int indent;
      private final int lineIndex;
      private final List<ResourceMerger.Node> children = new ArrayList<>();
      private int end;

      private Node(String key, int indent, int lineIndex) {
         super();
         this.key = key;
         this.indent = indent;
         this.lineIndex = lineIndex;
      }

      private String key() {
         return this.key;
      }

      private int indent() {
         return this.indent;
      }

      private int lineIndex() {
         return this.lineIndex;
      }

      private List<ResourceMerger.Node> children() {
         return this.children;
      }

      private int end() {
         return this.end;
      }

      private void end(int value) {
         this.end = value;
      }

      private ResourceMerger.Node child(String childKey) {
         for (ResourceMerger.Node node : this.children) {
            if (node.key.equals(childKey)) {
               return node;
            }
         }

         return null;
      }
   }
}
