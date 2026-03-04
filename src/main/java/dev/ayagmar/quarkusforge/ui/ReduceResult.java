package dev.ayagmar.quarkusforge.ui;

import java.util.List;

/** Reducer output bundle with next immutable state, effects to run, and immediate UI action. */
record ReduceResult(UiState nextState, List<UiEffect> effects, UiAction action) {
  ReduceResult {
    effects = List.copyOf(effects);
  }
}
