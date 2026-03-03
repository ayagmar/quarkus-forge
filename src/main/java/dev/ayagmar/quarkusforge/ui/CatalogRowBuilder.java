package dev.ayagmar.quarkusforge.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds the flat row list from a filtered set of extension catalog items. Handles category
 * grouping, section headers, collapse state, and recent selections prepending.
 */
final class CatalogRowBuilder {
  static final String RECENT_SECTION_TITLE = "Recently Selected";
  static final int MAX_RECENT_SELECTIONS = 10;

  private CatalogRowBuilder() {}

  static List<ExtensionCatalogRow> buildRows(
      List<ExtensionCatalogItem> items,
      Set<String> collapsedCategories,
      List<String> recentExtensionIds,
      String currentQuery,
      boolean favoritesOnly,
      boolean selectedOnly,
      String activePreset) {
    if (items.isEmpty()) {
      return List.of();
    }
    List<ExtensionCatalogRow> rows = new ArrayList<>();
    prependRecentRows(
        rows, items, recentExtensionIds, currentQuery, favoritesOnly, selectedOnly, activePreset);
    appendCategoryRows(rows, items, collapsedCategories);
    return List.copyOf(rows);
  }

  static String resolveCategoryTitle(String categoryKey, String rawCategory) {
    return switch (categoryKey) {
      case "core" -> "Core";
      case "web" -> "Web";
      case "data" -> "Data";
      case "serialization" -> "Serialization";
      case "messaging" -> "Messaging";
      case "security" -> "Security";
      case "cloud" -> "Cloud";
      case "observability" -> "Observability";
      case "testing" -> "Testing";
      case "misc" -> "Misc";
      case "other" -> "Other";
      default -> titleCase(normalizeCategoryLabel(rawCategory));
    };
  }

  static Set<String> availableCategoryTitles(List<ExtensionCatalogItem> items) {
    Set<String> titles = new LinkedHashSet<>();
    for (ExtensionCatalogItem item : items) {
      titles.add(resolveCategoryTitle(item.categoryKey(), item.category()));
    }
    return titles;
  }

  static List<Integer> buildSelectableIndexes(List<ExtensionCatalogRow> rows) {
    List<Integer> indexes = new ArrayList<>();
    for (int i = 0; i < rows.size(); i++) {
      if (!rows.get(i).isSectionHeader()) {
        indexes.add(i);
      }
    }
    return List.copyOf(indexes);
  }

  static List<Integer> buildAllRowIndexes(List<ExtensionCatalogRow> rows) {
    List<Integer> indexes = new ArrayList<>(rows.size());
    for (int i = 0; i < rows.size(); i++) {
      indexes.add(i);
    }
    return List.copyOf(indexes);
  }

  static Map<String, Integer> buildRowIndexByExtensionId(List<ExtensionCatalogRow> rows) {
    Map<String, Integer> indexes = new LinkedHashMap<>();
    for (int i = 0; i < rows.size(); i++) {
      ExtensionCatalogRow row = rows.get(i);
      if (row.extension() != null) {
        indexes.put(row.extension().id(), i);
      }
    }
    return Map.copyOf(indexes);
  }

  // ── Private helpers ───────────────────────────────────────────────────

  private static void prependRecentRows(
      List<ExtensionCatalogRow> rows,
      List<ExtensionCatalogItem> items,
      List<String> recentExtensionIds,
      String currentQuery,
      boolean favoritesOnly,
      boolean selectedOnly,
      String activePreset) {
    if (!currentQuery.isBlank()
        || favoritesOnly
        || selectedOnly
        || !activePreset.isBlank()
        || recentExtensionIds.isEmpty()) {
      return;
    }
    List<ExtensionCatalogItem> recentItems = resolveRecentItems(items, recentExtensionIds);
    if (recentItems.isEmpty()) {
      return;
    }
    rows.add(ExtensionCatalogRow.section(RECENT_SECTION_TITLE, false, 0));
    for (ExtensionCatalogItem item : recentItems) {
      rows.add(ExtensionCatalogRow.item(item));
    }
  }

  private static List<ExtensionCatalogItem> resolveRecentItems(
      List<ExtensionCatalogItem> items, List<String> recentExtensionIds) {
    Map<String, ExtensionCatalogItem> byId = new LinkedHashMap<>();
    for (ExtensionCatalogItem item : items) {
      byId.put(item.id(), item);
    }
    Set<String> seen = new HashSet<>();
    List<ExtensionCatalogItem> recent = new ArrayList<>();
    for (String recentId : recentExtensionIds) {
      if (!seen.add(recentId)) {
        continue;
      }
      ExtensionCatalogItem item = byId.get(recentId);
      if (item != null) {
        recent.add(item);
      }
      if (recent.size() >= MAX_RECENT_SELECTIONS) {
        break;
      }
    }
    return recent;
  }

  private static void appendCategoryRows(
      List<ExtensionCatalogRow> rows,
      List<ExtensionCatalogItem> items,
      Set<String> collapsedCategories) {
    Map<String, List<ExtensionCatalogItem>> itemsByCategoryTitle = new LinkedHashMap<>();
    for (ExtensionCatalogItem item : items) {
      String title = resolveCategoryTitle(item.categoryKey(), item.category());
      itemsByCategoryTitle.computeIfAbsent(title, ignored -> new ArrayList<>()).add(item);
    }

    for (Map.Entry<String, List<ExtensionCatalogItem>> entry : itemsByCategoryTitle.entrySet()) {
      String title = entry.getKey();
      List<ExtensionCatalogItem> categoryItems = entry.getValue();
      boolean collapsed = collapsedCategories.contains(title);
      int totalCount = categoryItems.size();
      int hiddenCount = collapsed ? totalCount : 0;
      rows.add(ExtensionCatalogRow.section(title, collapsed, hiddenCount, totalCount));
      if (!collapsed) {
        for (ExtensionCatalogItem item : categoryItems) {
          rows.add(ExtensionCatalogRow.item(item));
        }
      }
    }
  }

  private static String titleCase(String value) {
    if (value == null || value.isBlank()) {
      return "Other";
    }
    String normalized = value.trim();
    if (normalized.length() == 1) {
      return normalized.toUpperCase(Locale.ROOT);
    }
    return normalized.substring(0, 1).toUpperCase(Locale.ROOT) + normalized.substring(1);
  }

  private static String normalizeCategoryLabel(String value) {
    if (value == null || value.isBlank()) {
      return "other";
    }
    return value.trim().toLowerCase(Locale.ROOT);
  }
}
