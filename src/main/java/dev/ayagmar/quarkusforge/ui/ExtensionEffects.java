package dev.ayagmar.quarkusforge.ui;

import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.input.TextInputState;
import java.util.List;
import java.util.Objects;

final class ExtensionEffects {
  interface Callbacks {
    UiIntent.ExtensionStateUpdatedIntent extensionStateUpdatedIntent();

    TextInputState extensionSearchState();

    String currentStatusMessage();
  }

  private final CatalogEffects catalogEffects;
  private final ExtensionInteractionHandler extensionInteraction;
  private final ExtensionCatalogPreferences extensionCatalogPreferences;
  private final ExtensionCatalogNavigation extensionCatalogNavigation;
  private final ExtensionCatalogProjection extensionCatalogProjection;
  private final Callbacks callbacks;

  ExtensionEffects(
      CatalogEffects catalogEffects,
      ExtensionInteractionHandler extensionInteraction,
      ExtensionCatalogPreferences extensionCatalogPreferences,
      ExtensionCatalogNavigation extensionCatalogNavigation,
      ExtensionCatalogProjection extensionCatalogProjection,
      Callbacks callbacks) {
    this.catalogEffects = Objects.requireNonNull(catalogEffects);
    this.extensionInteraction = Objects.requireNonNull(extensionInteraction);
    this.extensionCatalogPreferences = Objects.requireNonNull(extensionCatalogPreferences);
    this.extensionCatalogNavigation = Objects.requireNonNull(extensionCatalogNavigation);
    this.extensionCatalogProjection = Objects.requireNonNull(extensionCatalogProjection);
    this.callbacks = Objects.requireNonNull(callbacks);
  }

  List<UiIntent> executeCommand(UiIntent.ExtensionCommand command) {
    return switch (command) {
      case CLEAR_SEARCH ->
          extensionUpdateIntents(
              catalogEffects.clearSearchFilter(callbacks.extensionSearchState()));
      case DISABLE_FAVORITES_FILTER ->
          extensionUpdateIntents(
              extensionCatalogProjection.favoritesOnlyFilterEnabled()
                  ? extensionInteraction.toggleFavoritesOnlyFilter(ignored -> {})
                  : "Favorites filter disabled");
      case DISABLE_SELECTED_FILTER ->
          extensionUpdateIntents(
              extensionCatalogProjection.selectedOnlyFilterEnabled()
                  ? extensionInteraction.toggleSelectedOnlyFilter(ignored -> {})
                  : "Selected-only view disabled");
      case CLEAR_PRESET_FILTER ->
          extensionUpdateIntents(extensionInteraction.clearPresetFilter(ignored -> {}));
      case CLEAR_CATEGORY_FILTER -> {
        String statusMessage = extensionInteraction.clearCategoryFilter(ignored -> {});
        yield extensionUpdateIntents(
            statusMessage == null ? "Category filter cleared" : statusMessage);
      }
      case TOGGLE_FAVORITE_AT_SELECTION ->
          extensionUpdateIntents(extensionInteraction.toggleFavoriteAtSelection(ignored -> {}));
      case CLEAR_SELECTED_EXTENSIONS ->
          extensionUpdateIntents(extensionInteraction.clearSelectedExtensions());
      case TOGGLE_CATEGORY_AT_SELECTION ->
          extensionUpdateIntents(extensionInteraction.toggleCategoryCollapseAtSelection());
      case OPEN_ALL_CATEGORIES ->
          extensionUpdateIntents(extensionInteraction.expandAllCategories());
      case JUMP_TO_NEXT_CATEGORY ->
          extensionUpdateIntents(extensionInteraction.jumpToAdjacentSection(true));
      case JUMP_TO_PREVIOUS_CATEGORY ->
          extensionUpdateIntents(extensionInteraction.jumpToAdjacentSection(false));
      case HIERARCHY_LEFT -> {
        String statusMessage = extensionInteraction.handleHierarchyLeft();
        yield extensionUpdateIntents(
            statusMessage == null ? callbacks.currentStatusMessage() : statusMessage);
      }
      case HIERARCHY_RIGHT -> {
        String statusMessage = extensionInteraction.handleHierarchyRight();
        yield extensionUpdateIntents(
            statusMessage == null ? callbacks.currentStatusMessage() : statusMessage);
      }
      case TOGGLE_SELECTION_AT_CURSOR -> extensionUpdateIntents(toggleSelectionAtCursor());
      case TOGGLE_FAVORITES_FILTER ->
          extensionUpdateIntents(extensionInteraction.toggleFavoritesOnlyFilter(ignored -> {}));
      case TOGGLE_SELECTED_FILTER ->
          extensionUpdateIntents(extensionInteraction.toggleSelectedOnlyFilter(ignored -> {}));
      case CYCLE_PRESET_FILTER ->
          extensionUpdateIntents(extensionInteraction.cyclePresetFilter(ignored -> {}));
      case JUMP_TO_FAVORITE -> extensionUpdateIntents(extensionInteraction.jumpToFavorite());
      case CYCLE_CATEGORY_FILTER ->
          extensionUpdateIntents(extensionInteraction.cycleCategoryFilter(ignored -> {}));
    };
  }

  List<UiIntent> applyNavigationKey(KeyEvent keyEvent) {
    boolean handled =
        extensionCatalogNavigation.handleNavigationKey(extensionCatalogProjection.rows(), keyEvent);
    return handled ? List.of(callbacks.extensionStateUpdatedIntent()) : List.of();
  }

  private List<UiIntent> extensionUpdateIntents(String statusMessage) {
    return ExtensionIntentFactory.updateWithStatus(
        callbacks.extensionStateUpdatedIntent(), statusMessage);
  }

  private String toggleSelectionAtCursor() {
    Integer selectedRow = extensionCatalogNavigation.selectedRow();
    if (selectedRow == null) {
      return "No extension selected to toggle";
    }
    ExtensionCatalogItem extension = extensionCatalogProjection.itemAtRow(selectedRow);
    if (extension == null) {
      return "No extension selected to toggle";
    }
    // `select` returns false when the cursor item is already selected, so the explicit deselect
    // branch owns removing it and rebuilding the projection from the updated selection.
    if (extensionCatalogNavigation.select(extension.id())) {
      extensionCatalogPreferences.recordRecentSelection(extension.id());
      extensionCatalogProjection.refreshRows(
          extension.id(), extensionCatalogNavigation, extensionCatalogPreferences);
    } else {
      extensionCatalogNavigation.deselect(extension.id());
      extensionCatalogProjection.reapplyAfterSelectionMutation(
          extensionCatalogNavigation, extensionCatalogPreferences);
    }
    return "Toggled extension: " + extension.name();
  }
}
