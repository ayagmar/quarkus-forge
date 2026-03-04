package dev.ayagmar.quarkusforge.ui;

interface UiReducer {
  ReduceResult reduce(UiState state, UiIntent intent);
}
