package dev.ayagmar.quarkusforge.ui;

import java.util.List;

final class CoreUiReducer implements UiReducer {

  @Override
  public ReduceResult reduce(UiState state, UiIntent intent) {
    if (intent instanceof UiIntent.PostGenerationIntent postGenerationIntent) {
      return reducePostGeneration(state, postGenerationIntent.transition());
    }
    return new ReduceResult(state, List.of(), UiAction.ignored());
  }

  private static ReduceResult reducePostGeneration(
      UiState state, UiIntent.PostGenerationTransition transition) {
    return switch (transition) {
      case HANDLED -> new ReduceResult(state, List.of(), UiAction.handled(false));
      case QUIT ->
          new ReduceResult(
              state, List.of(new UiEffect.CancelPendingAsync()), UiAction.handled(true));
      case EXPORT_RECIPE ->
          new ReduceResult(
              state, List.of(new UiEffect.ExportRecipeAndLock()), UiAction.handled(false));
      case GENERATE_AGAIN ->
          new ReduceResult(
              state.withStatusAndError("Ready for next generation", ""),
              List.of(new UiEffect.ResetGenerationAfterOutcome()),
              UiAction.handled(false));
    };
  }
}
