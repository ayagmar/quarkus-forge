package dev.ayagmar.quarkusforge.ui;

import java.util.function.IntConsumer;

/**
 * Bridges extension catalog interactions and status message updates. Each method delegates to
 * {@link ExtensionCatalogState} and returns a status message describing the outcome.
 */
final class ExtensionInteractionHandler {
  private final ExtensionCatalogState catalogState;

  ExtensionInteractionHandler(ExtensionCatalogState catalogState) {
    this.catalogState = catalogState;
  }

  String toggleFavoriteAtSelection(IntConsumer onFiltered) {
    FavoriteToggleResult toggleResult = catalogState.toggleFavoriteAtSelection(onFiltered);
    if (!toggleResult.changed()) {
      return "No extension selected to favorite";
    }
    return (toggleResult.favoriteNow() ? "Favorited extension: " : "Unfavorited extension: ")
        + toggleResult.extensionName();
  }

  String cycleCategoryFilter(IntConsumer onFiltered) {
    CategoryFilterResult result = catalogState.cycleCategoryFilter(onFiltered);
    if (!result.filtered()) {
      return "Category filter cleared (" + result.matchCount() + " matches)";
    }
    return "Category filter: " + result.categoryTitle() + " (" + result.matchCount() + " matches)";
  }

  String clearCategoryFilter(IntConsumer onFiltered) {
    boolean cleared = catalogState.clearCategoryFilter(onFiltered);
    if (!cleared) {
      return null;
    }
    return "Category filter cleared";
  }

  String clearSelectedExtensions() {
    int clearedCount = catalogState.clearSelectedExtensions();
    if (clearedCount == 0) {
      return "No selected extensions to clear";
    }
    return "Cleared "
        + clearedCount
        + " selected "
        + (clearedCount == 1 ? "extension" : "extensions");
  }

  String toggleCategoryCollapseAtSelection() {
    CategoryCollapseResult collapseResult = catalogState.toggleCategoryCollapseAtSelection();
    if (!collapseResult.changed()) {
      return "No category selected to close";
    }
    return (collapseResult.collapsed() ? "Closed category: " : "Opened category: ")
        + collapseResult.categoryTitle();
  }

  String expandAllCategories() {
    int reopenedCount = catalogState.expandAllCategories();
    if (reopenedCount == 0) {
      return "All categories are already open";
    }
    return "Opened " + reopenedCount + " " + (reopenedCount == 1 ? "category" : "categories");
  }

  String jumpToFavorite() {
    JumpToFavoriteResult jumpResult = catalogState.jumpToFavorite();
    if (!jumpResult.jumped()) {
      return "No favorite extension in current catalog view";
    }
    return "Jumped to favorite: " + jumpResult.extensionName();
  }

  String jumpToAdjacentSection(boolean forward) {
    SectionJumpResult jumpResult = catalogState.jumpToAdjacentSection(forward);
    if (!jumpResult.moved()) {
      return forward ? "No next category section" : "No previous category section";
    }
    return "Jumped to category: " + jumpResult.categoryTitle();
  }

  String handleHierarchyLeft() {
    SectionFocusResult parentSectionResult = catalogState.focusParentSectionHeader();
    if (parentSectionResult.moved()) {
      return "Moved to section: " + parentSectionResult.sectionTitle();
    }
    if (catalogState.isCategorySectionHeaderSelected()
        && !catalogState.isSelectedCategorySectionCollapsed()) {
      return toggleCategoryCollapseAtSelection();
    }
    return null;
  }

  String handleHierarchyRight() {
    SectionFocusResult childResult = catalogState.focusFirstVisibleItemInSelectedSection();
    if (childResult.moved()) {
      return "Moved to first item in section: " + childResult.sectionTitle();
    }
    if (catalogState.isCategorySectionHeaderSelected()
        && catalogState.isSelectedCategorySectionCollapsed()) {
      return toggleCategoryCollapseAtSelection();
    }
    return null;
  }

  String toggleFavoritesOnlyFilter(IntConsumer onFiltered) {
    boolean enabled = catalogState.toggleFavoritesOnlyFilter(onFiltered);
    return enabled ? "Favorites filter enabled" : "Favorites filter disabled";
  }

  String toggleSelectedOnlyFilter(IntConsumer onFiltered) {
    boolean enabled = catalogState.toggleSelectedOnlyFilter(onFiltered);
    return enabled ? "Selected-only view enabled" : "Selected-only view disabled";
  }

  String cyclePresetFilter(IntConsumer onFiltered) {
    PresetFilterResult result = catalogState.cyclePresetFilter(onFiltered);
    if (!result.filtered()) {
      return "Preset filter disabled";
    }
    return "Preset filter: " + result.presetName() + " (" + result.matchCount() + ")";
  }

  String clearPresetFilter(IntConsumer onFiltered) {
    boolean cleared = catalogState.clearPresetFilter(onFiltered);
    if (!cleared) {
      return "Preset filter already disabled";
    }
    return "Preset filter disabled";
  }
}
