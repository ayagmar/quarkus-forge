package dev.ayagmar.quarkusforge.ui;

final class GenerationStateMachine {
  private GenerationStateMachine() {}

  static boolean isValidTransition(GenerationState currentState, GenerationState targetState) {
    if (currentState == targetState) {
      return false;
    }
    return switch (currentState) {
      case IDLE -> targetState == GenerationState.VALIDATING;
      case VALIDATING ->
          targetState == GenerationState.LOADING
              || targetState == GenerationState.ERROR
              || targetState == GenerationState.IDLE;
      case LOADING ->
          targetState == GenerationState.SUCCESS
              || targetState == GenerationState.ERROR
              || targetState == GenerationState.CANCELLED;
      case SUCCESS, ERROR, CANCELLED -> targetState == GenerationState.IDLE;
    };
  }
}
