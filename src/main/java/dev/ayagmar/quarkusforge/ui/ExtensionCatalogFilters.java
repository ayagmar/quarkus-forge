package dev.ayagmar.quarkusforge.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class ExtensionCatalogFilters {
  private Map<String, List<String>> presetExtensionsByName = Map.of();
  private String currentQuery;
  private boolean favoritesOnlyFilterEnabled;
  private boolean selectedOnlyFilterEnabled;
  private String activeCategoryFilterTitle;
  private String activePresetFilterName;

  ExtensionCatalogFilters(String initialQuery) {
    currentQuery = initialQuery == null ? "" : initialQuery;
    favoritesOnlyFilterEnabled = false;
    selectedOnlyFilterEnabled = false;
    activeCategoryFilterTitle = "";
    activePresetFilterName = "";
  }

  String currentQuery() {
    return currentQuery;
  }

  boolean favoritesOnlyFilterEnabled() {
    return favoritesOnlyFilterEnabled;
  }

  boolean selectedOnlyFilterEnabled() {
    return selectedOnlyFilterEnabled;
  }

  String activeCategoryFilterTitle() {
    return activeCategoryFilterTitle;
  }

  String activePresetFilterName() {
    return activePresetFilterName;
  }

  void updateQuery(String queryText) {
    currentQuery = queryText == null ? "" : queryText;
  }

  boolean toggleFavoritesOnly() {
    favoritesOnlyFilterEnabled = !favoritesOnlyFilterEnabled;
    return favoritesOnlyFilterEnabled;
  }

  boolean toggleSelectedOnly() {
    selectedOnlyFilterEnabled = !selectedOnlyFilterEnabled;
    return selectedOnlyFilterEnabled;
  }

  boolean clearCategoryFilter() {
    if (activeCategoryFilterTitle.isBlank()) {
      return false;
    }
    activeCategoryFilterTitle = "";
    return true;
  }

  void setPresetExtensionsByName(Map<String, List<String>> presetMap) {
    presetExtensionsByName = normalizePresetMap(presetMap);
    if (!activePresetFilterName.isBlank()
        && !presetExtensionsByName.containsKey(activePresetFilterName)) {
      activePresetFilterName = "";
    }
  }

  boolean clearPresetFilter() {
    if (activePresetFilterName.isBlank()) {
      return false;
    }
    activePresetFilterName = "";
    return true;
  }

  void cyclePresetFilter(List<String> presets) {
    if (presets.isEmpty()) {
      activePresetFilterName = "";
      return;
    }
    if (activePresetFilterName.isBlank()) {
      activePresetFilterName = presets.getFirst();
      return;
    }
    int index = presets.indexOf(activePresetFilterName);
    if (index < 0 || index >= presets.size() - 1) {
      activePresetFilterName = "";
      return;
    }
    activePresetFilterName = presets.get(index + 1);
  }

  void cycleCategoryFilter(List<String> categoryTitles) {
    if (categoryTitles.isEmpty()) {
      activeCategoryFilterTitle = "";
      return;
    }
    if (activeCategoryFilterTitle.isBlank()) {
      activeCategoryFilterTitle = categoryTitles.getFirst();
      return;
    }
    int index = categoryTitles.indexOf(activeCategoryFilterTitle);
    if (index < 0 || index >= categoryTitles.size() - 1) {
      activeCategoryFilterTitle = "";
      return;
    }
    activeCategoryFilterTitle = categoryTitles.get(index + 1);
  }

  List<String> availablePresetNames() {
    if (presetExtensionsByName.isEmpty()) {
      return List.of();
    }
    List<String> names = new ArrayList<>(presetExtensionsByName.keySet());
    names.sort(String::compareTo);
    return List.copyOf(names);
  }

  List<String> filterableCategoryTitles(
      ExtensionCatalogIndex catalogIndex,
      Set<String> selectedExtensionIds,
      Set<String> favoriteExtensionIds) {
    List<ExtensionCatalogItem> rankedResults = catalogIndex.search("", favoriteExtensionIds);
    rankedResults =
        applyPreCategoryFilters(rankedResults, selectedExtensionIds, favoriteExtensionIds);
    return List.copyOf(CatalogRowBuilder.availableCategoryTitles(rankedResults));
  }

  List<ExtensionCatalogItem> apply(
      List<ExtensionCatalogItem> items,
      Set<String> selectedExtensionIds,
      Set<String> favoriteExtensionIds) {
    List<ExtensionCatalogItem> result =
        applyPreCategoryFilters(items, selectedExtensionIds, favoriteExtensionIds);
    if (!activeCategoryFilterTitle.isBlank()) {
      result =
          result.stream()
              .filter(
                  item ->
                      activeCategoryFilterTitle.equals(
                          CatalogRowBuilder.resolveCategoryTitle(
                              item.categoryKey(), item.category())))
              .toList();
    }
    return result;
  }

  void reconcileAvailableCategories(Set<String> availableCategoryTitles) {
    if (!activeCategoryFilterTitle.isBlank()
        && !availableCategoryTitles.contains(activeCategoryFilterTitle)) {
      activeCategoryFilterTitle = "";
    }
  }

  private List<ExtensionCatalogItem> applyPreCategoryFilters(
      List<ExtensionCatalogItem> items,
      Set<String> selectedExtensionIds,
      Set<String> favoriteExtensionIds) {
    List<ExtensionCatalogItem> result = items;
    if (selectedOnlyFilterEnabled) {
      result = result.stream().filter(item -> selectedExtensionIds.contains(item.id())).toList();
    }
    if (favoritesOnlyFilterEnabled) {
      result = result.stream().filter(item -> favoriteExtensionIds.contains(item.id())).toList();
    }
    if (!activePresetFilterName.isBlank()) {
      List<String> presetExtensions = presetExtensionsByName.get(activePresetFilterName);
      Set<String> allowedIds =
          new LinkedHashSet<>(presetExtensions == null ? List.of() : presetExtensions);
      result = result.stream().filter(item -> allowedIds.contains(item.id())).toList();
    }
    return result;
  }

  private static Map<String, List<String>> normalizePresetMap(Map<String, List<String>> presetMap) {
    if (presetMap == null || presetMap.isEmpty()) {
      return Map.of();
    }
    LinkedHashMap<String, List<String>> normalized = new LinkedHashMap<>();
    for (Map.Entry<String, List<String>> entry : presetMap.entrySet()) {
      if (entry.getKey() == null || entry.getKey().isBlank()) {
        continue;
      }
      String key = entry.getKey().trim().toLowerCase(Locale.ROOT);
      List<String> extensions =
          entry.getValue() == null ? List.of() : List.copyOf(entry.getValue());
      normalized.put(key, extensions);
    }
    return Map.copyOf(normalized);
  }
}
