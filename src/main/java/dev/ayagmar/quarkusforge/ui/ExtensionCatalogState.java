package dev.ayagmar.quarkusforge.ui;

import dev.ayagmar.quarkusforge.api.ExtensionFavoritesStore;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.list.ListState;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

final class ExtensionCatalogState {
  private final ExtensionCatalogPreferences preferences;
  private final ExtensionCatalogNavigation navigation;
  private final ExtensionCatalogProjection projection;

  ExtensionCatalogState(UiScheduler scheduler, Duration debounceDelay, String initialQuery) {
    this(scheduler, debounceDelay, initialQuery, ExtensionFavoritesStore.inMemory(), Runnable::run);
  }

  ExtensionCatalogState(
      UiScheduler scheduler,
      Duration debounceDelay,
      String initialQuery,
      ExtensionFavoritesStore favoritesStore,
      Executor favoritesPersistenceExecutor) {
    preferences =
        new ExtensionCatalogPreferences(
            Objects.requireNonNull(favoritesStore),
            Objects.requireNonNull(favoritesPersistenceExecutor));
    navigation = new ExtensionCatalogNavigation();
    projection = new ExtensionCatalogProjection(scheduler, debounceDelay, initialQuery);
    projection.initialize(navigation, preferences);
  }

  void replaceCatalog(List<ExtensionCatalogItem> items, String query, IntConsumer onFiltered) {
    Objects.requireNonNull(items);
    Objects.requireNonNull(onFiltered);
    Set<String> availableExtensionIds = new LinkedHashSet<>();
    for (ExtensionCatalogItem item : items) {
      availableExtensionIds.add(item.id());
    }
    navigation.retainAvailableSelections(availableExtensionIds);
    preferences.retainAvailable(availableExtensionIds);
    projection.replaceCatalog(items, query, navigation, preferences, onFiltered);
  }

  void scheduleRefresh(String query, IntConsumer onFiltered) {
    projection.scheduleRefresh(query, navigation, preferences, onFiltered);
  }

  void refreshNow(String query, IntConsumer onFiltered) {
    projection.refreshNow(query, navigation, preferences, onFiltered);
  }

  boolean toggleFavoritesOnlyFilter(IntConsumer onFiltered) {
    return projection.toggleFavoritesOnlyFilter(navigation, preferences, onFiltered);
  }

  boolean toggleSelectedOnlyFilter(IntConsumer onFiltered) {
    return projection.toggleSelectedOnlyFilter(navigation, preferences, onFiltered);
  }

  boolean clearCategoryFilter(IntConsumer onFiltered) {
    return projection.clearCategoryFilter(navigation, preferences, onFiltered);
  }

  void setPresetExtensionsByName(Map<String, List<String>> presetMap, IntConsumer onFiltered) {
    projection.setPresetExtensionsByName(presetMap, navigation, preferences, onFiltered);
  }

  PresetFilterResult cyclePresetFilter(IntConsumer onFiltered) {
    return projection.cyclePresetFilter(navigation, preferences, onFiltered);
  }

  boolean clearPresetFilter(IntConsumer onFiltered) {
    return projection.clearPresetFilter(navigation, preferences, onFiltered);
  }

  CategoryFilterResult cycleCategoryFilter(IntConsumer onFiltered) {
    return projection.cycleCategoryFilter(navigation, preferences, onFiltered);
  }

  FavoriteToggleResult toggleFavoriteAtSelection(IntConsumer onFiltered) {
    Objects.requireNonNull(onFiltered);
    ExtensionCatalogItem selected = selectedListItem();
    if (selected == null) {
      return FavoriteToggleResult.none();
    }
    boolean isNowFavorite = preferences.toggleFavorite(selected.id());
    projection.reapplyAfterSelectionMutation(navigation, preferences);
    return new FavoriteToggleResult(true, selected.name(), isNowFavorite);
  }

