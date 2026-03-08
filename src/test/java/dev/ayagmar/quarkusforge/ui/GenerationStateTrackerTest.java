package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GenerationStateTrackerTest {

  @Test
  void initialStateIsIdle() {
    GenerationStateTracker tracker = new GenerationStateTracker();

    assertThat(tracker.currentState()).isEqualTo(GenerationState.IDLE);
    assertThat(tracker.progressRatio()).isEqualTo(0.0);
    assertThat(tracker.progressPhase()).isEmpty();
    assertThat(tracker.isInProgress()).isFalse();
    assertThat(tracker.stateLabel()).isEqualTo("idle");
    assertThat(tracker.modeLabel()).isEqualTo("ready");
  }

  @Test
  void transitionIdleToValidating() {
    GenerationStateTracker tracker = new GenerationStateTracker();

    boolean result = tracker.transitionTo(GenerationState.VALIDATING);

    assertThat(result).isTrue();
    assertThat(tracker.progressRatio()).isEqualTo(0.05);
    assertThat(tracker.progressPhase()).isEqualTo("Validating input...");
    assertThat(tracker.stateLabel()).isEqualTo("validating");
    assertThat(tracker.modeLabel()).isEqualTo("validating input");
  }

  @Test
  void transitionValidatingToLoading() {
    GenerationStateTracker tracker = new GenerationStateTracker();
    tracker.transitionTo(GenerationState.VALIDATING);

    boolean result = tracker.transitionTo(GenerationState.LOADING);

    assertThat(result).isTrue();
    assertThat(tracker.progressRatio()).isEqualTo(0.1);
    assertThat(tracker.progressPhase()).isEqualTo("Starting generation...");
    assertThat(tracker.isInProgress()).isTrue();
    assertThat(tracker.stateLabel()).isEqualTo("loading (Esc to cancel)");
    assertThat(tracker.modeLabel()).isEqualTo("generation loading");
  }

  @Test
  void transitionLoadingToSuccess() {
    GenerationStateTracker tracker = trackerInLoadingState();

    boolean result = tracker.transitionTo(GenerationState.SUCCESS);

    assertThat(result).isTrue();
    assertThat(tracker.progressRatio()).isEqualTo(1.0);
    assertThat(tracker.progressPhase()).isEqualTo("Done!");
    assertThat(tracker.stateLabel()).isEqualTo("success");
    assertThat(tracker.modeLabel()).isEqualTo("last run succeeded");
  }

  @Test
  void transitionLoadingToError() {
    GenerationStateTracker tracker = trackerInLoadingState();

    boolean result = tracker.transitionTo(GenerationState.ERROR);

    assertThat(result).isTrue();
    assertThat(tracker.progressPhase()).isEmpty();
    assertThat(tracker.stateLabel()).isEqualTo("failed");
    assertThat(tracker.modeLabel()).isEqualTo("last run failed");
  }

  @Test
  void transitionLoadingToCancelled() {
    GenerationStateTracker tracker = trackerInLoadingState();

    boolean result = tracker.transitionTo(GenerationState.CANCELLED);

    assertThat(result).isTrue();
    assertThat(tracker.stateLabel()).isEqualTo("cancelled");
    assertThat(tracker.modeLabel()).isEqualTo("last run cancelled");
  }

  @Test
  void invalidTransitionReturnsFalse() {
    GenerationStateTracker tracker = new GenerationStateTracker();

    boolean result = tracker.transitionTo(GenerationState.LOADING);

    assertThat(result).isFalse();
    assertThat(tracker.currentState()).isEqualTo(GenerationState.IDLE);
  }

  @Test
  void sameStateTransitionReturnsFalse() {
    GenerationStateTracker tracker = new GenerationStateTracker();

    boolean result = tracker.transitionTo(GenerationState.IDLE);

    assertThat(result).isFalse();
  }

  @Test
  void updateProgressSetsStepAndRatio() {
    GenerationStateTracker tracker = trackerInLoadingState();

    tracker.updateProgress(GenerationProgressUpdate.extractingArchive("Extracting files"));

    assertThat(tracker.progressRatio()).isEqualTo(0.8);
    assertThat(tracker.progressPhase()).isEqualTo("Extracting files");
  }

  @Test
  void updateProgressWithBlankMessageUsesDefault() {
    GenerationStateTracker tracker = trackerInLoadingState();

    tracker.updateProgress(GenerationProgressUpdate.finalizing(""));

    assertThat(tracker.progressRatio()).isEqualTo(0.95);
    assertThat(tracker.progressPhase()).isEqualTo("finalizing generated project...");
  }

  @Test
  void tickUpdatesPhaseForRequestingArchive() {
    GenerationStateTracker tracker = trackerInLoadingState();

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
    GenerationStateTracker tracker = trackerInLoadingState();
    tracker.updateProgress(GenerationProgressUpdate.extractingArchive("extracting"));

    double ratioBefore = tracker.progressRatio();
    tracker.tick(5000L);

    assertThat(tracker.progressRatio()).isEqualTo(ratioBefore);
  }

  @Test
  void resetAfterTerminalOutcomeResetsSuccessToIdle() {
    GenerationStateTracker tracker = trackerInLoadingState();
    tracker.transitionTo(GenerationState.SUCCESS);

    tracker.resetAfterTerminalOutcome();

    assertThat(tracker.currentState()).isEqualTo(GenerationState.IDLE);
  }

  @Test
  void resetAfterTerminalOutcomeResetsErrorToIdle() {
    GenerationStateTracker tracker = trackerInLoadingState();
    tracker.transitionTo(GenerationState.ERROR);

    tracker.resetAfterTerminalOutcome();

    assertThat(tracker.currentState()).isEqualTo(GenerationState.IDLE);
  }

  @Test
  void resetAfterTerminalOutcomeResetsCancelledToIdle() {
    GenerationStateTracker tracker = trackerInLoadingState();
    tracker.transitionTo(GenerationState.CANCELLED);

    tracker.resetAfterTerminalOutcome();

    assertThat(tracker.currentState()).isEqualTo(GenerationState.IDLE);
  }

  @Test
  void resetAfterTerminalOutcomeDoesNothingForIdle() {
    GenerationStateTracker tracker = new GenerationStateTracker();

    tracker.resetAfterTerminalOutcome();

    assertThat(tracker.currentState()).isEqualTo(GenerationState.IDLE);
  }

  @Test
  void validTransitionFromValidatingToError() {
    assertThat(
            GenerationStateMachine.isValidTransition(
                GenerationState.VALIDATING, GenerationState.ERROR))
        .isTrue();
  }

  @Test
  void validTransitionFromValidatingToIdle() {
    assertThat(
            GenerationStateMachine.isValidTransition(
                GenerationState.VALIDATING, GenerationState.IDLE))
        .isTrue();
  }

  @Test
  void invalidTransitionFromErrorToLoading() {
    assertThat(
            GenerationStateMachine.isValidTransition(
                GenerationState.ERROR, GenerationState.LOADING))
        .isFalse();
  }

  @Test
  void tickProgressRatioIsCapped() {
    GenerationStateTracker tracker = trackerInLoadingState();

    // Simulate a very long wait
    tracker.tick(60_000L);

    assertThat(tracker.progressRatio()).isLessThanOrEqualTo(0.74);
  }

  @Test
  void updateProgressWithRequestingArchiveDefaultPhase() {
    GenerationStateTracker tracker = trackerInLoadingState();

    tracker.updateProgress(GenerationProgressUpdate.requestingArchive(""));

    assertThat(tracker.progressPhase()).contains("requesting project archive");
    assertThat(tracker.progressRatio()).isEqualTo(0.35);
  }

  private static GenerationStateTracker trackerInLoadingState() {
    GenerationStateTracker tracker = new GenerationStateTracker();
    tracker.transitionTo(GenerationState.VALIDATING);
    tracker.transitionTo(GenerationState.LOADING);
    return tracker;
  }
}
