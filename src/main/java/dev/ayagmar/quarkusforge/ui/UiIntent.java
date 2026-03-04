package dev.ayagmar.quarkusforge.ui;

sealed interface UiIntent {
  record PostGenerationIntent(PostGenerationTransition transition) implements UiIntent {}

  enum PostGenerationTransition {
    HANDLED,
    QUIT,
    EXPORT_RECIPE,
    GENERATE_AGAIN
  }
}
