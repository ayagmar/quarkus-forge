package dev.ayagmar.quarkusforge.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class ExtensionCatalogIndex {
  private final List<IndexedItem> indexedItems;

  ExtensionCatalogIndex(List<ExtensionCatalogItem> items) {
    indexedItems =
        items.stream()
            .distinct()
            .sorted(
                Comparator.comparing(
                        (ExtensionCatalogItem item) -> item.name().toLowerCase(Locale.ROOT))
                    .thenComparing(item -> item.id().toLowerCase(Locale.ROOT)))
            .map(IndexedItem::from)
            .toList();
  }

  List<ExtensionCatalogItem> search(String queryText) {
    String normalizedQuery = normalize(queryText);
    if (normalizedQuery.isBlank()) {
      return indexedItems.stream().map(IndexedItem::item).toList();
    }

    String[] tokens = normalizedQuery.split("\\s+");
    List<SearchResult> matches = new ArrayList<>();
    for (IndexedItem indexedItem : indexedItems) {
      if (!matchesAllTokens(indexedItem, tokens)) {
        continue;
      }
      matches.add(new SearchResult(indexedItem.item(), score(indexedItem, normalizedQuery)));
    }

    matches.sort(
        Comparator.comparingInt(SearchResult::score)
            .thenComparing(result -> result.item().name().toLowerCase(Locale.ROOT))
            .thenComparing(result -> result.item().id().toLowerCase(Locale.ROOT)));
    return matches.stream().map(SearchResult::item).toList();
  }

  private static boolean matchesAllTokens(IndexedItem indexedItem, String[] tokens) {
    for (String token : tokens) {
      boolean tokenMatches =
          indexedItem.id().contains(token)
              || indexedItem.name().contains(token)
              || indexedItem.shortName().contains(token);
      if (!tokenMatches) {
        return false;
      }
    }
    return true;
  }

  private static int score(IndexedItem indexedItem, String query) {
    if (indexedItem.id().equals(query) || indexedItem.shortName().equals(query)) {
      return 0;
    }
    if (indexedItem.id().startsWith(query) || indexedItem.shortName().startsWith(query)) {
      return 1;
    }
    if (indexedItem.name().startsWith(query)) {
      return 2;
    }
    return 3;
  }

  private static String normalize(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
  }

  private record IndexedItem(ExtensionCatalogItem item, String id, String name, String shortName) {
    static IndexedItem from(ExtensionCatalogItem item) {
      return new IndexedItem(
          item,
          item.id().toLowerCase(Locale.ROOT),
          item.name().toLowerCase(Locale.ROOT),
          item.shortName().toLowerCase(Locale.ROOT));
    }
  }

  private record SearchResult(ExtensionCatalogItem item, int score) {}
}
