package dev.ayagmar.quarkusforge.ui;

import dev.ayagmar.quarkusforge.api.ExtensionFavoritesStore;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.list.ListState;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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

  private final ListState listState;
  private final Set<String> selectedExtensionIds;
  private final Set<String> favoriteExtensionIds;
  private final List<String> recentExtensionIds;
  private final Debouncer searchDebouncer;
  private final LatestResultGate searchResultGate;
  private final ExtensionFavoritesStore favoritesStore;
  private final Executor favoritesPersistenceExecutor;
  private final Set<String> collapsedCategoryTitles;
  private Map<String, List<String>> presetExtensionsByName;
  private ExtensionCatalogIndex catalogIndex;
  private List<ExtensionCatalogItem> filteredExtensions;
  private List<ExtensionCatalogRow> filteredRows;
  private List<Integer> selectableRowIndexes;
  private List<Integer> allRowIndexes;
  private Map<String, Integer> rowIndexByExtensionId;
  private String currentQuery;
  private boolean favoritesOnlyFilterEnabled;
  private boolean selectedOnlyFilterEnabled;
  private String activeCategoryFilterTitle;
  private String activePresetFilterName;
  private CompletableFuture<Void> preferencePersistenceChain;

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
    this.favoritesStore = Objects.requireNonNull(favoritesStore);
    this.favoritesPersistenceExecutor = Objects.requireNonNull(favoritesPersistenceExecutor);
    listState = new ListState();
    selectedExtensionIds = new LinkedHashSet<>();
    favoriteExtensionIds = new LinkedHashSet<>(favoritesStore.loadFavoriteExtensionIds());
    recentExtensionIds = new ArrayList<>(favoritesStore.loadRecentExtensionIds());
    searchDebouncer = new Debouncer(scheduler, debounceDelay);
    searchResultGate = new LatestResultGate();
    collapsedCategoryTitles = new LinkedHashSet<>();
    presetExtensionsByName = Map.of();
    catalogIndex = new ExtensionCatalogIndex(DEFAULT_EXTENSIONS);
    filteredExtensions = List.of();
    filteredRows = List.of();
    selectableRowIndexes = List.of();
    allRowIndexes = List.of();
    rowIndexByExtensionId = Map.of();
    currentQuery = initialQuery == null ? "" : initialQuery;
    favoritesOnlyFilterEnabled = false;
    selectedOnlyFilterEnabled = false;
    activeCategoryFilterTitle = "";
    activePresetFilterName = "";
    preferencePersistenceChain = CompletableFuture.completedFuture(null);
    applyFiltered(currentQuery, searchResultGate.nextToken(), ignored -> {});
  }

  void replaceCatalog(List<ExtensionCatalogItem> items, String query, IntConsumer onFiltered) {
    Objects.requireNonNull(items);
    Objects.requireNonNull(onFiltered);
    Set<String> availableExtensionIds = new LinkedHashSet<>();
    for (ExtensionCatalogItem item : items) {
      availableExtensionIds.add(item.id());
    }
    selectedExtensionIds.removeIf(
        selectedExtensionId -> !availableExtensionIds.contains(selectedExtensionId));
    favoriteExtensionIds.removeIf(favoriteId -> !availableExtensionIds.contains(favoriteId));
    recentExtensionIds.removeIf(recentId -> !availableExtensionIds.contains(recentId));
    persistUserStateAsync();
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
    favoritesOnlyFilterEnabled = !favoritesOnlyFilterEnabled;
    applyFiltered(currentQuery, searchResultGate.nextToken(), onFiltered);
    return favoritesOnlyFilterEnabled;
  }

  boolean toggleSelectedOnlyFilter(IntConsumer onFiltered) {
    Objects.requireNonNull(onFiltered);
    selectedOnlyFilterEnabled = !selectedOnlyFilterEnabled;
    applyFiltered(currentQuery, searchResultGate.nextToken(), onFiltered);
    return selectedOnlyFilterEnabled;
  }

  boolean clearCategoryFilter(IntConsumer onFiltered) {
    Objects.requireNonNull(onFiltered);
    if (activeCategoryFilterTitle.isBlank()) {
      return false;
    }
    activeCategoryFilterTitle = "";
    applyFiltered(currentQuery, searchResultGate.nextToken(), onFiltered);
    return true;
  }

  void setPresetExtensionsByName(Map<String, List<String>> presetMap, IntConsumer onFiltered) {
    Objects.requireNonNull(onFiltered);
    presetExtensionsByName = normalizePresetMap(presetMap);
    if (!activePresetFilterName.isBlank()
        && !presetExtensionsByName.containsKey(activePresetFilterName)) {
      activePresetFilterName = "";
    }
    applyFiltered(currentQuery, searchResultGate.nextToken(), onFiltered);
  }

  PresetFilterResult cyclePresetFilter(IntConsumer onFiltered) {
    Objects.requireNonNull(onFiltered);
    List<String> presets = availablePresetNames();
    if (presets.isEmpty()) {
      activePresetFilterName = "";
      applyFiltered(currentQuery, searchResultGate.nextToken(), onFiltered);
      return PresetFilterResult.none(filteredExtensions.size());
    }

    if (activePresetFilterName.isBlank()) {
      activePresetFilterName = presets.getFirst();
    } else {
      int index = presets.indexOf(activePresetFilterName);
      if (index < 0 || index >= presets.size() - 1) {
        activePresetFilterName = "";
      } else {
        activePresetFilterName = presets.get(index + 1);
      }
    }
    applyFiltered(currentQuery, searchResultGate.nextToken(), onFiltered);
    if (activePresetFilterName.isBlank()) {
      return PresetFilterResult.none(filteredExtensions.size());
    }
    return new PresetFilterResult(true, activePresetFilterName, filteredExtensions.size());
  }

  boolean clearPresetFilter(IntConsumer onFiltered) {
    Objects.requireNonNull(onFiltered);
    if (activePresetFilterName.isBlank()) {
      return false;
    }
    activePresetFilterName = "";
    applyFiltered(currentQuery, searchResultGate.nextToken(), onFiltered);
    return true;
  }

  CategoryFilterResult cycleCategoryFilter(IntConsumer onFiltered) {
    Objects.requireNonNull(onFiltered);
    List<String> categoryTitles = filterableCategoryTitles();
    if (categoryTitles.isEmpty()) {
      activeCategoryFilterTitle = "";
      applyFiltered(currentQuery, searchResultGate.nextToken(), onFiltered);
      return CategoryFilterResult.none(filteredExtensions.size());
    }

    if (activeCategoryFilterTitle.isBlank()) {
      activeCategoryFilterTitle = categoryTitles.getFirst();
    } else {
      int index = categoryTitles.indexOf(activeCategoryFilterTitle);
      if (index < 0 || index >= categoryTitles.size() - 1) {
        activeCategoryFilterTitle = "";
      } else {
        activeCategoryFilterTitle = categoryTitles.get(index + 1);
      }
    }
    applyFiltered(currentQuery, searchResultGate.nextToken(), onFiltered);
    if (activeCategoryFilterTitle.isBlank()) {
      return CategoryFilterResult.none(filteredExtensions.size());
    }
    return new CategoryFilterResult(true, activeCategoryFilterTitle, filteredExtensions.size());
  }

  FavoriteToggleResult toggleFavoriteAtSelection(IntConsumer onFiltered) {
    Objects.requireNonNull(onFiltered);
    ExtensionCatalogItem selected = selectedListItem();
    if (selected == null) {
      return FavoriteToggleResult.none();
    }
    boolean isNowFavorite = favoriteExtensionIds.add(selected.id());
    if (!isNowFavorite) {
      favoriteExtensionIds.remove(selected.id());
    }
    persistUserStateAsync();
    applyFiltered(currentQuery, searchResultGate.nextToken(), onFiltered);
    return new FavoriteToggleResult(true, selected.name(), isNowFavorite);
  }

  CategoryCollapseResult toggleCategoryCollapseAtSelection() {
    Integer selectedRow = listState.selected();
    if (selectedRow == null) {
      return CategoryCollapseResult.none();
    }
    String categoryTitle = selectedCategoryTitleForRow(selectedRow);
    if (categoryTitle == null) {
      return CategoryCollapseResult.none();
    }

    boolean collapsed = collapsedCategoryTitles.add(categoryTitle);
    if (!collapsed) {
      collapsedCategoryTitles.remove(categoryTitle);
    }
    refreshRows(selectedListItemId());
    Integer sectionHeaderIndex = sectionHeaderRowIndex(categoryTitle);
    if (sectionHeaderIndex != null) {
      listState.select(sectionHeaderIndex);
    }
    return new CategoryCollapseResult(true, categoryTitle, collapsed);
  }

  int expandAllCategories() {
    int collapsedCount = collapsedCategoryTitles.size();
    if (collapsedCount == 0) {
      return 0;
    }
    String previousFocusedId = selectedListItemId();
    collapsedCategoryTitles.clear();
    refreshRows(previousFocusedId);
    return collapsedCount;
  }

  JumpToFavoriteResult jumpToFavorite() {
    if (selectableRowIndexes.isEmpty()) {
      return JumpToFavoriteResult.none();
    }
    List<Integer> favoriteRows = favoriteRowIndexes();
    if (favoriteRows.isEmpty()) {
      return JumpToFavoriteResult.none();
    }

    Integer currentRow = listState.selected();
    int nextFavoriteRow = favoriteRows.getFirst();
    if (currentRow != null) {
      for (Integer favoriteRow : favoriteRows) {
        if (favoriteRow > currentRow) {
          nextFavoriteRow = favoriteRow;
          break;
        }
      }
    }

    listState.select(nextFavoriteRow);
    ExtensionCatalogItem focusedItem = rowToItem(nextFavoriteRow);
    return focusedItem == null
        ? JumpToFavoriteResult.none()
        : new JumpToFavoriteResult(true, focusedItem.name());
  }

  SectionJumpResult jumpToAdjacentSection(boolean forward) {
    if (filteredRows.isEmpty()) {
      return SectionJumpResult.none();
    }
    Integer currentSelection = listState.selected();
    int start = currentSelection == null ? (forward ? -1 : filteredRows.size()) : currentSelection;
    int step = forward ? 1 : -1;
    for (int i = start + step; i >= 0 && i < filteredRows.size(); i += step) {
      ExtensionCatalogRow row = filteredRows.get(i);
      if (!row.isSectionHeader()) {
        continue;
      }
      if (CatalogRowBuilder.RECENT_SECTION_TITLE.equals(row.label())) {
        continue;
      }
      listState.select(i);
      return new SectionJumpResult(true, row.label());
    }
    return SectionJumpResult.none();
  }

  void cancelPendingAsync() {
    searchDebouncer.cancel();
    searchResultGate.cancel();
  }

  List<ExtensionCatalogItem> filteredExtensions() {
    return filteredExtensions;
  }

  List<ExtensionCatalogRow> filteredRows() {
    return filteredRows;
  }

  ListState listState() {
    return listState;
  }

  List<String> selectedExtensionIds() {
    return List.copyOf(selectedExtensionIds);
  }

  int selectedExtensionCount() {
    return selectedExtensionIds.size();
  }

  int clearSelectedExtensions() {
    int clearedCount = selectedExtensionIds.size();
    if (clearedCount == 0) {
      return 0;
    }
    selectedExtensionIds.clear();
    reapplyFiltersAfterSelectionMutation();
    return clearedCount;
  }

  int favoriteExtensionCount() {
    return favoriteExtensionIds.size();
  }

  int totalCatalogExtensionCount() {
    return catalogIndex.totalItemCount();
  }

  String focusedExtensionId() {
    ExtensionCatalogItem selected = selectedListItem();
    return selected == null ? "" : selected.id();
  }

  String focusedExtensionDescription() {
    ExtensionCatalogItem selected = selectedListItem();
    return selected == null ? "" : selected.description();
  }

  boolean favoritesOnlyFilterEnabled() {
    return favoritesOnlyFilterEnabled;
  }

  boolean selectedOnlyFilterEnabled() {
    return selectedOnlyFilterEnabled;
  }

  String activeCategoryFilterTitle() {
    return activeCategoryFilterTitle;
  }

  String activePresetFilterName() {
    return activePresetFilterName;
  }

  boolean isSelectionAtTop() {
    Integer selected = listState.selected();
    List<Integer> navigationRowIndexes = navigationRowIndexes(selected);
    if (selected == null || navigationRowIndexes.isEmpty()) {
      return true;
    }
    return selected <= navigationRowIndexes.getFirst();
  }

  boolean isSelected(String extensionId) {
    return selectedExtensionIds.contains(extensionId);
  }

  boolean isFavorite(String extensionId) {
    return favoriteExtensionIds.contains(extensionId);
  }

  boolean isCategorySectionHeaderSelected() {
    Integer selectedRow = listState.selected();
    if (selectedRow == null || selectedRow < 0 || selectedRow >= filteredRows.size()) {
      return false;
    }
    ExtensionCatalogRow row = filteredRows.get(selectedRow);
    return row.isSectionHeader() && !CatalogRowBuilder.RECENT_SECTION_TITLE.equals(row.label());
  }

  boolean isSelectedCategorySectionCollapsed() {
    if (!isCategorySectionHeaderSelected()) {
      return false;
    }
    Integer selectedRow = listState.selected();
    return selectedRow != null && filteredRows.get(selectedRow).collapsed();
  }

  String selectedSectionHeaderTitle() {
    Integer selectedRow = listState.selected();
    if (selectedRow == null || selectedRow < 0 || selectedRow >= filteredRows.size()) {
      return "";
    }
    ExtensionCatalogRow row = filteredRows.get(selectedRow);
    return row.isSectionHeader() ? row.label() : "";
  }

  SectionFocusResult focusParentSectionHeader() {
    Integer selectedRow = listState.selected();
    if (selectedRow == null || selectedRow <= 0 || selectedRow >= filteredRows.size()) {
      return SectionFocusResult.none();
    }
    if (filteredRows.get(selectedRow).isSectionHeader()) {
      return SectionFocusResult.none();
    }
    for (int i = selectedRow - 1; i >= 0; i--) {
      ExtensionCatalogRow row = filteredRows.get(i);
      if (!row.isSectionHeader()) {
        continue;
      }
      listState.select(i);
      return new SectionFocusResult(true, row.label());
    }
    return SectionFocusResult.none();
  }

  SectionFocusResult focusFirstVisibleItemInSelectedSection() {
    Integer selectedRow = listState.selected();
    if (selectedRow == null
        || selectedRow < 0
        || selectedRow >= filteredRows.size()
        || !filteredRows.get(selectedRow).isSectionHeader()) {
      return SectionFocusResult.none();
    }
    int childIndex = selectedRow + 1;
    if (childIndex >= filteredRows.size()) {
      return SectionFocusResult.none();
    }
    ExtensionCatalogRow childRow = filteredRows.get(childIndex);
    if (childRow.isSectionHeader()) {
      return SectionFocusResult.none();
    }
    listState.select(childIndex);
    return new SectionFocusResult(true, filteredRows.get(selectedRow).label());
  }

  boolean handleListKeys(KeyEvent keyEvent, Consumer<String> onToggled) {
    Objects.requireNonNull(keyEvent);
    Objects.requireNonNull(onToggled);
    Integer selectedRow = listState.selected();
    List<Integer> navigationRowIndexes = navigationRowIndexes(selectedRow);
    if (navigationRowIndexes.isEmpty()) {
      return false;
    }
    if (keyEvent.isUp() || UiKeyMatchers.isVimUpKey(keyEvent)) {
      selectPrevious(navigationRowIndexes);
      return true;
    }
    if (keyEvent.isDown() || UiKeyMatchers.isVimDownKey(keyEvent)) {
      selectNext(navigationRowIndexes);
      return true;
    }
    if (keyEvent.isHome() || UiKeyMatchers.isVimHomeKey(keyEvent)) {
      selectFirst(navigationRowIndexes);
      return true;
    }
    if (keyEvent.isEnd() || UiKeyMatchers.isVimEndKey(keyEvent)) {
      selectLast(navigationRowIndexes);
      return true;
    }
    if (keyEvent.isSelect()) {
      ExtensionCatalogItem extension = selectedListItem();
      if (extension == null) {
        return false;
      }
      boolean selectedNow = selectedExtensionIds.add(extension.id());
      if (!selectedNow) {
        selectedExtensionIds.remove(extension.id());
        reapplyFiltersAfterSelectionMutation();
      } else {
        recordRecentSelection(extension.id());
      }
      onToggled.accept(extension.name());
      return true;
    }
    return false;
  }

  private void applyFiltered(String queryText, long token, IntConsumer onFiltered) {
    if (!searchResultGate.shouldApply(token)) {
      return;
    }
    currentQuery = queryText == null ? "" : queryText;
    String previousFocusedId = selectedListItemId();

    List<ExtensionCatalogItem> rankedResults =
        catalogIndex.search(currentQuery, favoriteExtensionIds);
    rankedResults = applyFavoritesAndPresetFilters(rankedResults);

    Set<String> availableCategoryTitles = new LinkedHashSet<>(filterableCategoryTitles());
    if (!activeCategoryFilterTitle.isBlank()
        && !availableCategoryTitles.contains(activeCategoryFilterTitle)) {
      activeCategoryFilterTitle = "";
    }

    if (!activeCategoryFilterTitle.isBlank()) {
      rankedResults =
          rankedResults.stream()
              .filter(
                  item ->
                      activeCategoryFilterTitle.equals(
                          CatalogRowBuilder.resolveCategoryTitle(
                              item.categoryKey(), item.category())))
              .toList();
    }

    filteredExtensions = rankedResults;
    collapsedCategoryTitles.retainAll(availableCategoryTitles);
    refreshRows(previousFocusedId);
    onFiltered.accept(filteredExtensions.size());
  }

  private List<String> filterableCategoryTitles() {
    List<ExtensionCatalogItem> rankedResults = catalogIndex.search("", favoriteExtensionIds);
    rankedResults = applyFavoritesAndPresetFilters(rankedResults);
    return List.copyOf(availableCategoryTitles(rankedResults));
  }

  private List<ExtensionCatalogItem> applyFavoritesAndPresetFilters(
      List<ExtensionCatalogItem> items) {
    List<ExtensionCatalogItem> result = items;
    if (selectedOnlyFilterEnabled) {
      result = result.stream().filter(item -> selectedExtensionIds.contains(item.id())).toList();
    }
    if (favoritesOnlyFilterEnabled) {
      result = result.stream().filter(item -> favoriteExtensionIds.contains(item.id())).toList();
    }
    if (!activePresetFilterName.isBlank()) {
      List<String> presetExtensions = presetExtensionsByName.get(activePresetFilterName);
      Set<String> allowedIds =
          new LinkedHashSet<>(presetExtensions == null ? List.of() : presetExtensions);
      result = result.stream().filter(item -> allowedIds.contains(item.id())).toList();
    }
    return result;
  }

  private void refreshRows(String previousFocusedExtensionId) {
    filteredRows =
        CatalogRowBuilder.buildRows(
            filteredExtensions,
            collapsedCategoryTitles,
            recentExtensionIds,
            currentQuery,
            favoritesOnlyFilterEnabled,
            selectedOnlyFilterEnabled,
            activePresetFilterName);
    selectableRowIndexes = CatalogRowBuilder.buildSelectableIndexes(filteredRows);
    allRowIndexes = CatalogRowBuilder.buildAllRowIndexes(filteredRows);
    rowIndexByExtensionId = CatalogRowBuilder.buildRowIndexByExtensionId(filteredRows);
    restoreSelection(previousFocusedExtensionId);
  }

  private static Set<String> availableCategoryTitles(List<ExtensionCatalogItem> rankedItems) {
    return CatalogRowBuilder.availableCategoryTitles(rankedItems);
  }

  private void reapplyFiltersAfterSelectionMutation() {
    applyFiltered(currentQuery, searchResultGate.nextToken(), ignored -> {});
  }

  private static Map<String, List<String>> normalizePresetMap(Map<String, List<String>> presetMap) {
    if (presetMap == null || presetMap.isEmpty()) {
      return Map.of();
    }
    LinkedHashMap<String, List<String>> normalized = new LinkedHashMap<>();
    for (Map.Entry<String, List<String>> entry : presetMap.entrySet()) {
      if (entry.getKey() == null || entry.getKey().isBlank()) {
        continue;
      }
      String key = entry.getKey().trim().toLowerCase(Locale.ROOT);
      List<String> extensions =
          entry.getValue() == null ? List.of() : List.copyOf(entry.getValue());
      normalized.put(key, extensions);
    }
    return Map.copyOf(normalized);
  }

  private List<String> availablePresetNames() {
    if (presetExtensionsByName.isEmpty()) {
      return List.of();
    }
    List<String> names = new ArrayList<>(presetExtensionsByName.keySet());
    names.sort(String::compareTo);
    return List.copyOf(names);
  }

  private void restoreSelection(String previouslyFocusedExtensionId) {
    if (allRowIndexes.isEmpty()) {
      listState.select(null);
      return;
    }

    if (selectableRowIndexes.isEmpty()) {
      listState.select(allRowIndexes.getFirst());
      return;
    }

    if (previouslyFocusedExtensionId != null) {
      Integer restoredIndex = rowIndexByExtensionId.get(previouslyFocusedExtensionId);
      if (restoredIndex != null) {
        listState.select(restoredIndex);
        return;
      }

      int firstSelectable = selectableRowIndexes.getFirst();
      // If the previously focused extension disappeared (e.g. filters changed or its category was
      // collapsed) and the first selectable row is far from the top, prefer selecting the first
      // real section header instead of skipping early collapsed categories.
      if (firstSelectable > 1) {
        Integer firstSectionHeader = firstNonRecentSectionHeaderIndex();
        if (firstSectionHeader != null) {
          listState.select(firstSectionHeader);
          return;
        }
      }
    }

    int firstSelectable = selectableRowIndexes.getFirst();
    // If early categories are collapsed, the first selectable row can be far from the top;
    // selecting the first section header prevents skipping the initial categories.
    if (previouslyFocusedExtensionId == null && firstSelectable > 1) {
      Integer firstSectionHeader = firstNonRecentSectionHeaderIndex();
      if (firstSectionHeader != null) {
        listState.select(firstSectionHeader);
        return;
      }
    }

    listState.select(firstSelectable);
  }

  private Integer firstNonRecentSectionHeaderIndex() {
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

  private String selectedListItemId() {
    ExtensionCatalogItem selected = selectedListItem();
    return selected == null ? null : selected.id();
  }

  private ExtensionCatalogItem selectedListItem() {
    Integer selectedRow = listState.selected();
    return selectedRow == null ? null : rowToItem(selectedRow);
  }

  private ExtensionCatalogItem rowToItem(int rowIndex) {
    if (rowIndex < 0 || rowIndex >= filteredRows.size()) {
      return null;
    }
    return filteredRows.get(rowIndex).extension();
  }

  private List<Integer> favoriteRowIndexes() {
    List<Integer> indexes = new ArrayList<>();
    for (Integer rowIndex : selectableRowIndexes) {
      ExtensionCatalogItem rowItem = rowToItem(rowIndex);
      if (rowItem != null && favoriteExtensionIds.contains(rowItem.id())) {
        indexes.add(rowIndex);
      }
    }
    return indexes;
  }

  private void selectPrevious(List<Integer> navigationRowIndexes) {
    int currentPosition = selectedPosition(navigationRowIndexes);
    if (currentPosition <= 0) {
      listState.select(navigationRowIndexes.getFirst());
      return;
    }
    listState.select(navigationRowIndexes.get(currentPosition - 1));
  }

  private void selectNext(List<Integer> navigationRowIndexes) {
    int currentPosition = selectedPosition(navigationRowIndexes);
    int nextPosition = Math.min(currentPosition + 1, navigationRowIndexes.size() - 1);
    listState.select(navigationRowIndexes.get(nextPosition));
  }

  private void selectFirst(List<Integer> navigationRowIndexes) {
    listState.select(navigationRowIndexes.getFirst());
  }

  private void selectLast(List<Integer> navigationRowIndexes) {
    listState.select(navigationRowIndexes.getLast());
  }

  private int selectedPosition(List<Integer> navigationRowIndexes) {
    Integer selected = listState.selected();
    if (selected == null) {
      return -1;
    }
    int position = navigationRowIndexes.indexOf(selected);
    if (position >= 0) {
      return position;
    }

    int nearestPreviousSelectable = -1;
    for (int i = 0; i < navigationRowIndexes.size(); i++) {
      if (navigationRowIndexes.get(i) <= selected) {
        nearestPreviousSelectable = i;
        continue;
      }
      break;
    }
    return nearestPreviousSelectable;
  }

  private List<Integer> navigationRowIndexes(Integer selectedRow) {
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

  private String selectedCategoryTitleForRow(int rowIndex) {
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
    if (extension == null) {
      return null;
    }
    if (isUnderRecentSection(rowIndex)) {
      return null;
    }
    return CatalogRowBuilder.resolveCategoryTitle(extension.categoryKey(), extension.category());
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

  private Integer sectionHeaderRowIndex(String categoryTitle) {
    for (int i = 0; i < filteredRows.size(); i++) {
      ExtensionCatalogRow row = filteredRows.get(i);
      if (row.isSectionHeader() && row.label().equals(categoryTitle)) {
        return i;
      }
    }
    return null;
  }

  private void recordRecentSelection(String extensionId) {
    recentExtensionIds.remove(extensionId);
    recentExtensionIds.addFirst(extensionId);
    while (recentExtensionIds.size() > CatalogRowBuilder.MAX_RECENT_SELECTIONS) {
      recentExtensionIds.removeLast();
    }
    persistUserStateAsync();
    refreshRows(extensionId);
  }

  private void persistUserStateAsync() {
    Set<String> favoriteSnapshot = Set.copyOf(favoriteExtensionIds);
    List<String> recentSnapshot = List.copyOf(recentExtensionIds);
    preferencePersistenceChain =
        preferencePersistenceChain
            .exceptionally(ignored -> null)
            .thenRunAsync(
                () -> favoritesStore.saveAll(favoriteSnapshot, recentSnapshot),
                favoritesPersistenceExecutor);
  }
}
