package dev.ayagmar.quarkusforge.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class ExtensionCatalogIndex {
  private static final int BASELINE_FAVORITE_RANK = 0;
  private static final int BASELINE_NOT_POPULAR_RANK = Integer.MAX_VALUE;
  private static final List<String> CURATED_POPULAR_BASELINE =
      List.of(
          "io.quarkus:quarkus-rest",
          "io.quarkus:quarkus-rest-jackson",
          "io.quarkus:quarkus-smallrye-openapi",
          "io.quarkus:quarkus-hibernate-orm",
          "io.quarkus:quarkus-jdbc-postgresql",
          "io.quarkus:quarkus-arc",
          "io.quarkus:quarkus-hibernate-validator",
          "io.quarkus:quarkus-security",
          "io.quarkus:quarkus-oidc",
          "io.quarkus:quarkus-junit5");

  private final List<IndexedItem> indexedItems;
  private final Map<String, Integer> popularBaselineById;

  ExtensionCatalogIndex(List<ExtensionCatalogItem> items) {
    Map<String, ExtensionCatalogItem> uniqueById = new LinkedHashMap<>();
    for (ExtensionCatalogItem item : items) {
      uniqueById.putIfAbsent(normalize(item.id()), item);
    }

    indexedItems =
        uniqueById.values().stream()
            .sorted(
                Comparator.comparing((ExtensionCatalogItem item) -> normalize(item.name()))
                    .thenComparing(item -> normalize(item.id())))
            .map(IndexedItem::from)
            .toList();

    Map<String, Integer> baseline = new LinkedHashMap<>();
    for (int i = 0; i < CURATED_POPULAR_BASELINE.size(); i++) {
      baseline.put(normalize(CURATED_POPULAR_BASELINE.get(i)), i + 1);
    }
    popularBaselineById = Map.copyOf(baseline);
  }

  List<ExtensionCatalogItem> search(String queryText, Set<String> favoriteExtensionIds) {
    String normalizedQuery = normalize(queryText);
    Set<String> normalizedFavorites = normalizeIdSet(favoriteExtensionIds);
    if (normalizedQuery.isBlank()) {
      return indexedItems.stream()
          .sorted(emptyQueryComparator(normalizedFavorites))
          .map(IndexedItem::item)
          .toList();
    }

    String[] tokens = normalizedQuery.split("\\s+");
    List<SearchResult> matches = new ArrayList<>();
    for (IndexedItem indexedItem : indexedItems) {
      if (!matchesAllTokens(indexedItem, tokens)) {
        continue;
      }
      matches.add(new SearchResult(indexedItem, score(indexedItem, normalizedQuery)));
    }

    Comparator<IndexedItem> tieBreaker = emptyQueryComparator(normalizedFavorites);
    matches.sort(
        Comparator.comparingInt(SearchResult::score)
            .thenComparing(searchResult -> searchResult.item(), tieBreaker));
    return matches.stream().map(searchResult -> searchResult.item().item()).toList();
  }

  int totalItemCount() {
    return indexedItems.size();
  }

  private Comparator<IndexedItem> emptyQueryComparator(Set<String> normalizedFavorites) {
    return Comparator.comparingInt(IndexedItem::apiOrderRank)
        .thenComparingInt(indexedItem -> baselineRank(indexedItem, normalizedFavorites))
        .thenComparing(IndexedItem::name)
        .thenComparing(IndexedItem::id);
  }

  private int baselineRank(IndexedItem indexedItem, Set<String> normalizedFavorites) {
    if (normalizedFavorites.contains(indexedItem.id())) {
      return BASELINE_FAVORITE_RANK;
    }
    return popularBaselineById.getOrDefault(indexedItem.id(), BASELINE_NOT_POPULAR_RANK);
  }

  private static boolean matchesAllTokens(IndexedItem indexedItem, String[] tokens) {
    for (String token : tokens) {
      boolean tokenMatches =
          indexedItem.id().contains(token)
              || indexedItem.name().contains(token)
              || indexedItem.shortName().contains(token)
              || indexedItem.category().contains(token);
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

  private static Set<String> normalizeIdSet(Set<String> extensionIds) {
    if (extensionIds == null || extensionIds.isEmpty()) {
      return Set.of();
    }
    Set<String> normalized = new LinkedHashSet<>();
    for (String extensionId : extensionIds) {
      normalized.add(normalize(extensionId));
    }
    return Set.copyOf(normalized);
  }

  private static String normalize(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
  }

  private record IndexedItem(
      ExtensionCatalogItem item,
      String id,
      String name,
      String shortName,
      String category,
      int apiOrderRank) {
    static IndexedItem from(ExtensionCatalogItem item) {
      Integer apiOrder = item.apiOrder();
      int orderRank = apiOrder == null ? Integer.MAX_VALUE : apiOrder;
      return new IndexedItem(
          item,
          normalize(item.id()),
          normalize(item.name()),
          normalize(item.shortName()),
          normalize(item.category()),
          orderRank);
    }
  }

  private record SearchResult(IndexedItem item, int score) {}
}
