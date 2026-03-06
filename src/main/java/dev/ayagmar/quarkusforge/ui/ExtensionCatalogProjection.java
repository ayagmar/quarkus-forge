package dev.ayagmar.quarkusforge.ui;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntConsumer;

final class ExtensionCatalogProjection {
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
  private final ExtensionCatalogFilters filters;
  private final ExtensionCatalogRows rows;
  private ExtensionCatalogIndex catalogIndex;

  ExtensionCatalogProjection(UiScheduler scheduler, Duration debounceDelay, String initialQuery) {
    Objects.requireNonNull(scheduler);
    Objects.requireNonNull(debounceDelay);
    searchDebouncer = new Debouncer(scheduler, debounceDelay);
    searchResultGate = new LatestResultGate();
    filters = new ExtensionCatalogFilters(initialQuery);
    rows = new ExtensionCatalogRows();
    catalogIndex = new ExtensionCatalogIndex(DEFAULT_EXTENSIONS);
  }

  void initialize(ExtensionCatalogNavigation navigation, ExtensionCatalogPreferences preferences) {
    refreshNow(filters.currentQuery(), navigation, preferences, ignored -> {});
  }

  void replaceCatalog(
      List<ExtensionCatalogItem> items,
      String query,
      ExtensionCatalogNavigation navigation,
      ExtensionCatalogPreferences preferences,
      IntConsumer onFiltered) {
    Objects.requireNonNull(items);
    Objects.requireNonNull(onFiltered);
    catalogIndex = new ExtensionCatalogIndex(items);
    applyFiltered(query, searchResultGate.nextToken(), navigation, preferences, onFiltered);
  }

  void scheduleRefresh(
      String query,
      ExtensionCatalogNavigation navigation,
      ExtensionCatalogPreferences preferences,
      IntConsumer onFiltered) {
    Objects.requireNonNull(onFiltered);
    long token = searchResultGate.nextToken();
    searchDebouncer.submit(() -> applyFiltered(query, token, navigation, preferences, onFiltered));
  }

  void refreshNow(
      String query,
      ExtensionCatalogNavigation navigation,
      ExtensionCatalogPreferences preferences,
      IntConsumer onFiltered) {
    Objects.requireNonNull(onFiltered);
    applyFiltered(query, searchResultGate.nextToken(), navigation, preferences, onFiltered);
  }

  boolean toggleFavoritesOnlyFilter(
      ExtensionCatalogNavigation navigation,
      ExtensionCatalogPreferences preferences,
      IntConsumer onFiltered) {
    Objects.requireNonNull(onFiltered);
    boolean enabled = filters.toggleFavoritesOnly();
    applyFiltered(
        filters.currentQuery(), searchResultGate.nextToken(), navigation, preferences, onFiltered);
    return enabled;
  }

  boolean toggleSelectedOnlyFilter(
      ExtensionCatalogNavigation navigation,
      ExtensionCatalogPreferences preferences,
      IntConsumer onFiltered) {
    Objects.requireNonNull(onFiltered);
    boolean enabled = filters.toggleSelectedOnly();
    applyFiltered(
        filters.currentQuery(), searchResultGate.nextToken(), navigation, preferences, onFiltered);
    return enabled;
  }

  boolean clearCategoryFilter(
      ExtensionCatalogNavigation navigation,
      ExtensionCatalogPreferences preferences,
      IntConsumer onFiltered) {
    Objects.requireNonNull(onFiltered);
    if (!filters.clearCategoryFilter()) {
      return false;
    }
    applyFiltered(
        filters.currentQuery(), searchResultGate.nextToken(), navigation, preferences, onFiltered);
    return true;
  }

  void setPresetExtensionsByName(
      Map<String, List<String>> presetMap,
      ExtensionCatalogNavigation navigation,
      ExtensionCatalogPreferences preferences,
      IntConsumer onFiltered) {
    Objects.requireNonNull(onFiltered);
    filters.setPresetExtensionsByName(presetMap);
    applyFiltered(
        filters.currentQuery(), searchResultGate.nextToken(), navigation, preferences, onFiltered);
  }

  PresetFilterResult cyclePresetFilter(
      ExtensionCatalogNavigation navigation,
      ExtensionCatalogPreferences preferences,
      IntConsumer onFiltered) {
    Objects.requireNonNull(onFiltered);
    filters.cyclePresetFilter(filters.availablePresetNames());
    applyFiltered(
        filters.currentQuery(), searchResultGate.nextToken(), navigation, preferences, onFiltered);
    if (filters.activePresetFilterName().isBlank()) {
      return PresetFilterResult.none(rows.filteredExtensions().size());
    }
    return new PresetFilterResult(
        true, filters.activePresetFilterName(), rows.filteredExtensions().size());
  }

