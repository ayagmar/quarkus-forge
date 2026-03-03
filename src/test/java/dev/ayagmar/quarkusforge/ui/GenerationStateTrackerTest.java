package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GenerationStateTrackerTest {

  @Test
  void initialStateIsIdle() {
    GenerationStateTracker tracker = new GenerationStateTracker();

    assertThat(tracker.currentState()).isEqualTo(CoreTuiController.GenerationState.IDLE);
    assertThat(tracker.progressRatio()).isEqualTo(0.0);
    assertThat(tracker.progressPhase()).isEmpty();
    assertThat(tracker.isInProgress()).isFalse();
    assertThat(tracker.stateLabel()).isEqualTo("idle");
    assertThat(tracker.modeLabel()).isEqualTo("ready");
  }

  @Test
  void transitionIdleToValidating() {
    GenerationStateTracker tracker = new GenerationStateTracker();

    boolean result = tracker.transitionTo(CoreTuiController.GenerationState.VALIDATING);

    assertThat(result).isTrue();
    assertThat(tracker.progressRatio()).isEqualTo(0.05);
    assertThat(tracker.progressPhase()).isEqualTo("Validating input...");
    assertThat(tracker.stateLabel()).isEqualTo("validating");
    assertThat(tracker.modeLabel()).isEqualTo("validating input");
  }

  @Test
  void transitionValidatingToLoading() {
    GenerationStateTracker tracker = new GenerationStateTracker();
    tracker.transitionTo(CoreTuiController.GenerationState.VALIDATING);

    boolean result = tracker.transitionTo(CoreTuiController.GenerationState.LOADING);

    assertThat(result).isTrue();
    assertThat(tracker.progressRatio()).isEqualTo(0.1);
    assertThat(tracker.progressPhase()).isEqualTo("Starting generation...");
    assertThat(tracker.isInProgress()).isTrue();
    assertThat(tracker.stateLabel()).isEqualTo("loading (Esc to cancel)");
    assertThat(tracker.modeLabel()).isEqualTo("generation loading");
  }

  @Test
  void transitionLoadingToSuccess() {
    GenerationStateTracker tracker = new GenerationStateTracker();
    tracker.transitionTo(CoreTuiController.GenerationState.VALIDATING);
    tracker.transitionTo(CoreTuiController.GenerationState.LOADING);

    boolean result = tracker.transitionTo(CoreTuiController.GenerationState.SUCCESS);

    assertThat(result).isTrue();
    assertThat(tracker.progressRatio()).isEqualTo(1.0);
    assertThat(tracker.progressPhase()).isEqualTo("Done!");
    assertThat(tracker.stateLabel()).isEqualTo("success");
    assertThat(tracker.modeLabel()).isEqualTo("last run succeeded");
  }

  @Test
  void transitionLoadingToError() {
    GenerationStateTracker tracker = new GenerationStateTracker();
    tracker.transitionTo(CoreTuiController.GenerationState.VALIDATING);
    tracker.transitionTo(CoreTuiController.GenerationState.LOADING);

    boolean result = tracker.transitionTo(CoreTuiController.GenerationState.ERROR);

    assertThat(result).isTrue();
    assertThat(tracker.progressPhase()).isEmpty();
    assertThat(tracker.stateLabel()).isEqualTo("failed");
    assertThat(tracker.modeLabel()).isEqualTo("last run failed");
  }

  @Test
  void transitionLoadingToCancelled() {
    GenerationStateTracker tracker = new GenerationStateTracker();
    tracker.transitionTo(CoreTuiController.GenerationState.VALIDATING);
    tracker.transitionTo(CoreTuiController.GenerationState.LOADING);

    boolean result = tracker.transitionTo(CoreTuiController.GenerationState.CANCELLED);

    assertThat(result).isTrue();
    assertThat(tracker.stateLabel()).isEqualTo("cancelled");
    assertThat(tracker.modeLabel()).isEqualTo("last run cancelled");
  }

  @Test
  void invalidTransitionReturnsFalse() {
    GenerationStateTracker tracker = new GenerationStateTracker();

    boolean result = tracker.transitionTo(CoreTuiController.GenerationState.LOADING);

    assertThat(result).isFalse();
    assertThat(tracker.currentState()).isEqualTo(CoreTuiController.GenerationState.IDLE);
  }

  @Test
  void sameStateTransitionReturnsFalse() {
    GenerationStateTracker tracker = new GenerationStateTracker();

    boolean result = tracker.transitionTo(CoreTuiController.GenerationState.IDLE);

    assertThat(result).isFalse();
  }

  @Test
  void updateProgressSetsStepAndRatio() {
    GenerationStateTracker tracker = new GenerationStateTracker();
    tracker.transitionTo(CoreTuiController.GenerationState.VALIDATING);
    tracker.transitionTo(CoreTuiController.GenerationState.LOADING);

    tracker.updateProgress(GenerationProgressUpdate.extractingArchive("Extracting files"));

    assertThat(tracker.progressRatio()).isEqualTo(0.8);
    assertThat(tracker.progressPhase()).isEqualTo("Extracting files");
  }

  @Test
  void updateProgressWithBlankMessageUsesDefault() {
    GenerationStateTracker tracker = new GenerationStateTracker();
    tracker.transitionTo(CoreTuiController.GenerationState.VALIDATING);
    tracker.transitionTo(CoreTuiController.GenerationState.LOADING);

    tracker.updateProgress(GenerationProgressUpdate.finalizing(""));

    assertThat(tracker.progressRatio()).isEqualTo(0.95);
    assertThat(tracker.progressPhase()).isEqualTo("finalizing generated project...");
  }

  @Test
  void tickUpdatesPhaseForRequestingArchive() {
    GenerationStateTracker tracker = new GenerationStateTracker();
    tracker.transitionTo(CoreTuiController.GenerationState.VALIDATING);
    tracker.transitionTo(CoreTuiController.GenerationState.LOADING);

    tracker.tick(3000L);

    assertThat(tracker.progressPhase()).contains("waiting for Quarkus API response");
    assertThat(tracker.progressPhase()).contains("3s");
    assertThat(tracker.progressRatio()).isGreaterThan(0.1);
  }

  @Test
  void tickDoesNothingWhenNotLoading() {
    GenerationStateTracker tracker = new GenerationStateTracker();
    tracker.tick(5000L);

    assertThat(tracker.progressPhase()).isEmpty();
    assertThat(tracker.progressRatio()).isEqualTo(0.0);
  }

  @Test
  void tickDoesNothingForNonRequestingStep() {
    GenerationStateTracker tracker = new GenerationStateTracker();
    tracker.transitionTo(CoreTuiController.GenerationState.VALIDATING);
    tracker.transitionTo(CoreTuiController.GenerationState.LOADING);
    tracker.updateProgress(GenerationProgressUpdate.extractingArchive("extracting"));

    double ratioBefore = tracker.progressRatio();
    tracker.tick(5000L);

    assertThat(tracker.progressRatio()).isEqualTo(ratioBefore);
  }

  @Test
  void resetAfterTerminalOutcomeResetsSuccessToIdle() {
    GenerationStateTracker tracker = new GenerationStateTracker();
    tracker.transitionTo(CoreTuiController.GenerationState.VALIDATING);
    tracker.transitionTo(CoreTuiController.GenerationState.LOADING);
    tracker.transitionTo(CoreTuiController.GenerationState.SUCCESS);

    tracker.resetAfterTerminalOutcome();

    assertThat(tracker.currentState()).isEqualTo(CoreTuiController.GenerationState.IDLE);
  }

  @Test
  void resetAfterTerminalOutcomeResetsErrorToIdle() {
    GenerationStateTracker tracker = new GenerationStateTracker();
    tracker.transitionTo(CoreTuiController.GenerationState.VALIDATING);
    tracker.transitionTo(CoreTuiController.GenerationState.LOADING);
    tracker.transitionTo(CoreTuiController.GenerationState.ERROR);

    tracker.resetAfterTerminalOutcome();

    assertThat(tracker.currentState()).isEqualTo(CoreTuiController.GenerationState.IDLE);
  }

  @Test
  void resetAfterTerminalOutcomeResetsCancelledToIdle() {
    GenerationStateTracker tracker = new GenerationStateTracker();
    tracker.transitionTo(CoreTuiController.GenerationState.VALIDATING);
    tracker.transitionTo(CoreTuiController.GenerationState.LOADING);
    tracker.transitionTo(CoreTuiController.GenerationState.CANCELLED);

    tracker.resetAfterTerminalOutcome();

    assertThat(tracker.currentState()).isEqualTo(CoreTuiController.GenerationState.IDLE);
  }

  @Test
  void resetAfterTerminalOutcomeDoesNothingForIdle() {
    GenerationStateTracker tracker = new GenerationStateTracker();

    tracker.resetAfterTerminalOutcome();

    assertThat(tracker.currentState()).isEqualTo(CoreTuiController.GenerationState.IDLE);
  }

  @Test
  void validTransitionFromValidatingToError() {
    assertThat(
            GenerationStateTracker.isValidTransition(
                CoreTuiController.GenerationState.VALIDATING,
                CoreTuiController.GenerationState.ERROR))
        .isTrue();
  }

  @Test
  void validTransitionFromValidatingToIdle() {
    assertThat(
            GenerationStateTracker.isValidTransition(
                CoreTuiController.GenerationState.VALIDATING,
                CoreTuiController.GenerationState.IDLE))
        .isTrue();
  }

  @Test
  void invalidTransitionFromErrorToLoading() {
    assertThat(
            GenerationStateTracker.isValidTransition(
                CoreTuiController.GenerationState.ERROR, CoreTuiController.GenerationState.LOADING))
        .isFalse();
  }

  @Test
  void tickProgressRatioIsCapped() {
    GenerationStateTracker tracker = new GenerationStateTracker();
    tracker.transitionTo(CoreTuiController.GenerationState.VALIDATING);
    tracker.transitionTo(CoreTuiController.GenerationState.LOADING);

    // Simulate a very long wait
    tracker.tick(60_000L);

    assertThat(tracker.progressRatio()).isLessThanOrEqualTo(0.74);
  }

  @Test
  void updateProgressWithRequestingArchiveDefaultPhase() {
    GenerationStateTracker tracker = new GenerationStateTracker();
    tracker.transitionTo(CoreTuiController.GenerationState.VALIDATING);
    tracker.transitionTo(CoreTuiController.GenerationState.LOADING);

    tracker.updateProgress(GenerationProgressUpdate.requestingArchive(""));

    assertThat(tracker.progressPhase()).contains("requesting project archive");
    assertThat(tracker.progressRatio()).isEqualTo(0.35);
  }
}