  CategoryCollapseResult toggleCategoryCollapseAtSelection() {
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

  int expandAllCategories() {
    int collapsedCount = projection.expandAllCategories();
    if (collapsedCount == 0) {
      return 0;
    }
    projection.refreshRows(selectedListItemId(), navigation, preferences);
    return collapsedCount;
  }

  JumpToFavoriteResult jumpToFavorite() {
    return navigation.jumpToFavorite(projection.rows(), preferences.favoriteIdsView());
  }

  SectionJumpResult jumpToAdjacentSection(boolean forward) {
    return navigation.jumpToAdjacentSection(projection.rows(), forward);
  }

  void cancelPendingAsync() {
    projection.cancelPendingAsync();
  }

  List<ExtensionCatalogItem> filteredExtensions() {
    return projection.filteredExtensions();
  }

  List<ExtensionCatalogRow> filteredRows() {
    return projection.filteredRows();
  }

  ListState listState() {
    return navigation.listState();
  }

  List<String> selectedExtensionIds() {
    return navigation.selectedExtensionIds();
  }

  int selectedExtensionCount() {
    return navigation.selectedExtensionCount();
  }

  int clearSelectedExtensions() {
    int clearedCount = navigation.clearSelectedExtensions();
    if (clearedCount == 0) {
      return 0;
    }
    projection.reapplyAfterSelectionMutation(navigation, preferences);
    return clearedCount;
  }

  int favoriteExtensionCount() {
    return preferences.favoriteExtensionCount();
  }

  int totalCatalogExtensionCount() {
    return projection.totalCatalogExtensionCount();
  }

  String focusedExtensionId() {
    String focusedId = selectedListItemId();
    return focusedId == null ? "" : focusedId;
  }

  String focusedExtensionDescription() {
    ExtensionCatalogItem selected = selectedListItem();
    return selected == null ? "" : selected.description();
  }

  boolean favoritesOnlyFilterEnabled() {
    return projection.favoritesOnlyFilterEnabled();
  }

  boolean selectedOnlyFilterEnabled() {
    return projection.selectedOnlyFilterEnabled();
  }

  String activeCategoryFilterTitle() {
    return projection.activeCategoryFilterTitle();
  }

  String activePresetFilterName() {
    return projection.activePresetFilterName();
  }

  boolean isSelectionAtTop() {
    return navigation.isSelectionAtTop(projection.rows());
  }

  boolean isSelected(String extensionId) {
    return navigation.isSelected(extensionId);
  }

  boolean isFavorite(String extensionId) {
    return preferences.isFavorite(extensionId);
  }

  boolean isCategorySectionHeaderSelected() {
    return projection.isCategorySectionHeaderSelected(navigation.selectedRow());
  }

  boolean isSelectedCategorySectionCollapsed() {
    return projection.isSelectedCategorySectionCollapsed(navigation.selectedRow());
  }

  String selectedSectionHeaderTitle() {
    return projection.selectedSectionHeaderTitle(navigation.selectedRow());
  }

  SectionFocusResult focusParentSectionHeader() {
    return navigation.focusParentSectionHeader(projection.rows());
  }

  SectionFocusResult focusFirstVisibleItemInSelectedSection() {
    return navigation.focusFirstVisibleItemInSelectedSection(projection.rows());
  }

  boolean handleListKeys(KeyEvent keyEvent, Consumer<String> onToggled) {
    Objects.requireNonNull(keyEvent);
    Objects.requireNonNull(onToggled);
    if (navigation.handleNavigationKey(projection.rows(), keyEvent)) {
      return true;
    }
    if (!keyEvent.isSelect()) {
      return false;
    }
    ExtensionCatalogItem extension = selectedListItem();
    if (extension == null) {
      return false;
    }
    if (navigation.select(extension.id())) {
      preferences.recordRecentSelection(extension.id());
      projection.refreshRows(extension.id(), navigation, preferences);
    } else {
      navigation.deselect(extension.id());
      projection.reapplyAfterSelectionMutation(navigation, preferences);
    }
    onToggled.accept(extension.name());
    return true;
  }

  private String selectedListItemId() {
    return projection.itemIdAtRow(navigation.selectedRow());
  }

  private ExtensionCatalogItem selectedListItem() {
    Integer selectedRow = navigation.selectedRow();
    return selectedRow == null ? null : projection.itemAtRow(selectedRow);
  }
}
