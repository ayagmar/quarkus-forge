package dev.ayagmar.quarkusforge.ui;

/** Pure state transition contract: current state + intent -> next state + effects + action. */
interface UiReducer {
  ReduceResult reduce(UiState state, UiIntent intent);
}
