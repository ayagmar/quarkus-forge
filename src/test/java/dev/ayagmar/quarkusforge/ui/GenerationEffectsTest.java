package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.api.GenerationRequest;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class GenerationEffectsTest {

  @Test
  void prepareForGenerationResetsTerminalTrackerState() {
    GenerationStateTracker tracker = new GenerationStateTracker();
    tracker.transitionTo(CoreTuiController.GenerationState.VALIDATING);
    tracker.transitionTo(CoreTuiController.GenerationState.LOADING);
    tracker.transitionTo(CoreTuiController.GenerationState.SUCCESS);
    GenerationEffects effects =
        new GenerationEffects(new GenerationFlowCoordinator(), tracker, new ControlledRunner());

    effects.prepareForGeneration();

    assertThat(tracker.currentState()).isEqualTo(CoreTuiController.GenerationState.IDLE);
  }

  @Test
  void startGenerationBuildsRequestFromCurrentSelections() {
    ControlledRunner runner = new ControlledRunner();
    GenerationEffects effects =
        new GenerationEffects(
            new GenerationFlowCoordinator(), new GenerationStateTracker(), runner);
    RecordingCallbacks callbacks = new RecordingCallbacks();
    ProjectRequest request =
        new ProjectRequest(
            "org.acme",
            "demo",
            "1.0.0-SNAPSHOT",
            "org.acme.demo",
            "./output",
            "3.20",
            "maven",
            "21");

    effects.startGeneration(
        request,
        List.of("io.quarkus:quarkus-rest", "io.quarkus:quarkus-smallrye-openapi"),
        Path.of("output/demo"),
        callbacks);
    runner.complete(Path.of("output/demo"));

    assertThat(runner.request)
        .isEqualTo(
            new GenerationRequest(
                "org.acme",
                "demo",
                "1.0.0-SNAPSHOT",
                "3.20",
                "maven",
                "21",
                List.of("io.quarkus:quarkus-rest", "io.quarkus:quarkus-smallrye-openapi")));
    assertThat(runner.outputDirectory).isEqualTo(Path.of("output/demo"));
    assertThat(callbacks.transitions)
        .containsExactly(
            CoreTuiController.GenerationState.LOADING, CoreTuiController.GenerationState.SUCCESS);
    assertThat(callbacks.successPaths).containsExactly(Path.of("output/demo").toAbsolutePath());
  }

  private static final class ControlledRunner implements ProjectGenerationRunner {
    private GenerationRequest request;
    private Path outputDirectory;
    private CompletableFuture<Path> future;

    @Override
    public CompletableFuture<Path> generate(
        GenerationRequest generationRequest,
        Path outputDirectory,
        BooleanSupplier cancelled,
        Consumer<GenerationProgressUpdate> progressListener) {
      request = generationRequest;
      this.outputDirectory = outputDirectory;
      future = new CompletableFuture<>();
      return future;
    }

    void complete(Path path) {
      future.complete(path);
    }
  }

  private static final class RecordingCallbacks implements GenerationFlowCallbacks {
    private final List<CoreTuiController.GenerationState> transitions = new ArrayList<>();
    private final List<Path> successPaths = new ArrayList<>();
    private CoreTuiController.GenerationState currentState = CoreTuiController.GenerationState.IDLE;

    @Override
    public void beforeGenerationStart() {}

    @Override
    public boolean transitionTo(CoreTuiController.GenerationState targetState) {
      currentState = targetState;
      transitions.add(targetState);
      return true;
    }

    @Override
    public CoreTuiController.GenerationState currentState() {
      return currentState;
    }

    @Override
    public String generationStateLabel() {
      return currentState.name();
    }

    @Override
    public void onSubmitIgnored(String stateLabel) {}

    @Override
    public void scheduleOnRenderThread(Runnable task) {
      task.run();
    }

    @Override
    public void onProgress(GenerationProgressUpdate progressUpdate) {}

    @Override
    public void onGenerationSuccess(Path generatedPath) {
      successPaths.add(generatedPath);
    }

    @Override
    public void onGenerationCancelled() {}

    @Override
    public void onGenerationFailed(Throwable cause) {}

    @Override
    public void onCancellationRequested() {}
  }
}
