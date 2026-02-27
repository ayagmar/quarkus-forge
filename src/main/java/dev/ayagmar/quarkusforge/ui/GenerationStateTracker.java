package dev.ayagmar.quarkusforge.ui;

final class GenerationStateTracker {
  private static final String[] WAITING_FRAMES = {".", "..", "...", "...."};

  private CoreTuiController.GenerationState currentState;
  private CoreTuiController.GenerationProgressStep currentStep;
  private double progressRatio;
  private String progressPhase;
  private long waitingTick;

  GenerationStateTracker() {
    currentState = CoreTuiController.GenerationState.IDLE;
    currentStep = CoreTuiController.GenerationProgressStep.REQUESTING_ARCHIVE;
    progressRatio = 0.0;
    progressPhase = "";
    waitingTick = 0L;
  }

  CoreTuiController.GenerationState currentState() {
    return currentState;
  }

  boolean transitionTo(CoreTuiController.GenerationState targetState) {
    if (!isValidTransition(currentState, targetState)) {
      return false;
    }
    currentState = targetState;
    if (targetState == CoreTuiController.GenerationState.VALIDATING) {
      progressRatio = 0.05;
      progressPhase = "Validating input...";
    } else if (targetState == CoreTuiController.GenerationState.LOADING) {
      progressRatio = 0.1;
      progressPhase = "Starting generation...";
      currentStep = CoreTuiController.GenerationProgressStep.REQUESTING_ARCHIVE;
      waitingTick = 0L;
    } else if (targetState == CoreTuiController.GenerationState.SUCCESS) {
      progressRatio = 1.0;
      progressPhase = "Done!";
      currentStep = CoreTuiController.GenerationProgressStep.FINALIZING;
    } else if (targetState == CoreTuiController.GenerationState.ERROR
        || targetState == CoreTuiController.GenerationState.CANCELLED) {
      progressPhase = "";
    } else if (targetState == CoreTuiController.GenerationState.IDLE) {
      currentStep = CoreTuiController.GenerationProgressStep.REQUESTING_ARCHIVE;
      progressRatio = 0.0;
      progressPhase = "";
      waitingTick = 0L;
    }
    return true;
  }

  void updateProgress(CoreTuiController.GenerationProgressUpdate progressUpdate) {
    currentStep = progressUpdate.step();
    waitingTick = 0L;
    progressPhase = progressUpdate.message();
    if (progressPhase.isBlank()) {
      progressPhase = defaultPhaseFor(currentStep);
    }
    progressRatio = baseRatioFor(currentStep);
  }

  void tick(long elapsedMillis) {
    if (currentState != CoreTuiController.GenerationState.LOADING) {
      return;
    }
    if (currentStep != CoreTuiController.GenerationProgressStep.REQUESTING_ARCHIVE) {
      return;
    }
    waitingTick++;
    int frameIndex = (int) Math.floorMod(waitingTick, WAITING_FRAMES.length);
    String frame = WAITING_FRAMES[frameIndex];
    progressPhase = "waiting for Quarkus API response (" + (elapsedMillis / 1000) + "s)" + frame;
    progressRatio = Math.min(0.35 + (elapsedMillis / 1000.0) * 0.015, 0.74);
  }

  double progressRatio() {
    return progressRatio;
  }

  String progressPhase() {
    return progressPhase;
  }

  void resetAfterTerminalOutcome() {
    if (currentState == CoreTuiController.GenerationState.SUCCESS
        || currentState == CoreTuiController.GenerationState.ERROR
        || currentState == CoreTuiController.GenerationState.CANCELLED) {
      transitionTo(CoreTuiController.GenerationState.IDLE);
    }
  }

  boolean isInProgress() {
    return currentState == CoreTuiController.GenerationState.LOADING;
  }

  String stateLabel() {
    return switch (currentState) {
      case IDLE -> "idle";
      case VALIDATING -> "validating";
      case LOADING -> "loading (Esc to cancel)";
      case SUCCESS -> "success";
      case ERROR -> "failed";
      case CANCELLED -> "cancelled";
    };
  }

  String modeLabel() {
    return switch (currentState) {
      case IDLE -> "ready";
      case VALIDATING -> "validating input";
      case LOADING -> "generation loading";
      case SUCCESS -> "last run succeeded";
      case ERROR -> "last run failed";
      case CANCELLED -> "last run cancelled";
    };
  }

  static boolean isValidTransition(
      CoreTuiController.GenerationState currentState,
      CoreTuiController.GenerationState targetState) {
    if (currentState == targetState) {
      return false;
    }
    return switch (currentState) {
      case IDLE -> targetState == CoreTuiController.GenerationState.VALIDATING;
      case VALIDATING ->
          targetState == CoreTuiController.GenerationState.LOADING
              || targetState == CoreTuiController.GenerationState.ERROR
              || targetState == CoreTuiController.GenerationState.IDLE;
      case LOADING ->
          targetState == CoreTuiController.GenerationState.SUCCESS
              || targetState == CoreTuiController.GenerationState.ERROR
              || targetState == CoreTuiController.GenerationState.CANCELLED;
      case SUCCESS, ERROR, CANCELLED -> targetState == CoreTuiController.GenerationState.IDLE;
    };
  }

  private static double baseRatioFor(CoreTuiController.GenerationProgressStep step) {
    return switch (step) {
      case REQUESTING_ARCHIVE -> 0.35;
      case EXTRACTING_ARCHIVE -> 0.8;
      case FINALIZING -> 0.95;
    };
  }

  private static String defaultPhaseFor(CoreTuiController.GenerationProgressStep step) {
    return switch (step) {
      case REQUESTING_ARCHIVE -> "requesting project archive from Quarkus API...";
      case EXTRACTING_ARCHIVE -> "extracting project archive...";
      case FINALIZING -> "finalizing generated project...";
    };
  }
}
