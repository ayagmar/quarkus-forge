package dev.ayagmar.quarkusforge.ui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class ExtensionCatalogRows {
  private final Set<String> collapsedCategoryTitles = new LinkedHashSet<>();
  private List<ExtensionCatalogItem> filteredExtensions = List.of();
  private List<ExtensionCatalogRow> filteredRows = List.of();
  private List<Integer> selectableRowIndexes = List.of();
  private List<Integer> allRowIndexes = List.of();
  private Map<String, Integer> rowIndexByExtensionId = Map.of();

  void update(
      List<ExtensionCatalogItem> filteredExtensions,
      List<String> recentExtensionIds,
      ExtensionCatalogFilters filters) {
    this.filteredExtensions = List.copyOf(Objects.requireNonNull(filteredExtensions));
    filteredRows =
        List.copyOf(
            CatalogRowBuilder.buildRows(
                this.filteredExtensions,
                collapsedCategoryTitles,
                Objects.requireNonNull(recentExtensionIds),
                filters.currentQuery(),
                filters.favoritesOnlyFilterEnabled(),
                filters.selectedOnlyFilterEnabled(),
                filters.activePresetFilterName()));
    selectableRowIndexes = List.copyOf(CatalogRowBuilder.buildSelectableIndexes(filteredRows));
    allRowIndexes = List.copyOf(CatalogRowBuilder.buildAllRowIndexes(filteredRows));
    rowIndexByExtensionId = Map.copyOf(CatalogRowBuilder.buildRowIndexByExtensionId(filteredRows));
  }

  void retainAvailableCategories(Set<String> availableCategoryTitles) {
    collapsedCategoryTitles.retainAll(availableCategoryTitles);
  }

  boolean toggleCategoryCollapse(String categoryTitle) {
    if (categoryTitle == null
        || categoryTitle.isBlank()
        || CatalogRowBuilder.RECENT_SECTION_TITLE.equals(categoryTitle)) {
      return false;
    }
    boolean collapsed = collapsedCategoryTitles.add(categoryTitle);
    if (!collapsed) {
      collapsedCategoryTitles.remove(categoryTitle);
    }
    return collapsed;
  }

  int expandAllCategories() {
    int collapsedCount = collapsedCategoryTitles.size();
    if (collapsedCount > 0) {
      collapsedCategoryTitles.clear();
    }
    return collapsedCount;
  }

  List<ExtensionCatalogItem> filteredExtensions() {
    return filteredExtensions;
  }

  List<ExtensionCatalogRow> filteredRows() {
    return filteredRows;
  }

  List<Integer> selectableRowIndexes() {
    return selectableRowIndexes;
  }

  List<Integer> allRowIndexes() {
    return allRowIndexes;
  }

  Integer rowIndexByExtensionId(String extensionId) {
    return rowIndexByExtensionId.get(extensionId);
  }

  ExtensionCatalogItem itemAtRow(int rowIndex) {
    if (rowIndex < 0 || rowIndex >= filteredRows.size()) {
      return null;
    }
    return filteredRows.get(rowIndex).extension();
  }

  String itemIdAtRow(Integer rowIndex) {
    if (rowIndex == null) {
      return null;
    }
    ExtensionCatalogItem selected = itemAtRow(rowIndex);
    return selected == null ? null : selected.id();
  }

  Integer firstNonRecentSectionHeaderIndex() {
    for (int i = 0; i < filteredRows.size(); i++) {
      ExtensionCatalogRow row = filteredRows.get(i);
      if (!row.isSectionHeader()) {
        continue;
      }
      if (CatalogRowBuilder.RECENT_SECTION_TITLE.equals(row.label())) {
        continue;
      }
      return i;
    }
    return null;
  }

  List<Integer> favoriteRowIndexes(Set<String> favoriteExtensionIds) {
    List<Integer> indexes = new ArrayList<>();
    for (Integer rowIndex : selectableRowIndexes) {
      ExtensionCatalogItem rowItem = itemAtRow(rowIndex);
      if (rowItem != null && favoriteExtensionIds.contains(rowItem.id())) {
        indexes.add(rowIndex);
      }
    }
    return indexes;
  }

  List<Integer> navigationRowIndexes(Integer selectedRow) {
    if (allRowIndexes.isEmpty()) {
      return List.of();
    }
    if (selectableRowIndexes.isEmpty()) {
      return allRowIndexes;
    }
    if (selectedRow != null
        && selectedRow >= 0
        && selectedRow < filteredRows.size()
        && filteredRows.get(selectedRow).isSectionHeader()) {
      return allRowIndexes;
    }
    return selectableRowIndexes;
  }

  boolean isCategorySectionHeaderSelected(Integer selectedRow) {
    if (selectedRow == null || selectedRow < 0 || selectedRow >= filteredRows.size()) {
      return false;
    }
    ExtensionCatalogRow row = filteredRows.get(selectedRow);
    return row.isSectionHeader() && !CatalogRowBuilder.RECENT_SECTION_TITLE.equals(row.label());
  }

  boolean isSelectedCategorySectionCollapsed(Integer selectedRow) {
    return isCategorySectionHeaderSelected(selectedRow)
        && filteredRows.get(selectedRow).collapsed();
  }

  String selectedSectionHeaderTitle(Integer selectedRow) {
    if (selectedRow == null || selectedRow < 0 || selectedRow >= filteredRows.size()) {
      return "";
    }
    ExtensionCatalogRow row = filteredRows.get(selectedRow);
    return row.isSectionHeader() ? row.label() : "";
  }

  String categoryTitleForRow(int rowIndex) {
    if (rowIndex < 0 || rowIndex >= filteredRows.size()) {
      return null;
    }
    ExtensionCatalogRow row = filteredRows.get(rowIndex);
    if (row.isSectionHeader()) {
      if (CatalogRowBuilder.RECENT_SECTION_TITLE.equals(row.label())) {
        return null;
      }
      return row.label();
    }
    ExtensionCatalogItem extension = row.extension();
    if (extension == null || isUnderRecentSection(rowIndex)) {
      return null;
    }
    return CatalogRowBuilder.resolveCategoryTitle(extension.categoryKey(), extension.category());
  }

  Integer sectionHeaderRowIndex(String categoryTitle) {
    for (int i = 0; i < filteredRows.size(); i++) {
      ExtensionCatalogRow row = filteredRows.get(i);
      if (row.isSectionHeader() && row.label().equals(categoryTitle)) {
        return i;
      }
    }
    return null;
  }

  private boolean isUnderRecentSection(int rowIndex) {
    for (int i = rowIndex - 1; i >= 0; i--) {
      ExtensionCatalogRow candidate = filteredRows.get(i);
      if (!candidate.isSectionHeader()) {
        continue;
      }
      return CatalogRowBuilder.RECENT_SECTION_TITLE.equals(candidate.label());
    }
    return false;
  }
}
