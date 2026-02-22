package dev.ayagmar.quarkusforge.ui;

import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.list.ListState;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

final class ExtensionCatalogState {
  private static final List<ExtensionCatalogItem> DEFAULT_EXTENSIONS =
      List.of(
          new ExtensionCatalogItem("io.quarkus:quarkus-rest", "REST", "rest"),
          new ExtensionCatalogItem(
              "io.quarkus:quarkus-rest-jackson", "REST Jackson", "rest-jackson"),
          new ExtensionCatalogItem(
              "io.quarkus:quarkus-jdbc-postgresql", "JDBC PostgreSQL", "jdbc-postgresql"),
          new ExtensionCatalogItem(
              "io.quarkus:quarkus-hibernate-orm", "Hibernate ORM", "hibernate-orm"),
          new ExtensionCatalogItem("io.quarkus:quarkus-smallrye-openapi", "OpenAPI", "openapi"),
          new ExtensionCatalogItem("io.quarkus:quarkus-arc", "CDI", "cdi"),
          new ExtensionCatalogItem("io.quarkus:quarkus-junit5", "JUnit 5", "junit5"));

  private final ListState listState;
  private final Set<String> selectedExtensionIds;
  private final Debouncer searchDebouncer;
  private final LatestResultGate searchResultGate;
  private ExtensionCatalogIndex catalogIndex;
  private List<ExtensionCatalogItem> filteredExtensions;

  ExtensionCatalogState(UiScheduler scheduler, Duration debounceDelay, String initialQuery) {
    Objects.requireNonNull(scheduler);
    Objects.requireNonNull(debounceDelay);
    listState = new ListState();
    selectedExtensionIds = new LinkedHashSet<>();
    searchDebouncer = new Debouncer(scheduler, debounceDelay);
    searchResultGate = new LatestResultGate();
    catalogIndex = new ExtensionCatalogIndex(DEFAULT_EXTENSIONS);
    applyFiltered(initialQuery, searchResultGate.nextToken(), ignored -> {});
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
    catalogIndex = new ExtensionCatalogIndex(items);
    applyFiltered(query, searchResultGate.nextToken(), onFiltered);
  }

  void scheduleRefresh(String query, IntConsumer onFiltered) {
    Objects.requireNonNull(onFiltered);
    long token = searchResultGate.nextToken();
    searchDebouncer.submit(() -> applyFiltered(query, token, onFiltered));
  }

  void cancelPendingAsync() {
    searchDebouncer.cancel();
    searchResultGate.cancel();
  }

  List<ExtensionCatalogItem> filteredExtensions() {
    return filteredExtensions;
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

  boolean isSelected(String extensionId) {
    return selectedExtensionIds.contains(extensionId);
  }

  boolean handleListKeys(KeyEvent keyEvent, Consumer<String> onToggled) {
    Objects.requireNonNull(keyEvent);
    Objects.requireNonNull(onToggled);
    int size = filteredExtensions.size();
    if (size == 0) {
      return false;
    }
    if (keyEvent.isUp()) {
      listState.selectPrevious();
      return true;
    }
    if (keyEvent.isDown()) {
      listState.selectNext(size);
      return true;
    }
    if (keyEvent.isHome()) {
      listState.selectFirst();
      return true;
    }
    if (keyEvent.isEnd()) {
      listState.selectLast(size);
      return true;
    }
    if (keyEvent.isSelect()) {
      Integer selectedIndex = listState.selected();
      if (selectedIndex == null || selectedIndex < 0 || selectedIndex >= size) {
        return false;
      }
      ExtensionCatalogItem extension = filteredExtensions.get(selectedIndex);
      if (!selectedExtensionIds.add(extension.id())) {
        selectedExtensionIds.remove(extension.id());
      }
      onToggled.accept(extension.shortName());
      return true;
    }
    return false;
  }

  private void applyFiltered(String queryText, long token, IntConsumer onFiltered) {
    if (!searchResultGate.shouldApply(token)) {
      return;
    }

    filteredExtensions = catalogIndex.search(queryText);
    if (filteredExtensions.isEmpty()) {
      listState.select(null);
    } else if (listState.selected() == null || listState.selected() >= filteredExtensions.size()) {
      listState.selectFirst();
    }
    onFiltered.accept(filteredExtensions.size());
  }
}
