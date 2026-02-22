package dev.ayagmar.quarkusforge.ui;

import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.list.ListState;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
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

  private static final Map<String, Integer> CATEGORY_PRIORITY =
      Map.ofEntries(
          Map.entry("core", 0),
          Map.entry("web", 1),
          Map.entry("data", 2),
          Map.entry("serialization", 3),
          Map.entry("messaging", 4),
          Map.entry("security", 5),
          Map.entry("cloud", 6),
          Map.entry("observability", 7),
          Map.entry("testing", 8),
          Map.entry("misc", 9),
          Map.entry("other", 10));

  private final ListState listState;
  private final Set<String> selectedExtensionIds;
  private final Set<String> favoriteExtensionIds;
  private final Debouncer searchDebouncer;
  private final LatestResultGate searchResultGate;
  private final ExtensionFavoritesStore favoritesStore;
  private final Executor favoritesPersistenceExecutor;
  private ExtensionCatalogIndex catalogIndex;
  private List<ExtensionCatalogItem> filteredExtensions;
  private List<ExtensionCatalogRow> filteredRows;
  private List<Integer> selectableRowIndexes;
  private Map<String, Integer> rowIndexByExtensionId;
  private String currentQuery;
  private boolean favoritesOnlyFilterEnabled;
  private CompletableFuture<Void> favoritePersistenceChain;

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
    searchDebouncer = new Debouncer(scheduler, debounceDelay);
    searchResultGate = new LatestResultGate();
    catalogIndex = new ExtensionCatalogIndex(DEFAULT_EXTENSIONS);
    filteredExtensions = List.of();
    filteredRows = List.of();
    selectableRowIndexes = List.of();
    rowIndexByExtensionId = Map.of();
    currentQuery = initialQuery == null ? "" : initialQuery;
    favoritesOnlyFilterEnabled = false;
    favoritePersistenceChain = CompletableFuture.completedFuture(null);
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
    persistFavoritesAsync();
    catalogIndex = new ExtensionCatalogIndex(items);
    applyFiltered(query, searchResultGate.nextToken(), onFiltered);
  }

  void scheduleRefresh(String query, IntConsumer onFiltered) {
    Objects.requireNonNull(onFiltered);
    long token = searchResultGate.nextToken();
    searchDebouncer.submit(() -> applyFiltered(query, token, onFiltered));
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
    persistFavoritesAsync();
    applyFiltered(currentQuery, searchResultGate.nextToken(), onFiltered);
    return new FavoriteToggleResult(true, selected.name(), isNowFavorite);
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
    if (selected == null || selectableRowIndexes.isEmpty()) {
      return true;
    }
    return selected <= selectableRowIndexes.getFirst();
  }

  boolean isSelected(String extensionId) {
    return selectedExtensionIds.contains(extensionId);
  }

  boolean isFavorite(String extensionId) {
    return favoriteExtensionIds.contains(extensionId);
  }

  boolean handleListKeys(KeyEvent keyEvent, Consumer<String> onToggled) {
    Objects.requireNonNull(keyEvent);
    Objects.requireNonNull(onToggled);
    if (selectableRowIndexes.isEmpty()) {
      return false;
    }
    if (keyEvent.isUp()) {
      selectPrevious();
      return true;
    }
    if (keyEvent.isDown()) {
      selectNext();
      return true;
    }
    if (keyEvent.isHome()) {
      selectFirst();
      return true;
    }
    if (keyEvent.isEnd()) {
      selectLast();
      return true;
    }
    if (keyEvent.isSelect()) {
      ExtensionCatalogItem extension = selectedListItem();
      if (extension == null) {
        return false;
      }
      if (!selectedExtensionIds.add(extension.id())) {
        selectedExtensionIds.remove(extension.id());
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
    if (favoritesOnlyFilterEnabled) {
      rankedResults =
          rankedResults.stream().filter(item -> favoriteExtensionIds.contains(item.id())).toList();
    }

    filteredExtensions = rankedResults;
    filteredRows = buildRows(rankedResults);
    selectableRowIndexes = buildSelectableIndexes(filteredRows);
    rowIndexByExtensionId = buildRowIndexByExtensionId(filteredRows);
    restoreSelection(previousFocusedId);
    onFiltered.accept(filteredExtensions.size());
  }

  private List<ExtensionCatalogRow> buildRows(List<ExtensionCatalogItem> rankedItems) {
    if (rankedItems.isEmpty()) {
      return List.of();
    }

    List<ExtensionCatalogRow> rows = new ArrayList<>();
    List<ExtensionCatalogItem> favorites =
        rankedItems.stream().filter(item -> favoriteExtensionIds.contains(item.id())).toList();
    if (!favorites.isEmpty()) {
      rows.add(ExtensionCatalogRow.section("Favorites"));
      for (ExtensionCatalogItem favorite : favorites) {
        rows.add(ExtensionCatalogRow.item(favorite));
      }
    }

    List<ExtensionCatalogItem> remaining =
        favorites.isEmpty()
            ? rankedItems
            : rankedItems.stream()
                .filter(item -> !favoriteExtensionIds.contains(item.id()))
                .toList();
    if (remaining.isEmpty()) {
      return List.copyOf(rows);
    }

    Map<String, CategorySection> sectionsByCategory = new LinkedHashMap<>();
    for (ExtensionCatalogItem item : remaining) {
      sectionsByCategory
          .computeIfAbsent(
              item.categoryKey(),
              ignored ->
                  new CategorySection(resolveCategoryTitle(item.categoryKey(), item.category())))
          .items()
          .add(item);
    }

    List<Map.Entry<String, CategorySection>> sortedSections =
        new ArrayList<>(sectionsByCategory.entrySet());
    sortedSections.sort(
        Comparator.comparingInt(
                (Map.Entry<String, CategorySection> entry) -> categoryRank(entry.getKey()))
            .thenComparing(entry -> entry.getValue().title().toLowerCase(Locale.ROOT)));

    for (Map.Entry<String, CategorySection> entry : sortedSections) {
      rows.add(ExtensionCatalogRow.section(entry.getValue().title()));
      for (ExtensionCatalogItem item : entry.getValue().items()) {
        rows.add(ExtensionCatalogRow.item(item));
      }
    }
    return List.copyOf(rows);
  }

  private static int categoryRank(String categoryKey) {
    return CATEGORY_PRIORITY.getOrDefault(categoryKey, Integer.MAX_VALUE);
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

  private void restoreSelection(String previouslyFocusedExtensionId) {
    if (selectableRowIndexes.isEmpty()) {
      listState.select(null);
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

  private void selectPrevious() {
    int currentPosition = selectedPosition();
    if (currentPosition <= 0) {
      listState.select(selectableRowIndexes.getFirst());
      return;
    }
    listState.select(selectableRowIndexes.get(currentPosition - 1));
  }

  private void selectNext() {
    int currentPosition = selectedPosition();
    int nextPosition = Math.min(currentPosition + 1, selectableRowIndexes.size() - 1);
    listState.select(selectableRowIndexes.get(nextPosition));
  }

  private void selectFirst() {
    listState.select(selectableRowIndexes.getFirst());
  }

  private void selectLast() {
    listState.select(selectableRowIndexes.getLast());
  }

  private int selectedPosition() {
    Integer selected = listState.selected();
    if (selected == null) {
      return 0;
    }
    int position = selectableRowIndexes.indexOf(selected);
    return position < 0 ? 0 : position;
  }

  private void persistFavoritesAsync() {
    Set<String> snapshot = Set.copyOf(favoriteExtensionIds);
    favoritePersistenceChain =
        favoritePersistenceChain
            .exceptionally(ignored -> null)
            .thenRunAsync(
                () -> favoritesStore.saveFavoriteExtensionIds(snapshot),
                favoritesPersistenceExecutor);
  }

  private record CategorySection(String title, List<ExtensionCatalogItem> items) {
    private CategorySection(String title) {
      this(title, new ArrayList<>());
    }
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
}
