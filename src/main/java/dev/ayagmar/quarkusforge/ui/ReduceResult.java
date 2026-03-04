package dev.ayagmar.quarkusforge.ui;

import java.util.List;

record ReduceResult(UiState nextState, List<UiEffect> effects, UiAction action) {
  ReduceResult {
    effects = List.copyOf(effects);
  }
}
