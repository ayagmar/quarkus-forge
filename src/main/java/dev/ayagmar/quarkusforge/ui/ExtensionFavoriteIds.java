package dev.ayagmar.quarkusforge.ui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ExtensionFavoriteIds {
  private ExtensionFavoriteIds() {}

  static Set<String> normalizeSet(Iterable<String> source) {
    Set<String> normalized = new LinkedHashSet<>();
    for (String extensionId : source) {
      if (extensionId == null) {
        continue;
      }
      String trimmed = extensionId.trim();
      if (!trimmed.isBlank()) {
        normalized.add(trimmed);
      }
    }
    return Set.copyOf(normalized);
  }

  static List<String> normalizeList(Iterable<String> source) {
    List<String> normalized = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (String extensionId : source) {
      if (extensionId == null) {
        continue;
      }
      String trimmed = extensionId.trim();
      if (trimmed.isBlank() || !seen.add(trimmed)) {
        continue;
      }
      normalized.add(trimmed);
    }
    return List.copyOf(normalized);
  }
}
