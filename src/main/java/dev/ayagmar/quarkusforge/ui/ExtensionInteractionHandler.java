package dev.ayagmar.quarkusforge.ui;

import java.util.function.IntConsumer;

/**
 * Bridges extension catalog interactions and status message updates. Each method delegates to
 * focused catalog collaborators and returns a status message describing the outcome.
 */
final class ExtensionInteractionHandler {
  private final ExtensionCatalogPreferences preferences;
  private final ExtensionCatalogNavigation navigation;
  private final ExtensionCatalogProjection projection;

  ExtensionInteractionHandler(
      ExtensionCatalogPreferences preferences,
      ExtensionCatalogNavigation navigation,
      ExtensionCatalogProjection projection) {
    this.preferences = preferences;
    this.navigation = navigation;
    this.projection = projection;
  }

  String toggleFavoriteAtSelection(IntConsumer onFiltered) {
    FavoriteToggleResult toggleResult = toggleFavoriteAtSelectionInternal(onFiltered);
    if (!toggleResult.changed()) {
      return "No extension selected to favorite";
    }
    return (toggleResult.favoriteNow() ? "Favorited extension: " : "Unfavorited extension: ")
        + toggleResult.extensionName();
  }

  String cycleCategoryFilter(IntConsumer onFiltered) {
    CategoryFilterResult result =
        projection.cycleCategoryFilter(navigation, preferences, onFiltered);
    if (!result.filtered()) {
      return "Category filter cleared (" + result.matchCount() + " matches)";
    }
    return "Category filter: " + result.categoryTitle() + " (" + result.matchCount() + " matches)";
  }

  String clearCategoryFilter(IntConsumer onFiltered) {
    boolean cleared = projection.clearCategoryFilter(navigation, preferences, onFiltered);
    if (!cleared) {
      return null;
    }
    return "Category filter cleared";
  }

  String clearSelectedExtensions() {
    int clearedCount = navigation.clearSelectedExtensions();
    if (clearedCount == 0) {
      return "No selected extensions to clear";
    }
    projection.reapplyAfterSelectionMutation(navigation, preferences);
    return "Cleared "
        + clearedCount
        + " selected "
        + (clearedCount == 1 ? "extension" : "extensions");
  }

  String toggleCategoryCollapseAtSelection() {
    CategoryCollapseResult collapseResult = toggleCategoryCollapseAtSelectionInternal();
    if (!collapseResult.changed()) {
      return "No category selected to close";
    }
    return (collapseResult.collapsed() ? "Closed category: " : "Opened category: ")
        + collapseResult.categoryTitle();
  }

  String expandAllCategories() {
    int reopenedCount = projection.expandAllCategories();
    if (reopenedCount == 0) {
      return "All categories are already open";
    }
    projection.refreshRows(selectedListItemId(), navigation, preferences);
    return "Opened " + reopenedCount + " " + (reopenedCount == 1 ? "category" : "categories");
  }

  String jumpToFavorite() {
    JumpToFavoriteResult jumpResult =
        navigation.jumpToFavorite(projection.rows(), preferences.favoriteIdsView());
    if (!jumpResult.jumped()) {
      return "No favorite extension in current catalog view";
    }
    return "Jumped to favorite: " + jumpResult.extensionName();
  }

  String jumpToAdjacentSection(boolean forward) {
    SectionJumpResult jumpResult = navigation.jumpToAdjacentSection(projection.rows(), forward);
    if (!jumpResult.moved()) {
      return forward ? "No next category section" : "No previous category section";
    }
    return "Jumped to category: " + jumpResult.categoryTitle();
  }

  String handleHierarchyLeft() {
    SectionFocusResult parentSectionResult = navigation.focusParentSectionHeader(projection.rows());
    if (parentSectionResult.moved()) {
      return "Moved to section: " + parentSectionResult.sectionTitle();
    }
    if (projection.isCategorySectionHeaderSelected(navigation.selectedRow())
        && !projection.isSelectedCategorySectionCollapsed(navigation.selectedRow())) {
      return toggleCategoryCollapseAtSelection();
    }
    return null;
  }

  String handleHierarchyRight() {
    SectionFocusResult childResult =
        navigation.focusFirstVisibleItemInSelectedSection(projection.rows());
    if (childResult.moved()) {
      return "Moved to first item in section: " + childResult.sectionTitle();
    }
    if (projection.isCategorySectionHeaderSelected(navigation.selectedRow())
        && projection.isSelectedCategorySectionCollapsed(navigation.selectedRow())) {
      return toggleCategoryCollapseAtSelection();
    }
    return null;
  }

  String toggleFavoritesOnlyFilter(IntConsumer onFiltered) {
    boolean enabled = projection.toggleFavoritesOnlyFilter(navigation, preferences, onFiltered);
    return enabled ? "Favorites filter enabled" : "Favorites filter disabled";
  }

  String toggleSelectedOnlyFilter(IntConsumer onFiltered) {
    boolean enabled = projection.toggleSelectedOnlyFilter(navigation, preferences, onFiltered);
    return enabled ? "Selected-only view enabled" : "Selected-only view disabled";
  }

  String cyclePresetFilter(IntConsumer onFiltered) {
    PresetFilterResult result = projection.cyclePresetFilter(navigation, preferences, onFiltered);
    if (!result.filtered()) {
      return "Preset filter disabled";
    }
    return "Preset filter: " + result.presetName() + " (" + result.matchCount() + ")";
  }

  String clearPresetFilter(IntConsumer onFiltered) {
    boolean cleared = projection.clearPresetFilter(navigation, preferences, onFiltered);
    if (!cleared) {
      return "Preset filter already disabled";
    }
    return "Preset filter disabled";
  }

  private FavoriteToggleResult toggleFavoriteAtSelectionInternal(IntConsumer onFiltered) {
    ExtensionCatalogItem selected = selectedListItem();
    if (selected == null) {
      return FavoriteToggleResult.none();
    }
    boolean isNowFavorite = preferences.toggleFavorite(selected.id());
    projection.reapplyAfterSelectionMutation(navigation, preferences);
    onFiltered.accept(projection.filteredExtensions().size());
    return new FavoriteToggleResult(true, selected.name(), isNowFavorite);
  }

  private CategoryCollapseResult toggleCategoryCollapseAtSelectionInternal() {
    Integer selectedRow = navigation.selectedRow();
    if (selectedRow == null) {
      return CategoryCollapseResult.none();
    }
    String categoryTitle = projection.categoryTitleForRow(selectedRow);
    if (categoryTitle == null) {
      return CategoryCollapseResult.none();
    }
    boolean collapsed = projection.toggleCategoryCollapse(categoryTitle);
    projection.refreshRows(selectedListItemId(), navigation, preferences);
    Integer sectionHeaderIndex = projection.sectionHeaderRowIndex(categoryTitle);
    if (sectionHeaderIndex != null) {
      navigation.listState().select(sectionHeaderIndex);
    }
    return new CategoryCollapseResult(true, categoryTitle, collapsed);
  }

  private String selectedListItemId() {
    return projection.itemIdAtRow(navigation.selectedRow());
  }

  private ExtensionCatalogItem selectedListItem() {
    Integer selectedRow = navigation.selectedRow();
    return selectedRow == null ? null : projection.itemAtRow(selectedRow);
  }
}
