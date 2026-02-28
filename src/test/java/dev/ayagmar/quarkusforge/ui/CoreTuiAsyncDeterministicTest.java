package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class CoreTuiAsyncDeterministicTest {
  @Test
  void debounceAppliesOnlyAfterVirtualTimeAdvance() {
    UiControllerTestHarness.ManualUiScheduler scheduler =
        new UiControllerTestHarness.ManualUiScheduler(false);
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), scheduler, Duration.ofMillis(200));
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_SEARCH);

    controller.onEvent(KeyEvent.ofChar('j'));
    controller.onEvent(KeyEvent.ofChar('d'));
    controller.onEvent(KeyEvent.ofChar('b'));
    controller.onEvent(KeyEvent.ofChar('c'));

    assertThat(controller.filteredExtensionCount()).isEqualTo(7);
    scheduler.advanceBy(Duration.ofMillis(199));
    assertThat(controller.filteredExtensionCount()).isEqualTo(7);

    scheduler.advanceBy(Duration.ofMillis(1));
    assertThat(controller.filteredExtensionCount()).isEqualTo(1);
    assertThat(controller.firstFilteredExtensionId()).contains("jdbc-postgresql");
  }

  @Test
  void staleResultsNeverOverwriteLatestQueryWhenCancelIsIneffective() {
    UiControllerTestHarness.ManualUiScheduler scheduler =
        new UiControllerTestHarness.ManualUiScheduler(true);
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), scheduler, Duration.ofMillis(150));
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_SEARCH);

    for (char character : "rest".toCharArray()) {
      controller.onEvent(KeyEvent.ofChar(character));
    }
    for (int i = 0; i < 4; i++) {
      controller.onEvent(KeyEvent.ofKey(KeyCode.BACKSPACE));
    }
    for (char character : "jdbc".toCharArray()) {
      controller.onEvent(KeyEvent.ofChar(character));
    }

    scheduler.advanceBy(Duration.ofMillis(150));
    assertThat(controller.filteredExtensionCount()).isEqualTo(1);
    assertThat(controller.firstFilteredExtensionId()).contains("jdbc-postgresql");
  }

  @Test
  void escapeClearPreventsPendingDebounceTaskFromApplying() {
    UiControllerTestHarness.ManualUiScheduler scheduler =
        new UiControllerTestHarness.ManualUiScheduler(false);
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), scheduler, Duration.ofMillis(120));
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_SEARCH);

    for (char character : "jdbc".toCharArray()) {
      controller.onEvent(KeyEvent.ofChar(character));
    }
    UiAction clearAction = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
    assertThat(clearAction.handled()).isTrue();
    assertThat(clearAction.shouldQuit()).isFalse();

    scheduler.advanceBy(Duration.ofMillis(500));
    assertThat(controller.filteredExtensionCount()).isEqualTo(7);
  }

  @Test
  void pendingSearchRefreshDoesNotOverrideGenerationStatus() {
    UiControllerTestHarness.ManualUiScheduler scheduler =
        new UiControllerTestHarness.ManualUiScheduler(false);
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(),
            scheduler,
            Duration.ofMillis(120),
            (generationRequest, outputDirectory, cancelled, progressListener) -> {
              progressListener.accept(
                  GenerationProgressUpdate.requestingArchive(
                      "requesting project archive from Quarkus API..."));
              return new CompletableFuture<>();
            });
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_SEARCH);

    for (char character : "jdbc".toCharArray()) {
      controller.onEvent(KeyEvent.ofChar(character));
    }

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    assertThat(controller.statusMessage()).contains("Generation in progress");

    scheduler.advanceBy(Duration.ofMillis(120));
    assertThat(controller.statusMessage()).contains("Generation in progress");
  }

  @Test
  void escapeClearsSearchImmediatelyEvenWhenDebounceIsConfigured() {
    UiControllerTestHarness.ManualUiScheduler scheduler =
        new UiControllerTestHarness.ManualUiScheduler(false);
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), scheduler, Duration.ofMillis(200));
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_SEARCH);

    for (char character : "jdbc".toCharArray()) {
      controller.onEvent(KeyEvent.ofChar(character));
    }
    scheduler.advanceBy(Duration.ofMillis(200));
    assertThat(controller.filteredExtensionCount()).isEqualTo(1);

    UiAction escape = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
    assertThat(escape.handled()).isTrue();
    assertThat(escape.shouldQuit()).isFalse();
    assertThat(controller.filteredExtensionCount()).isEqualTo(7);
    assertThat(controller.statusMessage()).contains("Extension search cleared");
  }
}
