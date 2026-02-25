package dev.ayagmar.quarkusforge.ui;

final class GenerationStateTracker {
  private CoreTuiController.GenerationState currentState;
  private double progressRatio;
  private String progressPhase;

  GenerationStateTracker() {
    currentState = CoreTuiController.GenerationState.IDLE;
    progressRatio = 0.0;
    progressPhase = "";
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
      progressPhase = "Preparing request...";
    } else if (targetState == CoreTuiController.GenerationState.SUCCESS) {
      progressRatio = 1.0;
      progressPhase = "Done!";
    } else if (targetState == CoreTuiController.GenerationState.ERROR
        || targetState == CoreTuiController.GenerationState.CANCELLED) {
      progressPhase = "";
    } else if (targetState == CoreTuiController.GenerationState.IDLE) {
      progressRatio = 0.0;
      progressPhase = "";
    }
    return true;
  }

  void updateProgress(String message) {
    progressPhase = message == null ? "" : message;
    if (message != null) {
      String lower = message.toLowerCase();
      if (lower.contains("downloading")) {
        progressRatio = 0.3;
      } else if (lower.contains("extracting")) {
        progressRatio = 0.7;
      } else if (lower.contains("done") || lower.contains("succeed")) {
        progressRatio = 1.0;
      } else {
        progressRatio = Math.min(progressRatio + 0.05, 0.9);
      }
    }
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
}
