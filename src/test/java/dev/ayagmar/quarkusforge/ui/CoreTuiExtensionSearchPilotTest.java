package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.api.ExtensionDto;
import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class CoreTuiExtensionSearchPilotTest {
  @Test
  void apiLoadedCatalogIsIndexedAndSearchable() {
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), UiScheduler.immediate(), Duration.ZERO);
    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                List.of(
                    new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest"),
                    new ExtensionDto("io.quarkus:quarkus-smallrye-openapi", "OpenAPI", "openapi"),
                    new ExtensionDto(
                        "io.quarkus:quarkus-jdbc-postgresql",
                        "JDBC PostgreSQL",
                        "jdbc-postgresql"))));

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
                List.of(
                    new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest"),
                    new ExtensionDto(
                        "io.quarkus:quarkus-rest-client", "REST Client", "rest-client"),
                    new ExtensionDto(
                        "io.quarkus:quarkus-jdbc-postgresql",
                        "JDBC PostgreSQL",
                        "jdbc-postgresql"))));

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
  void emptyCatalogLoadKeepsFallbackCatalogAndShowsError() {
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), UiScheduler.immediate(), Duration.ZERO);

    controller.loadExtensionCatalogAsync(() -> CompletableFuture.completedFuture(List.of()));

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
                List.of(
                    new ExtensionDto(
                        "io.quarkus:quarkus-jdbc-postgresql",
                        "JDBC PostgreSQL",
                        "jdbc-postgresql"))));

    assertThat(controller.selectedExtensionIds()).isEmpty();
  }

  @Test
  void asyncCatalogCompletionIsIgnoredAfterQuit() {
    QueueingScheduler scheduler = new QueueingScheduler();
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), scheduler, Duration.ZERO);
    CompletableFuture<List<ExtensionDto>> loadFuture = new CompletableFuture<>();
    controller.loadExtensionCatalogAsync(() -> loadFuture);
    assertThat(controller.statusMessage()).isEqualTo("Loading extension catalog...");

    CoreTuiController.UiAction quitAction = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
    assertThat(quitAction.shouldQuit()).isTrue();

    loadFuture.complete(List.of(new ExtensionDto("io.quarkus:quarkus-funqy", "Funqy", "funqy")));
    scheduler.runAll();

    assertThat(controller.statusMessage()).isEqualTo("Loading extension catalog...");
    assertThat(controller.filteredExtensionCount()).isEqualTo(7);
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
