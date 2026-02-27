package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.api.CatalogSource;
import dev.ayagmar.quarkusforge.api.ExtensionDto;
import dev.ayagmar.quarkusforge.api.MetadataDto;
import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import dev.tamboui.tui.event.TickEvent;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoreTuiExtensionSearchPilotTest {
  @TempDir Path tempDir;

  @Test
  void apiLoadedCatalogIsIndexedAndSearchable() {
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), UiScheduler.immediate(), Duration.ZERO);
    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                CoreTuiController.ExtensionCatalogLoadResult.live(
                    List.of(
                        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest"),
                        new ExtensionDto(
                            "io.quarkus:quarkus-smallrye-openapi", "OpenAPI", "openapi"),
                        new ExtensionDto(
                            "io.quarkus:quarkus-jdbc-postgresql",
                            "JDBC PostgreSQL",
                            "jdbc-postgresql")))));

    moveFocusTo(controller, FocusTarget.EXTENSION_SEARCH);
    controller.onEvent(KeyEvent.ofChar('j'));
    controller.onEvent(KeyEvent.ofChar('d'));
    controller.onEvent(KeyEvent.ofChar('b'));

    assertThat(controller.filteredExtensionCount()).isEqualTo(1);
    assertThat(controller.firstFilteredExtensionId()).contains("jdbc-postgresql");
  }

  @Test
  void selectionByStableIdPersistsAcrossFiltering() {
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), UiScheduler.immediate(), Duration.ZERO);
    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                CoreTuiController.ExtensionCatalogLoadResult.live(
                    List.of(
                        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest"),
                        new ExtensionDto(
                            "io.quarkus:quarkus-rest-client", "REST Client", "rest-client"),
                        new ExtensionDto(
                            "io.quarkus:quarkus-jdbc-postgresql",
                            "JDBC PostgreSQL",
                            "jdbc-postgresql")))));

    moveFocusTo(controller, FocusTarget.EXTENSION_LIST);
    controller.onEvent(KeyEvent.ofChar(' '));
    String selectedId = controller.selectedExtensionIds().getFirst();

    moveFocusTo(controller, FocusTarget.EXTENSION_SEARCH);
    for (char character : "jdbc".toCharArray()) {
      controller.onEvent(KeyEvent.ofChar(character));
    }
    assertThat(controller.selectedExtensionIds()).contains(selectedId);

    for (int i = 0; i < 4; i++) {
      controller.onEvent(KeyEvent.ofKey(KeyCode.BACKSPACE));
    }
    assertThat(controller.selectedExtensionIds()).contains(selectedId);
  }

  @Test
  void failedCatalogLoadKeepsFallbackCatalogAndShowsError() {
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), UiScheduler.immediate(), Duration.ZERO);

    controller.loadExtensionCatalogAsync(
        () -> CompletableFuture.failedFuture(new IllegalStateException("network down")));

    assertThat(controller.statusMessage()).isEqualTo("Using fallback extension catalog");
    assertThat(controller.filteredExtensionCount()).isEqualTo(7);
    assertThat(renderToString(controller)).contains("Error: Catalog load failed: network down");
  }

  @Test
  void synchronousCatalogLoaderFailureKeepsFallbackCatalogAndShowsError() {
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), UiScheduler.immediate(), Duration.ZERO);

    controller.loadExtensionCatalogAsync(
        () -> {
          throw new IllegalStateException("loader crashed");
        });

    assertThat(controller.statusMessage()).isEqualTo("Using fallback extension catalog");
    assertThat(controller.filteredExtensionCount()).isEqualTo(7);
    assertThat(renderToString(controller)).contains("Error: Catalog load failed: loader crashed");
  }

  @Test
  void nullCatalogLoaderFutureKeepsFallbackCatalogAndShowsError() {
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), UiScheduler.immediate(), Duration.ZERO);

    controller.loadExtensionCatalogAsync(() -> null);

    assertThat(controller.statusMessage()).isEqualTo("Using fallback extension catalog");
    assertThat(controller.filteredExtensionCount()).isEqualTo(7);
    assertThat(renderToString(controller))
        .contains("Error: Catalog load failed: loader returned null future");
  }

  @Test
  void emptyCatalogLoadKeepsFallbackCatalogAndShowsError() {
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), UiScheduler.immediate(), Duration.ZERO);

    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                CoreTuiController.ExtensionCatalogLoadResult.live(List.of())));

    assertThat(controller.statusMessage()).isEqualTo("Using fallback extension catalog");
    assertThat(controller.filteredExtensionCount()).isEqualTo(7);
    assertThat(renderToString(controller)).contains("Error: Catalog load returned no extensions");
  }

  @Test
  void selectionsArePrunedWhenCatalogReloadRemovesIds() {
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), UiScheduler.immediate(), Duration.ZERO);
    moveFocusTo(controller, FocusTarget.EXTENSION_LIST);
    controller.onEvent(KeyEvent.ofChar(' '));
    assertThat(controller.selectedExtensionIds()).hasSize(1);

    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                CoreTuiController.ExtensionCatalogLoadResult.live(
                    List.of(
                        new ExtensionDto(
                            "io.quarkus:quarkus-jdbc-postgresql",
                            "JDBC PostgreSQL",
                            "jdbc-postgresql")))));

    assertThat(controller.selectedExtensionIds()).isEmpty();
  }

  @Test
  void asyncCatalogCompletionIsIgnoredAfterQuit() {
    QueueingScheduler scheduler = new QueueingScheduler();
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), scheduler, Duration.ZERO);
    CompletableFuture<CoreTuiController.ExtensionCatalogLoadResult> loadFuture =
        new CompletableFuture<>();
    controller.loadExtensionCatalogAsync(() -> loadFuture);
    assertThat(controller.statusMessage()).isEqualTo("Loading extension catalog...");

    CoreTuiController.UiAction quitAction = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
    assertThat(quitAction.shouldQuit()).isTrue();

    loadFuture.complete(
        CoreTuiController.ExtensionCatalogLoadResult.live(
            List.of(new ExtensionDto("io.quarkus:quarkus-funqy", "Funqy", "funqy"))));
    scheduler.runAll();

    assertThat(controller.statusMessage()).isEqualTo("Loading extension catalog...");
    assertThat(controller.filteredExtensionCount()).isEqualTo(7);
  }

  @Test
  void tickEventRequestsRepaintAfterAsyncCatalogCompletion() {
    QueueingScheduler scheduler = new QueueingScheduler();
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), scheduler, Duration.ZERO);
    CompletableFuture<CoreTuiController.ExtensionCatalogLoadResult> loadFuture =
        new CompletableFuture<>();
    controller.loadExtensionCatalogAsync(() -> loadFuture);

    loadFuture.complete(
        CoreTuiController.ExtensionCatalogLoadResult.live(
            List.of(new ExtensionDto("io.quarkus:quarkus-funqy", "Funqy", "funqy"))));
    scheduler.runAll();

    CoreTuiController.UiAction tickAction =
        controller.onEvent(TickEvent.of(1, Duration.ofMillis(40)));
    assertThat(tickAction.handled()).isTrue();
    assertThat(controller.statusMessage()).contains("Loaded extension catalog from live API");
    assertThat(controller.filteredExtensionCount()).isEqualTo(1);
  }

  @Test
  void cachedCatalogResultShowsSourceAndStaleIndicator() {
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), UiScheduler.immediate(), Duration.ZERO);

    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                new CoreTuiController.ExtensionCatalogLoadResult(
                    List.of(new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest")),
                    CatalogSource.CACHE,
                    true,
                    "Live catalog unavailable (network down); using stale cached snapshot",
                    sampleMetadata())));

    String rendered = renderToString(controller);
    assertThat(rendered).contains("Catalog: cache [stale]");
    assertThat(rendered).doesNotContain("Catalog: cache [stale] | error:");
    assertThat(controller.statusMessage()).contains("stale cached snapshot");
  }

  @Test
  void ctrlRReloadsCatalogWithoutRestart() {
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), UiScheduler.immediate(), Duration.ZERO);
    AtomicInteger callCount = new AtomicInteger();

    controller.loadExtensionCatalogAsync(
        () -> {
          int call = callCount.incrementAndGet();
          if (call == 1) {
            return CompletableFuture.failedFuture(new IllegalStateException("network down"));
          }
          return CompletableFuture.completedFuture(
              new CoreTuiController.ExtensionCatalogLoadResult(
                  List.of(
                      new ExtensionDto(
                          "io.quarkus:quarkus-jdbc-postgresql",
                          "JDBC PostgreSQL",
                          "jdbc-postgresql")),
                  CatalogSource.CACHE,
                  false,
                  "Live catalog unavailable (network down); using cached snapshot",
                  sampleMetadata()));
        });

    assertThat(controller.statusMessage()).isEqualTo("Using fallback extension catalog");
    controller.onEvent(KeyEvent.ofChar('r', KeyModifiers.CTRL));

    assertThat(callCount.get()).isEqualTo(2);
    assertThat(controller.firstFilteredExtensionId()).contains("jdbc-postgresql");
    assertThat(controller.statusMessage())
        .contains("Live catalog unavailable (network down); using cached snapshot");
  }

  @Test
  void failedReloadKeepsPreviouslyLoadedCatalogSourceAndItems() {
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), UiScheduler.immediate(), Duration.ZERO);

    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                CoreTuiController.ExtensionCatalogLoadResult.live(
                    List.of(
                        new ExtensionDto(
                            "io.quarkus:quarkus-jdbc-postgresql",
                            "JDBC PostgreSQL",
                            "jdbc-postgresql")))));
    assertThat(controller.firstFilteredExtensionId())
        .isEqualTo("io.quarkus:quarkus-jdbc-postgresql");
    assertThat(renderToString(controller)).contains("Catalog: live");

    controller.loadExtensionCatalogAsync(
        () -> CompletableFuture.failedFuture(new IllegalStateException("network down")));

    assertThat(controller.firstFilteredExtensionId())
        .isEqualTo("io.quarkus:quarkus-jdbc-postgresql");
    assertThat(renderToString(controller)).contains("Catalog: live");
    assertThat(controller.statusMessage())
        .isEqualTo("Catalog reload failed; keeping current catalog");
  }

  @Test
  void extensionListAutoScrollKeepsDeepSelectionVisible() {
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), UiScheduler.immediate(), Duration.ZERO);
    List<ExtensionDto> manyExtensions = new ArrayList<>();
    for (int i = 0; i < 120; i++) {
      String suffix = "%03d".formatted(i);
      manyExtensions.add(
          new ExtensionDto(
              "io.quarkus:quarkus-test-" + suffix, "Test Extension " + suffix, "test-" + suffix));
    }
    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                CoreTuiController.ExtensionCatalogLoadResult.live(manyExtensions)));

    moveFocusTo(controller, FocusTarget.EXTENSION_LIST);
    for (int i = 0; i < 80; i++) {
      controller.onEvent(KeyEvent.ofKey(KeyCode.DOWN));
    }

    String rendered = renderToString(controller);
    assertThat(rendered).contains("Test Extension 080");
    assertThat(rendered).doesNotContain("Test Extension 000");
  }

  @Test
  void catalogUsesStableCategorySectionHeaders() {
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), UiScheduler.immediate(), Duration.ZERO);

    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                CoreTuiController.ExtensionCatalogLoadResult.live(
                    List.of(
                        new ExtensionDto(
                            "io.quarkus:quarkus-jdbc-postgresql",
                            "JDBC PostgreSQL",
                            "jdbc-postgresql",
                            "Data",
                            30),
                        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest", "Web", 20),
                        new ExtensionDto("io.quarkus:quarkus-arc", "CDI", "cdi", "Core", 10)))));

    assertThat(controller.catalogSectionHeaders()).containsExactly("Core", "Web", "Data");
  }

  @Test
  void listOrderFollowsRankingAcrossCategories() {
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), UiScheduler.immediate(), Duration.ZERO);

    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                CoreTuiController.ExtensionCatalogLoadResult.live(
                    List.of(
                        new ExtensionDto(
                            "io.quarkus:quarkus-jdbc-postgresql",
                            "JDBC PostgreSQL",
                            "jdbc-postgresql",
                            "Data",
                            5),
                        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest", "Web", 10)))));

    assertThat(controller.focusedListExtensionId()).isEqualTo("io.quarkus:quarkus-jdbc-postgresql");
  }

  @Test
  void favoritingDoesNotOverrideApiOrderPrecedenceInList() {
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), UiScheduler.immediate(), Duration.ZERO);

    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                CoreTuiController.ExtensionCatalogLoadResult.live(
                    List.of(
                        new ExtensionDto("io.quarkus:quarkus-arc", "CDI", "cdi", "Core", 10),
                        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest", "Web", 20)))));
    moveFocusTo(controller, FocusTarget.EXTENSION_LIST);
    controller.onEvent(KeyEvent.ofKey(KeyCode.DOWN));
    controller.onEvent(KeyEvent.ofChar('f'));
    controller.onEvent(KeyEvent.ofKey(KeyCode.HOME));

    assertThat(controller.focusedListExtensionId()).isEqualTo("io.quarkus:quarkus-arc");
    assertThat(controller.favoriteExtensionCount()).isEqualTo(1);
  }

  @Test
  void selectionAndFavoriteRemainStableWhenCatalogOrderChanges() {
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), UiScheduler.immediate(), Duration.ZERO);
    String favoriteId = "io.quarkus:quarkus-rest";
    String otherId = "io.quarkus:quarkus-arc";

    controller.loadExtensionCatalogAsync(
        () -> CompletableFuture.completedFuture(withOrder(favoriteId, 30, otherId, 10)));

    moveFocusTo(controller, FocusTarget.EXTENSION_LIST);
    controller.onEvent(KeyEvent.ofKey(KeyCode.DOWN));
    assertThat(controller.focusedListExtensionId()).isEqualTo(favoriteId);
    controller.onEvent(KeyEvent.ofChar(' '));
    controller.onEvent(KeyEvent.ofChar('f'));
    assertThat(controller.selectedExtensionIds()).contains(favoriteId);
    assertThat(controller.favoriteExtensionCount()).isEqualTo(1);

    controller.loadExtensionCatalogAsync(
        () -> CompletableFuture.completedFuture(withOrder(favoriteId, 5, otherId, 40)));

    assertThat(controller.selectedExtensionIds()).contains(favoriteId);
    assertThat(controller.favoriteExtensionCount()).isEqualTo(1);
    assertThat(controller.focusedListExtensionId()).isEqualTo(favoriteId);
  }

  @Test
  void favoritesPersistAcrossControllerRestarts() {
    Path favoritesFile = tempDir.resolve("favorites.json");
    ExtensionFavoritesStore favoritesStore = ExtensionFavoritesStore.fileBacked(favoritesFile);

    CoreTuiController firstController = controllerWithFavoritesStore(favoritesStore);
    firstController.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                CoreTuiController.ExtensionCatalogLoadResult.live(
                    List.of(
                        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest", "Web", 20),
                        new ExtensionDto("io.quarkus:quarkus-arc", "CDI", "cdi", "Core", 10)))));
    moveFocusTo(firstController, FocusTarget.EXTENSION_LIST);
    firstController.onEvent(KeyEvent.ofKey(KeyCode.DOWN));
    firstController.onEvent(KeyEvent.ofChar('f'));
    assertThat(firstController.favoriteExtensionCount()).isEqualTo(1);

    CoreTuiController secondController = controllerWithFavoritesStore(favoritesStore);
    secondController.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                CoreTuiController.ExtensionCatalogLoadResult.live(
                    List.of(
                        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest", "Web", 20),
                        new ExtensionDto("io.quarkus:quarkus-arc", "CDI", "cdi", "Core", 10)))));

    assertThat(secondController.favoriteExtensionCount()).isEqualTo(1);
    assertThat(secondController.focusedListExtensionId()).isEqualTo("io.quarkus:quarkus-arc");
  }

  @Test
  void selectingExtensionShowsRecentlySelectedSection() {
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), UiScheduler.immediate(), Duration.ZERO);
    moveFocusTo(controller, FocusTarget.EXTENSION_LIST);
    controller.onEvent(KeyEvent.ofChar(' '));

    String rendered = renderToString(controller);

    assertThat(rendered).contains("Recently Selected");
    assertThat(rendered).contains("CDI");
  }

  @Test
  void recentSelectionsPersistAcrossControllerRestarts() {
    Path favoritesFile = tempDir.resolve("favorites.json");
    ExtensionFavoritesStore favoritesStore = ExtensionFavoritesStore.fileBacked(favoritesFile);

    CoreTuiController firstController = controllerWithFavoritesStore(favoritesStore);
    firstController.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                CoreTuiController.ExtensionCatalogLoadResult.live(
                    List.of(
                        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest", "Web", 20),
                        new ExtensionDto("io.quarkus:quarkus-arc", "CDI", "cdi", "Core", 10)))));
    moveFocusTo(firstController, FocusTarget.EXTENSION_LIST);
    firstController.onEvent(KeyEvent.ofChar(' '));
    assertThat(renderToString(firstController)).contains("Recently Selected");

    CoreTuiController secondController = controllerWithFavoritesStore(favoritesStore);
    secondController.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                CoreTuiController.ExtensionCatalogLoadResult.live(
                    List.of(
                        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest", "Web", 20),
                        new ExtensionDto("io.quarkus:quarkus-arc", "CDI", "cdi", "Core", 10)))));

    assertThat(renderToString(secondController)).contains("Recently Selected");
    assertThat(renderToString(secondController)).contains("CDI");
  }

  private static void moveFocusTo(CoreTuiController controller, FocusTarget target) {
    for (int i = 0; i < 20 && controller.focusTarget() != target; i++) {
      controller.onEvent(KeyEvent.ofKey(KeyCode.TAB));
    }
    assertThat(controller.focusTarget()).isEqualTo(target);
  }

  private static String renderToString(CoreTuiController controller) {
    Buffer buffer = Buffer.empty(new Rect(0, 0, 120, 32));
    Frame frame = Frame.forTesting(buffer);
    controller.render(frame);
    return buffer.toAnsiStringTrimmed();
  }

  private static MetadataDto sampleMetadata() {
    return new MetadataDto(
        List.of("21", "25"),
        List.of("maven", "gradle"),
        Map.of("maven", List.of("21", "25"), "gradle", List.of("25")));
  }

  private static CoreTuiController controllerWithFavoritesStore(
      ExtensionFavoritesStore favoritesStore) {
    return CoreTuiController.from(
        UiTestFixtureFactory.defaultForgeUiState(),
        UiScheduler.immediate(),
        Duration.ZERO,
        (generationRequest, outputDirectory, cancelled, progressListener) ->
            CompletableFuture.failedFuture(new IllegalStateException("not used in this test")),
        favoritesStore,
        Runnable::run);
  }

  private static CoreTuiController.ExtensionCatalogLoadResult withOrder(
      String primaryId, Integer primaryOrder, String secondaryId, Integer secondaryOrder) {
    List<ExtensionDto> extensions =
        List.of(
            new ExtensionDto(primaryId, "REST", "rest", "Web", primaryOrder),
            new ExtensionDto(secondaryId, "CDI", "cdi", "Core", secondaryOrder));
    return CoreTuiController.ExtensionCatalogLoadResult.live(extensions);
  }

  private static final class QueueingScheduler implements UiScheduler {
    private final List<Runnable> queuedTasks = new ArrayList<>();

    @Override
    public Cancellable schedule(Duration delay, Runnable task) {
      queuedTasks.add(task);
      return () -> queuedTasks.remove(task);
    }

    void runAll() {
      List<Runnable> pending = new ArrayList<>(queuedTasks);
      queuedTasks.clear();
      for (Runnable runnable : pending) {
        runnable.run();
      }
    }
  }
}
