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
  private static final List<ExtensionCatalogItem> DEFAULT_EXTENSIONS =
      List.of(
          new ExtensionCatalogItem("io.quarkus:quarkus-arc", "CDI", "cdi", "Core", 10),
          new ExtensionCatalogItem("io.quarkus:quarkus-rest", "REST", "rest", "Web", 20),
          new ExtensionCatalogItem(
              "io.quarkus:quarkus-rest-jackson", "REST Jackson", "rest-jackson", "Web", 21),
          new ExtensionCatalogItem(
              "io.quarkus:quarkus-smallrye-openapi", "OpenAPI", "openapi", "Web", 30),
          new ExtensionCatalogItem(
              "io.quarkus:quarkus-hibernate-orm", "Hibernate ORM", "hibernate-orm", "Data", 40),
          new ExtensionCatalogItem(
              "io.quarkus:quarkus-jdbc-postgresql",
              "JDBC PostgreSQL",
              "jdbc-postgresql",
              "Data",
              41),
          new ExtensionCatalogItem(
              "io.quarkus:quarkus-junit5", "JUnit 5", "junit5", "Testing", 90));

  private final Debouncer searchDebouncer;
  private final LatestResultGate searchResultGate;
  private final ExtensionCatalogPreferences preferences;
  private final ExtensionCatalogFilters filters;
  private final ExtensionCatalogRows rows;
  private final ExtensionCatalogNavigation navigation;
  private ExtensionCatalogIndex catalogIndex;

  ExtensionCatalogState(UiScheduler scheduler, Duration debounceDelay, String initialQuery) {
    this(scheduler, debounceDelay, initialQuery, ExtensionFavoritesStore.inMemory(), Runnable::run);
  }

  ExtensionCatalogState(
      UiScheduler scheduler,
      Duration debounceDelay,
      String initialQuery,
      ExtensionFavoritesStore favoritesStore,
      Executor favoritesPersistenceExecutor) {
    Objects.requireNonNull(scheduler);
    Objects.requireNonNull(debounceDelay);
    searchDebouncer = new Debouncer(scheduler, debounceDelay);
    searchResultGate = new LatestResultGate();
    preferences =
        new ExtensionCatalogPreferences(
            Objects.requireNonNull(favoritesStore),
            Objects.requireNonNull(favoritesPersistenceExecutor));
    filters = new ExtensionCatalogFilters(initialQuery);
    rows = new ExtensionCatalogRows();
    navigation = new ExtensionCatalogNavigation();
    catalogIndex = new ExtensionCatalogIndex(DEFAULT_EXTENSIONS);
    applyFiltered(filters.currentQuery(), searchResultGate.nextToken(), ignored -> {});
  }

  void replaceCatalog(List<ExtensionCatalogItem> items, String query, IntConsumer onFiltered) {
    Objects.requireNonNull(items);
    Objects.requireNonNull(onFiltered);
    Set<String> availableExtensionIds = new LinkedHashSet<>();
    for (ExtensionCatalogItem item : items) {
      availableExtensionIds.add(item.id());
    }
    navigation.selectedIdsView().removeIf(id -> !availableExtensionIds.contains(id));
    preferences.retainAvailable(availableExtensionIds);
    catalogIndex = new ExtensionCatalogIndex(items);
    applyFiltered(query, searchResultGate.nextToken(), onFiltered);
  }

  void scheduleRefresh(String query, IntConsumer onFiltered) {
    Objects.requireNonNull(onFiltered);
    long token = searchResultGate.nextToken();
    searchDebouncer.submit(() -> applyFiltered(query, token, onFiltered));
  }

  void refreshNow(String query, IntConsumer onFiltered) {
    Objects.requireNonNull(onFiltered);
    applyFiltered(query, searchResultGate.nextToken(), onFiltered);
  }

  boolean toggleFavoritesOnlyFilter(IntConsumer onFiltered) {
    Objects.requireNonNull(onFiltered);
    boolean enabled = filters.toggleFavoritesOnly();
    applyFiltered(filters.currentQuery(), searchResultGate.nextToken(), onFiltered);
    return enabled;
  }

  boolean toggleSelectedOnlyFilter(IntConsumer onFiltered) {
    Objects.requireNonNull(onFiltered);
    boolean enabled = filters.toggleSelectedOnly();
    applyFiltered(filters.currentQuery(), searchResultGate.nextToken(), onFiltered);
    return enabled;
  }

  boolean clearCategoryFilter(IntConsumer onFiltered) {
    Objects.requireNonNull(onFiltered);
    if (!filters.clearCategoryFilter()) {
      return false;
    }
    applyFiltered(filters.currentQuery(), searchResultGate.nextToken(), onFiltered);
    return true;
  }

  void setPresetExtensionsByName(Map<String, List<String>> presetMap, IntConsumer onFiltered) {
    Objects.requireNonNull(onFiltered);
    filters.setPresetExtensionsByName(presetMap);
    applyFiltered(filters.currentQuery(), searchResultGate.nextToken(), onFiltered);
  }

  PresetFilterResult cyclePresetFilter(IntConsumer onFiltered) {
    Objects.requireNonNull(onFiltered);
    List<String> presets = filters.availablePresetNames();
    filters.cyclePresetFilter(presets);
    applyFiltered(filters.currentQuery(), searchResultGate.nextToken(), onFiltered);
    if (filters.activePresetFilterName().isBlank()) {
      return PresetFilterResult.none(rows.filteredExtensions().size());
    }
    return new PresetFilterResult(
        true, filters.activePresetFilterName(), rows.filteredExtensions().size());
  }

  boolean clearPresetFilter(IntConsumer onFiltered) {
    Objects.requireNonNull(onFiltered);
    if (!filters.clearPresetFilter()) {
      return false;
    }
    applyFiltered(filters.currentQuery(), searchResultGate.nextToken(), onFiltered);
    return true;
  }

  CategoryFilterResult cycleCategoryFilter(IntConsumer onFiltered) {
    Objects.requireNonNull(onFiltered);
    List<String> categoryTitles =
        filters.filterableCategoryTitles(
            catalogIndex, navigation.selectedIdsView(), preferences.favoriteIdsView());
    filters.cycleCategoryFilter(categoryTitles);
    applyFiltered(filters.currentQuery(), searchResultGate.nextToken(), onFiltered);
    if (filters.activeCategoryFilterTitle().isBlank()) {
      return CategoryFilterResult.none(rows.filteredExtensions().size());
    }
    return new CategoryFilterResult(
        true, filters.activeCategoryFilterTitle(), rows.filteredExtensions().size());
  }

  FavoriteToggleResult toggleFavoriteAtSelection(IntConsumer onFiltered) {
    Objects.requireNonNull(onFiltered);
    ExtensionCatalogItem selected = selectedListItem();
    if (selected == null) {
      return FavoriteToggleResult.none();
    }
    boolean isNowFavorite = preferences.toggleFavorite(selected.id());
    applyFiltered(filters.currentQuery(), searchResultGate.nextToken(), onFiltered);
    return new FavoriteToggleResult(true, selected.name(), isNowFavorite);
  }

  CategoryCollapseResult toggleCategoryCollapseAtSelection() {
    Integer selectedRow = navigation.selectedRow();
    if (selectedRow == null) {
      return CategoryCollapseResult.none();
    }
    String categoryTitle = rows.categoryTitleForRow(selectedRow);
    if (categoryTitle == null) {
      return CategoryCollapseResult.none();
    }

    boolean collapsed = rows.toggleCategoryCollapse(categoryTitle);
    refreshRows(selectedListItemId());
    Integer sectionHeaderIndex = rows.sectionHeaderRowIndex(categoryTitle);
    if (sectionHeaderIndex != null) {
      navigation.listState().select(sectionHeaderIndex);
    }
    return new CategoryCollapseResult(true, categoryTitle, collapsed);
  }

  int expandAllCategories() {
    int collapsedCount = rows.expandAllCategories();
    if (collapsedCount == 0) {
      return 0;
    }
    refreshRows(selectedListItemId());
    return collapsedCount;
  }

  JumpToFavoriteResult jumpToFavorite() {
    return navigation.jumpToFavorite(rows, preferences.favoriteIdsView());
  }

  SectionJumpResult jumpToAdjacentSection(boolean forward) {
    return navigation.jumpToAdjacentSection(rows, forward);
  }

  void cancelPendingAsync() {
    searchDebouncer.cancel();
    searchResultGate.cancel();
  }

  List<ExtensionCatalogItem> filteredExtensions() {
    return rows.filteredExtensions();
  }

  List<ExtensionCatalogRow> filteredRows() {
    return rows.filteredRows();
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
    reapplyFiltersAfterSelectionMutation();
    return clearedCount;
  }

  int favoriteExtensionCount() {
    return preferences.favoriteExtensionCount();
  }

  int totalCatalogExtensionCount() {
    return catalogIndex.totalItemCount();
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
    return filters.favoritesOnlyFilterEnabled();
  }

  boolean selectedOnlyFilterEnabled() {
    return filters.selectedOnlyFilterEnabled();
  }

  String activeCategoryFilterTitle() {
    return filters.activeCategoryFilterTitle();
  }

  String activePresetFilterName() {
    return filters.activePresetFilterName();
  }

  boolean isSelectionAtTop() {
    return navigation.isSelectionAtTop(rows);
  }

  boolean isSelected(String extensionId) {
    return navigation.isSelected(extensionId);
  }

  boolean isFavorite(String extensionId) {
    return preferences.isFavorite(extensionId);
  }

  boolean isCategorySectionHeaderSelected() {
    return rows.isCategorySectionHeaderSelected(navigation.selectedRow());
  }

  boolean isSelectedCategorySectionCollapsed() {
    return rows.isSelectedCategorySectionCollapsed(navigation.selectedRow());
  }

  String selectedSectionHeaderTitle() {
    return rows.selectedSectionHeaderTitle(navigation.selectedRow());
  }

  SectionFocusResult focusParentSectionHeader() {
    return navigation.focusParentSectionHeader(rows);
  }

  SectionFocusResult focusFirstVisibleItemInSelectedSection() {
    return navigation.focusFirstVisibleItemInSelectedSection(rows);
  }

  boolean handleListKeys(KeyEvent keyEvent, Consumer<String> onToggled) {
    Objects.requireNonNull(keyEvent);
    Objects.requireNonNull(onToggled);
    if (navigation.handleNavigationKey(rows, keyEvent)) {
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
      refreshRows(extension.id());
    } else {
      navigation.deselect(extension.id());
      reapplyFiltersAfterSelectionMutation();
    }
    onToggled.accept(extension.name());
    return true;
  }

  private void applyFiltered(String queryText, long token, IntConsumer onFiltered) {
    if (!searchResultGate.shouldApply(token)) {
      return;
    }
    filters.updateQuery(queryText);
    String previousFocusedId = selectedListItemId();
    Set<String> favoriteIds = preferences.favoriteIdsView();
    Set<String> selectedIds = navigation.selectedIdsView();

    List<ExtensionCatalogItem> rankedResults =
        catalogIndex.search(filters.currentQuery(), favoriteIds);
    Set<String> availableCategoryTitles =
        new LinkedHashSet<>(
            filters.filterableCategoryTitles(catalogIndex, selectedIds, favoriteIds));
    filters.reconcileAvailableCategories(availableCategoryTitles);
    rankedResults = filters.apply(rankedResults, selectedIds, favoriteIds);

    rows.retainAvailableCategories(availableCategoryTitles);
    rows.update(rankedResults, preferences.recentIdsView(), filters);
    navigation.restoreSelection(rows, previousFocusedId);
    onFiltered.accept(rows.filteredExtensions().size());
  }

  private void refreshRows(String previousFocusedExtensionId) {
    rows.update(rows.filteredExtensions(), preferences.recentIdsView(), filters);
    navigation.restoreSelection(rows, previousFocusedExtensionId);
  }

  private void reapplyFiltersAfterSelectionMutation() {
    applyFiltered(filters.currentQuery(), searchResultGate.nextToken(), ignored -> {});
  }

  private String selectedListItemId() {
    return rows.itemIdAtRow(navigation.selectedRow());
  }

  private ExtensionCatalogItem selectedListItem() {
    Integer selectedRow = navigation.selectedRow();
    return selectedRow == null ? null : rows.itemAtRow(selectedRow);
  }
}
