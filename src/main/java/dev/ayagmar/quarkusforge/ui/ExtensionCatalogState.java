package dev.ayagmar.quarkusforge.ui;

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
  private static final String RECENT_SECTION_TITLE = "Recently Selected";
  private static final int MAX_RECENT_SELECTIONS = 10;
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
  private ExtensionCatalogIndex catalogIndex;
  private List<ExtensionCatalogItem> filteredExtensions;
  private List<ExtensionCatalogRow> filteredRows;
  private List<Integer> selectableRowIndexes;
  private List<Integer> allRowIndexes;
  private Map<String, Integer> rowIndexByExtensionId;
  private String currentQuery;
  private boolean favoritesOnlyFilterEnabled;
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
    catalogIndex = new ExtensionCatalogIndex(DEFAULT_EXTENSIONS);
    filteredExtensions = List.of();
    filteredRows = List.of();
    selectableRowIndexes = List.of();
    allRowIndexes = List.of();
    rowIndexByExtensionId = Map.of();
    currentQuery = initialQuery == null ? "" : initialQuery;
    favoritesOnlyFilterEnabled = false;
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
    return clearedCount;
  }

  int favoriteExtensionCount() {
    return favoriteExtensionIds.size();
  }

  String focusedExtensionId() {
    ExtensionCatalogItem selected = selectedListItem();
    return selected == null ? "" : selected.id();
  }

  boolean favoritesOnlyFilterEnabled() {
    return favoritesOnlyFilterEnabled;
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
    return row.isSectionHeader() && !RECENT_SECTION_TITLE.equals(row.label());
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
    if (keyEvent.isUp() || isVimUpKey(keyEvent)) {
      selectPrevious(navigationRowIndexes);
      return true;
    }
    if (keyEvent.isDown() || isVimDownKey(keyEvent)) {
      selectNext(navigationRowIndexes);
      return true;
    }
    if (keyEvent.isHome() || isVimHomeKey(keyEvent)) {
      selectFirst(navigationRowIndexes);
      return true;
    }
    if (keyEvent.isEnd() || isVimEndKey(keyEvent)) {
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
      } else {
        recordRecentSelection(extension.id());
      }
      onToggled.accept(extension.name());
      return true;
    }
    return false;
  }

  private static boolean isVimUpKey(KeyEvent keyEvent) {
    return isPlainChar(keyEvent, 'k', 'K');
  }

  private static boolean isVimDownKey(KeyEvent keyEvent) {
    return isPlainChar(keyEvent, 'j', 'J');
  }

  private static boolean isVimHomeKey(KeyEvent keyEvent) {
    return isPlainChar(keyEvent, 'g', 'g');
  }

  private static boolean isVimEndKey(KeyEvent keyEvent) {
    return isPlainChar(keyEvent, 'G', 'G');
  }

  private static boolean isPlainChar(KeyEvent keyEvent, char lower, char upper) {
    return keyEvent.code() == dev.tamboui.tui.event.KeyCode.CHAR
        && !keyEvent.hasCtrl()
        && !keyEvent.hasAlt()
        && (keyEvent.character() == lower || keyEvent.character() == upper);
  }

  private void applyFiltered(String queryText, long token, IntConsumer onFiltered) {
    if (!searchResultGate.shouldApply(token)) {
      return;
    }
    currentQuery = queryText == null ? "" : queryText;
    String previousFocusedId = selectedListItemId();

    List<ExtensionCatalogItem> rankedResults =
        catalogIndex.search(currentQuery, favoriteExtensionIds);
    if (favoritesOnlyFilterEnabled) {
      rankedResults =
          rankedResults.stream().filter(item -> favoriteExtensionIds.contains(item.id())).toList();
    }

    filteredExtensions = rankedResults;
    collapsedCategoryTitles.retainAll(availableCategoryTitles(rankedResults));
    refreshRows(previousFocusedId);
    onFiltered.accept(filteredExtensions.size());
  }

  private void refreshRows(String previousFocusedExtensionId) {
    filteredRows = buildRows(filteredExtensions);
    selectableRowIndexes = buildSelectableIndexes(filteredRows);
    allRowIndexes = buildAllRowIndexes(filteredRows);
    rowIndexByExtensionId = buildRowIndexByExtensionId(filteredRows);
    restoreSelection(previousFocusedExtensionId);
  }

  private static Set<String> availableCategoryTitles(List<ExtensionCatalogItem> rankedItems) {
    Set<String> categoryTitles = new LinkedHashSet<>();
    for (ExtensionCatalogItem item : rankedItems) {
      categoryTitles.add(resolveCategoryTitle(item.categoryKey(), item.category()));
    }
    return categoryTitles;
  }

  private List<ExtensionCatalogRow> buildRows(List<ExtensionCatalogItem> rankedItems) {
    if (rankedItems.isEmpty()) {
      return List.of();
    }

    List<ExtensionCatalogRow> rows = new ArrayList<>();
    prependRecentRows(rows, rankedItems);

    Map<String, Integer> categoryItemCount = new LinkedHashMap<>();
    for (ExtensionCatalogItem item : rankedItems) {
      String categoryTitle = resolveCategoryTitle(item.categoryKey(), item.category());
      categoryItemCount.merge(categoryTitle, 1, Integer::sum);
    }

    String previousCategoryTitle = null;
    for (ExtensionCatalogItem item : rankedItems) {
      String categoryTitle = resolveCategoryTitle(item.categoryKey(), item.category());
      if (!categoryTitle.equals(previousCategoryTitle)) {
        boolean collapsed = collapsedCategoryTitles.contains(categoryTitle);
        int hiddenCount = collapsed ? categoryItemCount.getOrDefault(categoryTitle, 0) : 0;
        rows.add(ExtensionCatalogRow.section(categoryTitle, collapsed, hiddenCount));
        previousCategoryTitle = categoryTitle;
      }
      if (collapsedCategoryTitles.contains(categoryTitle)) {
        continue;
      }
      rows.add(ExtensionCatalogRow.item(item));
    }
    return List.copyOf(rows);
  }

  private void prependRecentRows(
      List<ExtensionCatalogRow> rows, List<ExtensionCatalogItem> rankedItems) {
    if (!currentQuery.isBlank() || favoritesOnlyFilterEnabled || recentExtensionIds.isEmpty()) {
      return;
    }
    List<ExtensionCatalogItem> recentItems = resolveRecentItems(rankedItems);
    if (recentItems.isEmpty()) {
      return;
    }
    rows.add(ExtensionCatalogRow.section(RECENT_SECTION_TITLE, false, 0));
    for (ExtensionCatalogItem item : recentItems) {
      rows.add(ExtensionCatalogRow.item(item));
    }
  }

  private List<ExtensionCatalogItem> resolveRecentItems(List<ExtensionCatalogItem> rankedItems) {
    Map<String, ExtensionCatalogItem> byId = new LinkedHashMap<>();
    for (ExtensionCatalogItem item : rankedItems) {
      byId.put(item.id(), item);
    }
    List<ExtensionCatalogItem> recentItems = new ArrayList<>();
    for (String recentId : recentExtensionIds) {
      ExtensionCatalogItem item = byId.get(recentId);
      if (item != null) {
        recentItems.add(item);
      }
      if (recentItems.size() >= MAX_RECENT_SELECTIONS) {
        break;
      }
    }
    return recentItems;
  }

  private static String resolveCategoryTitle(String categoryKey, String rawCategory) {
    return switch (categoryKey) {
      case "core" -> "Core";
      case "web" -> "Web";
      case "data" -> "Data";
      case "serialization" -> "Serialization";
      case "messaging" -> "Messaging";
      case "security" -> "Security";
      case "cloud" -> "Cloud";
      case "observability" -> "Observability";
      case "testing" -> "Testing";
      case "misc" -> "Misc";
      case "other" -> "Other";
      default -> titleCase(rawCategory);
    };
  }

  private static String titleCase(String value) {
    if (value == null || value.isBlank()) {
      return "Other";
    }
    String normalized = value.trim();
    if (normalized.length() == 1) {
      return normalized.toUpperCase(Locale.ROOT);
    }
    return normalized.substring(0, 1).toUpperCase(Locale.ROOT) + normalized.substring(1);
  }

  private static List<Integer> buildSelectableIndexes(List<ExtensionCatalogRow> rows) {
    List<Integer> indexes = new ArrayList<>();
    for (int i = 0; i < rows.size(); i++) {
      if (!rows.get(i).isSectionHeader()) {
        indexes.add(i);
      }
    }
    return List.copyOf(indexes);
  }

  private static Map<String, Integer> buildRowIndexByExtensionId(List<ExtensionCatalogRow> rows) {
    Map<String, Integer> indexes = new LinkedHashMap<>();
    for (int i = 0; i < rows.size(); i++) {
      ExtensionCatalogRow row = rows.get(i);
      if (row.extension() != null) {
        indexes.put(row.extension().id(), i);
      }
    }
    return Map.copyOf(indexes);
  }

  private static List<Integer> buildAllRowIndexes(List<ExtensionCatalogRow> rows) {
    List<Integer> indexes = new ArrayList<>();
    for (int i = 0; i < rows.size(); i++) {
      indexes.add(i);
    }
    return List.copyOf(indexes);
  }

  private void restoreSelection(String previouslyFocusedExtensionId) {
    if (selectableRowIndexes.isEmpty()) {
      listState.select(allRowIndexes.isEmpty() ? null : allRowIndexes.getFirst());
      return;
    }
    if (previouslyFocusedExtensionId != null) {
      Integer restoredIndex = rowIndexByExtensionId.get(previouslyFocusedExtensionId);
      if (restoredIndex != null) {
        listState.select(restoredIndex);
        return;
      }
    }
    listState.select(selectableRowIndexes.getFirst());
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
      if (RECENT_SECTION_TITLE.equals(row.label())) {
        return null;
      }
      return row.label();
    }
    ExtensionCatalogItem extension = row.extension();
    if (extension == null) {
      return null;
    }
    return resolveCategoryTitle(extension.categoryKey(), extension.category());
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
    while (recentExtensionIds.size() > MAX_RECENT_SELECTIONS) {
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
                () -> {
                  favoritesStore.saveFavoriteExtensionIds(favoriteSnapshot);
                  favoritesStore.saveRecentExtensionIds(recentSnapshot);
                },
                favoritesPersistenceExecutor);
  }

  record FavoriteToggleResult(boolean changed, String extensionName, boolean favoriteNow) {
    static FavoriteToggleResult none() {
      return new FavoriteToggleResult(false, "", false);
    }
  }

  record JumpToFavoriteResult(boolean jumped, String extensionName) {
    static JumpToFavoriteResult none() {
      return new JumpToFavoriteResult(false, "");
    }
  }

  record SectionJumpResult(boolean moved, String categoryTitle) {
    static SectionJumpResult none() {
      return new SectionJumpResult(false, "");
    }
  }

  record CategoryCollapseResult(boolean changed, String categoryTitle, boolean collapsed) {
    static CategoryCollapseResult none() {
      return new CategoryCollapseResult(false, "", false);
    }
  }

  record SectionFocusResult(boolean moved, String sectionTitle) {
    static SectionFocusResult none() {
      return new SectionFocusResult(false, "");
    }
  }
}
