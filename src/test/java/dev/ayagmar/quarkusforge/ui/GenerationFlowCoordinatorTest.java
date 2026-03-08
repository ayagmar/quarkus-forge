package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.api.GenerationRequest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class GenerationFlowCoordinatorTest {
  @Test
  void startFlowTransitionsToSuccessOnCompletedFuture() {
    GenerationFlowCoordinator coordinator = new GenerationFlowCoordinator();
    TestCallbacks callbacks = new TestCallbacks();
    callbacks.transitionAllowed = true;
    ControlledRunner runner = new ControlledRunner();

    coordinator.startFlow(runner, request(), Path.of("output/demo"), callbacks);
    runner.complete(Path.of("./output/../output/demo"));

    assertThat(callbacks.transitions)
        .containsExactly(GenerationState.LOADING, GenerationState.SUCCESS);
    assertThat(callbacks.successPaths).containsExactly(Path.of("output/demo").toAbsolutePath());
    assertThat(callbacks.failedCauses).isEmpty();
    assertThat(callbacks.cancelledCount).isZero();
  }

  @Test
  void cancellationRequestWinsDuringCompletionRace() {
    GenerationFlowCoordinator coordinator = new GenerationFlowCoordinator();
    TestCallbacks callbacks = new TestCallbacks();
    callbacks.transitionAllowed = true;
    ControlledRunner runner = new ControlledRunner();

    coordinator.startFlow(runner, request(), Path.of("output/demo"), callbacks);
    coordinator.requestCancellation(callbacks);
    runner.fail(new RuntimeException("late failure"));

    assertThat(callbacks.transitions)
        .containsExactly(GenerationState.LOADING, GenerationState.CANCELLED);
    assertThat(callbacks.cancelledCount).isEqualTo(1);
    assertThat(callbacks.failedCauses).isEmpty();
  }

  @Test
  void staleCompletionFromOlderFlowIsIgnored() {
    GenerationFlowCoordinator coordinator = new GenerationFlowCoordinator();
    TestCallbacks callbacks = new TestCallbacks();
    callbacks.transitionAllowed = true;
    ControlledRunner firstRunner = new ControlledRunner();
    ControlledRunner secondRunner = new ControlledRunner();

    coordinator.startFlow(firstRunner, request(), Path.of("output/first"), callbacks);
    coordinator.startFlow(secondRunner, request(), Path.of("output/second"), callbacks);

    firstRunner.complete(Path.of("output/first"));
    secondRunner.complete(Path.of("output/second"));

    assertThat(callbacks.successPaths).hasSize(1);
    assertThat(callbacks.successPaths.getFirst())
        .isEqualTo(Path.of("output/second").toAbsolutePath());
  }

  @Test
  void failedTransitionLeavesFlowIdle() {
    GenerationFlowCoordinator coordinator = new GenerationFlowCoordinator();
    TestCallbacks callbacks = new TestCallbacks();
    callbacks.transitionAllowed = false;
    ControlledRunner runner = new ControlledRunner();

    coordinator.startFlow(runner, request(), Path.of("output/demo"), callbacks);

    assertThat(callbacks.submitIgnoredStates).containsExactly("IDLE");
    assertThat(runner.callCount).isZero();
  }

  @Test
  void reconcileCompletionAppliesDoneFutureEvenWhenSchedulerCallbackWasDropped() {
    GenerationFlowCoordinator coordinator = new GenerationFlowCoordinator();
    TestCallbacks callbacks = new TestCallbacks();
    callbacks.transitionAllowed = true;
    callbacks.dropScheduledTasks = true;
    ControlledRunner runner = new ControlledRunner();

    coordinator.startFlow(runner, request(), Path.of("output/demo"), callbacks);
    runner.complete(Path.of("output/demo"));
    assertThat(callbacks.successPaths).isEmpty();

    coordinator.reconcileCompletionIfDone(callbacks);

    assertThat(callbacks.transitions)
        .containsExactly(GenerationState.LOADING, GenerationState.SUCCESS);
    assertThat(callbacks.successPaths).containsExactly(Path.of("output/demo").toAbsolutePath());
  }

  private static GenerationRequest request() {
    return new GenerationRequest(
        "org.acme", "demo", "1.0.0-SNAPSHOT", "", "maven", "21", List.of());
  }

  private static final class ControlledRunner implements ProjectGenerationRunner {
    private CompletableFuture<Path> future;
    private int callCount;

    @Override
    public CompletableFuture<Path> generate(
        GenerationRequest generationRequest,
        Path outputDirectory,
        BooleanSupplier cancelled,
        Consumer<GenerationProgressUpdate> progressListener) {
      callCount++;
      future = new CompletableFuture<>();
      return future;
    }

    void complete(Path path) {
      future.complete(path);
    }

    void fail(Throwable throwable) {
      future.completeExceptionally(throwable);
    }
  }

  private static final class TestCallbacks implements GenerationFlowCallbacks {
    private final List<GenerationState> transitions = new ArrayList<>();
    private final List<Path> successPaths = new ArrayList<>();
    private final List<Throwable> failedCauses = new ArrayList<>();
    private final List<String> submitIgnoredStates = new ArrayList<>();
    private GenerationState currentState = GenerationState.IDLE;
    private boolean transitionAllowed;
    private boolean dropScheduledTasks;
    private int cancelledCount;

    @Override
    public void beforeGenerationStart() {}

    @Override
    public boolean transitionTo(GenerationState targetState) {
      if (!transitionAllowed && targetState == GenerationState.LOADING) {
        return false;
      }
      currentState = targetState;
      transitions.add(targetState);
      return true;
    }

    @Override
    public GenerationState currentState() {
      return currentState;
    }

    @Override
    public String generationStateLabel() {
      return currentState.name();
    }

    @Override
    public void onSubmitIgnored(String stateLabel) {
      submitIgnoredStates.add(stateLabel);
    }

    @Override
    public void scheduleOnRenderThread(Runnable task) {
      if (!dropScheduledTasks) {
        task.run();
      }
    }

    @Override
    public void onProgress(GenerationProgressUpdate progressUpdate) {}

    @Override
    public void onGenerationSuccess(Path generatedPath) {
      successPaths.add(generatedPath);
    }

    @Override
    public void onGenerationCancelled() {
      cancelledCount++;
    }

    @Override
    public void onGenerationFailed(Throwable cause) {
      failedCauses.add(cause);
    }

    @Override
    public void onCancellationRequested() {}
  }
}
