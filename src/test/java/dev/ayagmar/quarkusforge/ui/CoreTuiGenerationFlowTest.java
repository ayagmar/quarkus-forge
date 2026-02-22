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
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class CoreTuiGenerationFlowTest {
  @Test
  void successfulGenerationShowsNextStepHintAndLocksInteractiveInputs() {
    ControlledGenerationRunner generationRunner = new ControlledGenerationRunner();
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(),
            UiScheduler.immediate(),
            Duration.ZERO,
            generationRunner);

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));

    assertThat(generationRunner.callCount()).isEqualTo(1);
    assertThat(generationRunner.lastOutputDirectory())
        .isEqualTo(Path.of("./generated").resolve("forge-app"));
    assertThat(controller.statusMessage()).contains("Generation in progress");

    controller.onEvent(KeyEvent.ofKey(KeyCode.TAB));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.GROUP_ID);

    generationRunner.complete(Path.of("build/generated-project"));

    assertThat(controller.statusMessage()).contains("Generation succeeded");
    assertThat(renderToString(controller)).contains("Next: cd");
    assertThat(renderToString(controller)).contains("mvn quarkus:dev");
  }

  @Test
  void duplicateSubmitIsIgnoredWhileGenerationIsRunning() {
    ControlledGenerationRunner generationRunner = new ControlledGenerationRunner();
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(),
            UiScheduler.immediate(),
            Duration.ZERO,
            generationRunner);

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));

    assertThat(generationRunner.callCount()).isEqualTo(1);
    assertThat(controller.statusMessage()).contains("already in progress");
  }

  @Test
  void generationLockConsumesKeysWithoutTriggeringQuit() {
    ControlledGenerationRunner generationRunner = new ControlledGenerationRunner();
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(),
            UiScheduler.immediate(),
            Duration.ZERO,
            generationRunner);

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));

    CoreTuiController.UiAction tabAction = controller.onEvent(KeyEvent.ofKey(KeyCode.TAB));
    CoreTuiController.UiAction enterAction = controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));

    assertThat(tabAction.handled()).isTrue();
    assertThat(tabAction.shouldQuit()).isFalse();
    assertThat(enterAction.handled()).isTrue();
    assertThat(enterAction.shouldQuit()).isFalse();
  }

  @Test
  void escapeCancelsActiveGenerationWithoutImmediateQuit() {
    ControlledGenerationRunner generationRunner = new ControlledGenerationRunner();
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(),
            UiScheduler.immediate(),
            Duration.ZERO,
            generationRunner);

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    CoreTuiController.UiAction cancelAction = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));

    assertThat(cancelAction.shouldQuit()).isFalse();
    assertThat(controller.statusMessage())
        .containsAnyOf("Cancellation requested", "Generation cancelled");

    generationRunner.fail(new CancellationException("cancelled"));
    assertThat(controller.statusMessage()).contains("Generation cancelled");

    CoreTuiController.UiAction quitAction = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
    assertThat(quitAction.shouldQuit()).isTrue();
  }

  @Test
  void successfulGradleGenerationShowsGradleNextStepHint() {
    ControlledGenerationRunner generationRunner = new ControlledGenerationRunner();
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState("gradle"),
            UiScheduler.immediate(),
            Duration.ZERO,
            generationRunner);

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    generationRunner.complete(Path.of("build/generated-project"));

    assertThat(renderToString(controller)).contains("./gradlew quarkusDev");
    assertThat(renderToString(controller)).doesNotContain("mvn quarkus:dev");
  }

  @Test
  void successfulGradleKotlinDslGenerationShowsGradleNextStepHint() {
    ControlledGenerationRunner generationRunner = new ControlledGenerationRunner();
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState("gradle-kotlin-dsl"),
            UiScheduler.immediate(),
            Duration.ZERO,
            generationRunner);

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    generationRunner.complete(Path.of("build/generated-project"));

    assertThat(renderToString(controller)).contains("./gradlew quarkusDev");
    assertThat(renderToString(controller)).doesNotContain("mvn quarkus:dev");
  }

  @Test
  void generationFailureShowsErrorAndReleasesUiLock() {
    ControlledGenerationRunner generationRunner = new ControlledGenerationRunner();
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(),
            UiScheduler.immediate(),
            Duration.ZERO,
            generationRunner);

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    generationRunner.fail(new RuntimeException("download failed"));

    assertThat(controller.statusMessage()).contains("Generation failed");
    assertThat(renderToString(controller)).contains("Error: download failed");

    controller.onEvent(KeyEvent.ofKey(KeyCode.TAB));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.ARTIFACT_ID);
  }

  @Test
  void synchronousGenerationRunnerFailureIsSurfacedAsUiError() {
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(),
            UiScheduler.immediate(),
            Duration.ZERO,
            (generationRequest, outputDirectory, cancelled, progressListener) -> {
              throw new IllegalStateException("runner crashed");
            });

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));

    assertThat(controller.generationState()).isEqualTo(CoreTuiController.GenerationState.ERROR);
    assertThat(controller.statusMessage()).contains("Generation failed");
    assertThat(renderToString(controller)).contains("Error: runner crashed");
  }

  @Test
  void nullGenerationFutureIsSurfacedAsUiError() {
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(),
            UiScheduler.immediate(),
            Duration.ZERO,
            (generationRequest, outputDirectory, cancelled, progressListener) -> null);

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));

    assertThat(controller.generationState()).isEqualTo(CoreTuiController.GenerationState.ERROR);
    assertThat(controller.statusMessage()).contains("Generation failed");
    assertThat(renderToString(controller))
        .contains("Error: Generation service returned null future");
  }

  @Test
  void completionStillAppliesWhenSchedulerDropsAsyncCallback() {
    ControlledGenerationRunner generationRunner = new ControlledGenerationRunner();
    UiScheduler droppingScheduler = (delay, task) -> () -> false;
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(),
            droppingScheduler,
            Duration.ZERO,
            generationRunner);

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    generationRunner.complete(Path.of("build/generated-project"));

    assertThat(controller.generationState()).isEqualTo(CoreTuiController.GenerationState.LOADING);

    renderToString(controller);

    assertThat(controller.generationState()).isEqualTo(CoreTuiController.GenerationState.SUCCESS);
    assertThat(controller.statusMessage()).contains("Generation succeeded");
  }

  private static String renderToString(CoreTuiController controller) {
    Buffer buffer = Buffer.empty(new Rect(0, 0, 120, 34));
    Frame frame = Frame.forTesting(buffer);
    controller.render(frame);
    return buffer.toAnsiStringTrimmed();
  }

  private static final class ControlledGenerationRunner
      implements CoreTuiController.ProjectGenerationRunner {
    private int callCount;
    private Path lastOutputDirectory;
    private CompletableFuture<Path> future;

    ControlledGenerationRunner() {
      callCount = 0;
      lastOutputDirectory = null;
      future = new CompletableFuture<>();
    }

    @Override
    public CompletableFuture<Path> generate(
        GenerationRequest generationRequest,
        Path outputDirectory,
        BooleanSupplier cancelled,
        Consumer<String> progressListener) {
      callCount++;
      lastOutputDirectory = outputDirectory;
      progressListener.accept("downloading project archive...");
      return future;
    }

    int callCount() {
      return callCount;
    }

    Path lastOutputDirectory() {
      return lastOutputDirectory;
    }

    void complete(Path outputPath) {
      future.complete(outputPath);
    }

    void fail(Throwable throwable) {
      future.completeExceptionally(throwable);
    }
  }
}
