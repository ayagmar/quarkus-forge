package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.api.ExtensionDto;
import dev.ayagmar.quarkusforge.api.GenerationRequest;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.TickEvent;
import java.nio.file.Path;
import java.time.Duration;
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

    String defaultFooter = UiControllerTestHarness.renderToString(controller, 120, 32);
    assertThat(defaultFooter).contains("Tab/Shift+Tab: focus | Enter/Alt+G: submit | /: search");

    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_LIST);
    String listFooterWide = UiControllerTestHarness.renderToString(controller, 120, 32);
    assertThat(listFooterWide)
        .contains("Up/Down/Home/End or j/k: list nav")
        .contains("PgUp/PgDn: category jump");

    String listFooterNarrow = UiControllerTestHarness.renderToString(controller, 80, 32);
    assertThat(listFooterNarrow).contains("Up/Down or j/k: nav").contains("PgUp/PgDn: category");
  }

  @Test
  void extensionPanelShowsLoadingAndFallbackVisualStates() {
    UiControllerTestHarness.QueueingScheduler scheduler =
        new UiControllerTestHarness.QueueingScheduler();
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), scheduler, Duration.ofMillis(50));
    CompletableFuture<ExtensionCatalogLoadResult> loadFuture = new CompletableFuture<>();

    controller.loadExtensionCatalogAsync(() -> loadFuture);
    assertThat(UiControllerTestHarness.renderToString(controller, 120, 32))
        .contains("Extensions [loading]");
    assertThat(UiControllerTestHarness.renderToString(controller, 120, 32))
        .contains("Loading extension catalog");

    loadFuture.completeExceptionally(new IllegalStateException("network down"));
    scheduler.runAll();

    String rendered = UiControllerTestHarness.renderToString(controller, 120, 32);
    assertThat(rendered).contains("Extensions [fallback]");
    assertThat(rendered).contains("Catalog: snapshot");
    assertThat(rendered).contains("Error: Catalog load failed: network down");
  }

  @Test
  void startupOverlayTracksCatalogLoadingAndReadyStates() {
    UiControllerTestHarness.QueueingScheduler scheduler =
        new UiControllerTestHarness.QueueingScheduler();
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), scheduler, Duration.ofMillis(50));
    CompletableFuture<ExtensionCatalogLoadResult> loadFuture = new CompletableFuture<>();

    controller.loadExtensionCatalogAsync(() -> loadFuture);
    String loading = UiControllerTestHarness.renderToString(controller, 120, 32);
    assertThat(loading).contains("Startup");
    assertThat(loading).contains("____ _   _");
    assertThat(loading).contains("catalog load     : in progress");

    loadFuture.complete(
        ExtensionCatalogLoadResult.live(
            List.of(new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest", "Web", 10))));
    scheduler.runAll();
    String ready = UiControllerTestHarness.renderToString(controller, 120, 32);
    assertThat(ready).doesNotContain("Startup");
    assertThat(ready).contains("Loaded extension catalog from live API");
  }

  @Test
  void startupOverlayAutoHidesWhenReady() {
    UiControllerTestHarness.QueueingScheduler scheduler =
        new UiControllerTestHarness.QueueingScheduler();
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), scheduler, Duration.ofMillis(50));
    CompletableFuture<ExtensionCatalogLoadResult> loadFuture = new CompletableFuture<>();

    controller.loadExtensionCatalogAsync(() -> loadFuture);
    loadFuture.complete(
        ExtensionCatalogLoadResult.live(
            List.of(new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest", "Web", 10))));
    scheduler.runAll();
    assertThat(UiControllerTestHarness.renderToString(controller, 120, 32))
        .doesNotContain("Startup");
  }

  @Test
  void startupOverlayCanBeHeldForMinimumDurationAfterLoadCompletes() {
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), UiScheduler.immediate(), Duration.ZERO);
    controller.setStartupOverlayMinDuration(Duration.ofSeconds(5));

    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                ExtensionCatalogLoadResult.live(
                    List.of(
                        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest", "Web", 10)))));

    String rendered = UiControllerTestHarness.renderToString(controller, 120, 32);
    assertThat(rendered).contains("Startup");
    assertThat(rendered).contains("ready            : ready");
  }

  @Test
  void startupOverlayExpiresWithoutKeyboardInput() throws Exception {
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), UiScheduler.immediate(), Duration.ZERO);
    controller.setStartupOverlayMinDuration(Duration.ofMillis(5));
    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                ExtensionCatalogLoadResult.live(
                    List.of(
                        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest", "Web", 10)))));
    assertThat(UiControllerTestHarness.renderToString(controller, 120, 32)).contains("Startup");

    Thread.sleep(20);
    UiAction tickAction = controller.onEvent(TickEvent.of(1, Duration.ofMillis(40)));
    assertThat(tickAction.handled()).isTrue();
    assertThat(UiControllerTestHarness.renderToString(controller, 120, 32))
        .doesNotContain("Startup");
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

    String rendered = UiControllerTestHarness.renderToString(controller, 70, 32);
    assertThat(rendered).contains("Next:");
    assertThat(rendered).contains("...");
  }

  @Test
  void ctrlETogglesExpandedErrorDetailsInFooter() {
    UiControllerTestHarness.QueueingScheduler scheduler =
        new UiControllerTestHarness.QueueingScheduler();
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), scheduler, Duration.ofMillis(50));
    CompletableFuture<ExtensionCatalogLoadResult> loadFuture = new CompletableFuture<>();
    controller.loadExtensionCatalogAsync(() -> loadFuture);
    loadFuture.completeExceptionally(
        new IllegalStateException(
            "live metadata failed because catalog endpoint did not return expected JSON payload"));
    scheduler.runAll();

    String collapsed = UiControllerTestHarness.renderToString(controller, 90, 36);
    assertThat(collapsed).doesNotContain("Error details:");

    controller.onEvent(KeyEvent.ofChar('e', dev.tamboui.tui.event.KeyModifiers.CTRL));
    String expanded = UiControllerTestHarness.renderToString(controller, 90, 36);
    assertThat(expanded).contains("Error details:");
    assertThat(expanded).contains("did not return");
  }

  private static final class ControlledGenerationRunner implements ProjectGenerationRunner {
    private final CompletableFuture<Path> future = new CompletableFuture<>();

    @Override
    public CompletableFuture<Path> generate(
        GenerationRequest generationRequest,
        Path outputDirectory,
        BooleanSupplier cancelled,
        Consumer<GenerationProgressUpdate> progressListener) {
      return future;
    }

    void complete(Path outputPath) {
      future.complete(outputPath);
    }
  }
}
