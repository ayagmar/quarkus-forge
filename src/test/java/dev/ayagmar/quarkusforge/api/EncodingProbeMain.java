package dev.ayagmar.quarkusforge.api;

import dev.ayagmar.quarkusforge.forge.ForgefileStore;
import dev.ayagmar.quarkusforge.persistence.ExtensionFavoritesStore;
import dev.ayagmar.quarkusforge.persistence.UserPreferencesStore;
import java.nio.file.Path;

public final class EncodingProbeMain {
  private EncodingProbeMain() {}

  public static void main(String[] args) {
    if (args.length != 2) {
      throw new IllegalArgumentException("Expected <mode> <path>");
    }
    String value = readValue(args[0], Path.of(args[1]));
    System.out.print(asciiEscape(value));
  }

  private static String readValue(String mode, Path path) {
    return switch (mode) {
      case "favorites-recent" ->
          ExtensionFavoritesStore.fileBacked(path).loadRecentExtensionIds().getFirst();
      case "preferences-output-dir" ->
          UserPreferencesStore.fileBacked(path).loadLastRequest().outputDirectory();
      case "forgefile-output-dir" -> ForgefileStore.load(path).outputDirectory();
      case "catalog-extension-name" ->
          new CatalogSnapshotCache(path).read().orElseThrow().extensions().getFirst().name();
      default -> throw new IllegalArgumentException("Unknown probe mode: " + mode);
    };
  }

  private static String asciiEscape(String value) {
    StringBuilder escaped = new StringBuilder();
    for (int index = 0; index < value.length(); index++) {
      char current = value.charAt(index);
      if (current >= 0x20 && current <= 0x7E && current != '\\') {
        escaped.append(current);
      } else {
        escaped.append(String.format("\\u%04X", (int) current));
      }
    }
    return escaped.toString();
  }
}