  boolean clearPresetFilter(
      ExtensionCatalogNavigation navigation,
      ExtensionCatalogPreferences preferences,
      IntConsumer onFiltered) {
    Objects.requireNonNull(onFiltered);
    if (!filters.clearPresetFilter()) {
      return false;
    }
    applyFiltered(
        filters.currentQuery(), searchResultGate.nextToken(), navigation, preferences, onFiltered);
    return true;
  }

  CategoryFilterResult cycleCategoryFilter(
      ExtensionCatalogNavigation navigation,
      ExtensionCatalogPreferences preferences,
      IntConsumer onFiltered) {
    Objects.requireNonNull(onFiltered);
    List<String> categoryTitles =
        filters.filterableCategoryTitles(
            filters.currentQuery(),
            catalogIndex,
            navigation.selectedIdsView(),
            preferences.favoriteIdsView());
    filters.cycleCategoryFilter(categoryTitles);
    applyFiltered(
        filters.currentQuery(), searchResultGate.nextToken(), navigation, preferences, onFiltered);
    if (filters.activeCategoryFilterTitle().isBlank()) {
      return CategoryFilterResult.none(rows.filteredExtensions().size());
    }
    return new CategoryFilterResult(
        true, filters.activeCategoryFilterTitle(), rows.filteredExtensions().size());
  }

  void reapplyAfterSelectionMutation(
      ExtensionCatalogNavigation navigation, ExtensionCatalogPreferences preferences) {
    applyFiltered(
        filters.currentQuery(),
        searchResultGate.nextToken(),
        navigation,
        preferences,
        ignored -> {});
  }

  void refreshRows(
      String previousFocusedExtensionId,
      ExtensionCatalogNavigation navigation,
      ExtensionCatalogPreferences preferences) {
    rows.update(rows.filteredExtensions(), preferences.recentIdsView(), filters);
    navigation.restoreSelection(rows, previousFocusedExtensionId);
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

  int totalCatalogExtensionCount() {
    return catalogIndex.totalItemCount();
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

  boolean toggleCategoryCollapse(String categoryTitle) {
    return rows.toggleCategoryCollapse(categoryTitle);
  }

  int expandAllCategories() {
    return rows.expandAllCategories();
  }

  Integer sectionHeaderRowIndex(String categoryTitle) {
    return rows.sectionHeaderRowIndex(categoryTitle);
  }

  String categoryTitleForRow(int rowIndex) {
    return rows.categoryTitleForRow(rowIndex);
  }

  String itemIdAtRow(Integer rowIndex) {
    return rows.itemIdAtRow(rowIndex);
  }

  ExtensionCatalogItem itemAtRow(int rowIndex) {
    return rows.itemAtRow(rowIndex);
  }

  boolean isCategorySectionHeaderSelected(Integer selectedRow) {
    return rows.isCategorySectionHeaderSelected(selectedRow);
  }

  boolean isSelectedCategorySectionCollapsed(Integer selectedRow) {
    return rows.isSelectedCategorySectionCollapsed(selectedRow);
  }

  String selectedSectionHeaderTitle(Integer selectedRow) {
    return rows.selectedSectionHeaderTitle(selectedRow);
  }

  ExtensionCatalogRows rows() {
    return rows;
  }

  private void applyFiltered(
      String queryText,
      long token,
      ExtensionCatalogNavigation navigation,
      ExtensionCatalogPreferences preferences,
      IntConsumer onFiltered) {
    if (!searchResultGate.shouldApply(token)) {
      return;
    }
    filters.updateQuery(queryText);
    String previousFocusedId = itemIdAtRow(navigation.selectedRow());
    Set<String> favoriteIds = preferences.favoriteIdsView();
    Set<String> selectedIds = navigation.selectedIdsView();

    List<ExtensionCatalogItem> rankedResults =
        catalogIndex.search(filters.currentQuery(), favoriteIds);
    Set<String> availableCategoryTitles =
        new LinkedHashSet<>(
            filters.filterableCategoryTitles(
                filters.currentQuery(), catalogIndex, selectedIds, favoriteIds));
    filters.reconcileAvailableCategories(availableCategoryTitles);
    rankedResults = filters.apply(rankedResults, selectedIds, favoriteIds);

    rows.retainAvailableCategories(availableCategoryTitles);
    rows.update(rankedResults, preferences.recentIdsView(), filters);
    navigation.restoreSelection(rows, previousFocusedId);
    onFiltered.accept(rows.filteredExtensions().size());
  }
}
