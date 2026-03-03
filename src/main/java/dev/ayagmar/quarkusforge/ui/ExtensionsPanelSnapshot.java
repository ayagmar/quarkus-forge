package dev.ayagmar.quarkusforge.ui;

import java.util.List;
import java.util.Objects;

record ExtensionsPanelSnapshot(
    boolean panelFocused,
    boolean listFocused,
    boolean submitFocused,
    boolean searchFocused,
    boolean loading,
    String catalogErrorMessage,
    String catalogSource,
    boolean catalogStale,
    boolean favoritesOnlyFilterEnabled,
    boolean selectedOnlyFilterEnabled,
    int favoriteCount,
    String activePresetFilterName,
    String activeCategoryFilterTitle,
    int validationErrorCount,
    int filteredExtensionCount,
    int totalCatalogExtensionCount,
    List<ExtensionCatalogRow> filteredRows,
    List<String> selectedExtensionIds,
    String searchQuery,
    String focusedExtensionDescription) {
  ExtensionsPanelSnapshot {
    catalogErrorMessage = catalogErrorMessage == null ? "" : catalogErrorMessage;
    catalogSource = catalogSource == null ? "" : catalogSource;
    activePresetFilterName = activePresetFilterName == null ? "" : activePresetFilterName;
    activeCategoryFilterTitle = activeCategoryFilterTitle == null ? "" : activeCategoryFilterTitle;
    validationErrorCount = Math.max(0, validationErrorCount);
    filteredExtensionCount = Math.max(0, filteredExtensionCount);
    totalCatalogExtensionCount = Math.max(0, totalCatalogExtensionCount);
    filteredRows = List.copyOf(Objects.requireNonNull(filteredRows));
    selectedExtensionIds = List.copyOf(Objects.requireNonNull(selectedExtensionIds));
    searchQuery = searchQuery == null ? "" : searchQuery;
    focusedExtensionDescription =
        focusedExtensionDescription == null ? "" : focusedExtensionDescription;
  }
}
