package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class GenerationStateMachineTest {
  private static final List<Transition> ALLOWED_TRANSITIONS =
      List.of(
          new Transition(GenerationState.IDLE, GenerationState.VALIDATING),
          new Transition(GenerationState.VALIDATING, GenerationState.LOADING),
          new Transition(GenerationState.VALIDATING, GenerationState.ERROR),
          new Transition(GenerationState.VALIDATING, GenerationState.IDLE),
          new Transition(GenerationState.LOADING, GenerationState.SUCCESS),
          new Transition(GenerationState.LOADING, GenerationState.ERROR),
          new Transition(GenerationState.LOADING, GenerationState.CANCELLED),
          new Transition(GenerationState.SUCCESS, GenerationState.IDLE),
          new Transition(GenerationState.ERROR, GenerationState.IDLE),
          new Transition(GenerationState.CANCELLED, GenerationState.IDLE));

  @Test
  void allowsExpectedTransitions() {
    for (Transition transition : ALLOWED_TRANSITIONS) {
      assertThat(
              GenerationStateMachine.isValidTransition(
                  transition.currentState(), transition.targetState()))
          .as("%s -> %s", transition.currentState(), transition.targetState())
          .isTrue();
    }
  }

  @Test
  void rejectsAllOtherTransitions() {
    for (GenerationState currentState : GenerationState.values()) {
      for (GenerationState targetState : GenerationState.values()) {
        Transition transition = new Transition(currentState, targetState);
        if (ALLOWED_TRANSITIONS.contains(transition)) {
          continue;
        }
        assertThat(GenerationStateMachine.isValidTransition(currentState, targetState))
            .as("%s -> %s", currentState, targetState)
            .isFalse();
      }
    }
  }

  @Test
  void rejectsNullStatesExplicitly() {
    assertThatThrownBy(() -> GenerationStateMachine.isValidTransition(null, GenerationState.IDLE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be null");
    assertThatThrownBy(() -> GenerationStateMachine.isValidTransition(GenerationState.IDLE, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be null");
  }

  private record Transition(GenerationState currentState, GenerationState targetState) {}
}
