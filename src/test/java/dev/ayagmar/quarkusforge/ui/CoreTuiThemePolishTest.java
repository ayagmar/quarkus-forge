package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.api.GenerationRequest;
import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class CoreTuiThemePolishTest {
  @Test
  void footerHintsSwitchByFocusAndViewportWidth() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());

    String defaultFooter = renderToString(controller, 120, 32);
    assertThat(defaultFooter).contains("Tab/Shift+Tab: focus | Enter/Alt+G: submit | /: search");

    moveFocusTo(controller, FocusTarget.EXTENSION_LIST);
    String listFooterWide = renderToString(controller, 120, 32);
    assertThat(listFooterWide)
        .contains("Up/Down/Home/End or j/k: list nav")
        .contains("PgUp/PgDn: category jump");

    String listFooterNarrow = renderToString(controller, 80, 32);
    assertThat(listFooterNarrow).contains("Up/Down or j/k: nav").contains("PgUp/PgDn: category");
  }

  @Test
  void extensionPanelShowsLoadingAndFallbackVisualStates() {
    QueueingScheduler scheduler = new QueueingScheduler();
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), scheduler, Duration.ofMillis(50));
    CompletableFuture<CoreTuiController.ExtensionCatalogLoadResult> loadFuture =
        new CompletableFuture<>();

    controller.loadExtensionCatalogAsync(() -> loadFuture);
    assertThat(renderToString(controller, 120, 32)).contains("Extensions [loading]");
    assertThat(renderToString(controller, 120, 32)).contains("Loading extension catalog");

    loadFuture.completeExceptionally(new IllegalStateException("network down"));
    scheduler.runAll();

    String rendered = renderToString(controller, 120, 32);
    assertThat(rendered).contains("Extensions [fallback]");
    assertThat(rendered).contains("Catalog: snapshot");
    assertThat(rendered).contains("Error: Catalog load failed: network down");
  }

  @Test
  void narrowFooterTruncatesLongNextStepHint() {
    ControlledGenerationRunner generationRunner = new ControlledGenerationRunner();
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(),
            UiScheduler.immediate(),
            Duration.ZERO,
            generationRunner);

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    generationRunner.complete(Path.of("build/generated/very-long-project-path/with/many/segments"));

    String rendered = renderToString(controller, 70, 32);
    assertThat(rendered).contains("Next:");
    assertThat(rendered).contains("...");
  }

  @Test
  void ctrlETogglesExpandedErrorDetailsInFooter() {
    QueueingScheduler scheduler = new QueueingScheduler();
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), scheduler, Duration.ofMillis(50));
    CompletableFuture<CoreTuiController.ExtensionCatalogLoadResult> loadFuture =
        new CompletableFuture<>();
    controller.loadExtensionCatalogAsync(() -> loadFuture);
    loadFuture.completeExceptionally(
        new IllegalStateException(
            "live metadata failed because catalog endpoint did not return expected JSON payload"));
    scheduler.runAll();

    String collapsed = renderToString(controller, 90, 36);
    assertThat(collapsed).doesNotContain("Error details:");

    controller.onEvent(KeyEvent.ofChar('e', dev.tamboui.tui.event.KeyModifiers.CTRL));
    String expanded = renderToString(controller, 90, 36);
    assertThat(expanded).contains("Error details:");
    assertThat(expanded).contains("expected JSON payload");
  }

  private static void moveFocusTo(CoreTuiController controller, FocusTarget target) {
    for (int i = 0; i < 20 && controller.focusTarget() != target; i++) {
      controller.onEvent(KeyEvent.ofKey(KeyCode.TAB));
    }
    assertThat(controller.focusTarget()).isEqualTo(target);
  }

  private static String renderToString(CoreTuiController controller, int width, int height) {
    Buffer buffer = Buffer.empty(new Rect(0, 0, width, height));
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
      for (Runnable pendingTask : pending) {
        pendingTask.run();
      }
    }
  }

  private static final class ControlledGenerationRunner
      implements CoreTuiController.ProjectGenerationRunner {
    private final CompletableFuture<Path> future = new CompletableFuture<>();

    @Override
    public CompletableFuture<Path> generate(
        GenerationRequest generationRequest,
        Path outputDirectory,
        BooleanSupplier cancelled,
        Consumer<String> progressListener) {
      return future;
    }

    void complete(Path outputPath) {
      future.complete(outputPath);
    }
  }
}
