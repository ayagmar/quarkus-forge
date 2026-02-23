package dev.ayagmar.quarkusforge.ui;

final class GenerationStateTracker {
  private CoreTuiController.GenerationState currentState;

  GenerationStateTracker() {
    currentState = CoreTuiController.GenerationState.IDLE;
  }

  CoreTuiController.GenerationState currentState() {
    return currentState;
  }

  boolean transitionTo(CoreTuiController.GenerationState targetState) {
    if (!isValidTransition(currentState, targetState)) {
      return false;
    }
    currentState = targetState;
    return true;
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
