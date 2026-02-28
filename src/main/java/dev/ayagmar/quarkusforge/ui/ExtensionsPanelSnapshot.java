package dev.ayagmar.quarkusforge.ui;

import java.util.List;
import java.util.Objects;

record ExtensionsPanelSnapshot(
    String title,
    boolean panelFocused,
    boolean listFocused,
    boolean submitFocused,
    boolean searchFocused,
    boolean loading,
    String catalogErrorMessage,
    String catalogSource,
    boolean catalogStale,
    boolean favoritesOnlyFilterEnabled,
    int favoriteCount,
    String activeCategoryFilterTitle,
    int filteredExtensionCount,
    int totalCatalogExtensionCount,
    List<ExtensionCatalogRow> filteredRows,
    List<String> selectedExtensionIds,
    String searchQuery) {
  ExtensionsPanelSnapshot {
    title = Objects.requireNonNull(title);
    catalogErrorMessage = catalogErrorMessage == null ? "" : catalogErrorMessage;
    catalogSource = catalogSource == null ? "" : catalogSource;
    activeCategoryFilterTitle = activeCategoryFilterTitle == null ? "" : activeCategoryFilterTitle;
    filteredExtensionCount = Math.max(0, filteredExtensionCount);
    totalCatalogExtensionCount = Math.max(0, totalCatalogExtensionCount);
    filteredRows = List.copyOf(Objects.requireNonNull(filteredRows));
    selectedExtensionIds = List.copyOf(Objects.requireNonNull(selectedExtensionIds));
    searchQuery = searchQuery == null ? "" : searchQuery;
  }
}
