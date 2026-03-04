package dev.ayagmar.quarkusforge.ui;

import java.util.List;

final class CoreUiReducer implements UiReducer {

  @Override
  public ReduceResult reduce(UiState state, UiIntent intent) {
    if (intent instanceof UiIntent.PostGenerationIntent postGenerationIntent) {
      return reducePostGeneration(state, postGenerationIntent.transition());
    }
    if (intent instanceof UiIntent.SubmitReadyIntent) {
      return new ReduceResult(
          state, List.of(new UiEffect.StartGeneration()), UiAction.handled(false));
    }
    if (intent instanceof UiIntent.CancelGenerationIntent) {
      return new ReduceResult(
          state, List.of(new UiEffect.RequestGenerationCancellation()), UiAction.handled(false));
    }
    if (intent instanceof UiIntent.GenerationProgressIntent progressIntent) {
      GenerationProgressUpdate progressUpdate = progressIntent.progressUpdate();
      String progressMessage =
          progressUpdate.message().isBlank() ? "working..." : progressUpdate.message();
      return new ReduceResult(
          state.withFeedback("Generation in progress: " + progressMessage, "", ""),
          List.of(),
          UiAction.handled(false));
    }
    if (intent instanceof UiIntent.GenerationSuccessIntent successIntent) {
      return new ReduceResult(
          state.withFeedback("Generation succeeded: " + successIntent.generatedPath(), "", ""),
          List.of(
              new UiEffect.ShowPostGenerationSuccess(
                  successIntent.generatedPath(), successIntent.nextCommand()),
              new UiEffect.RequestAsyncRepaint()),
          UiAction.handled(false));
    }
    if (intent instanceof UiIntent.GenerationCancelledIntent) {
      return new ReduceResult(
          state.withFeedback(
              "Generation cancelled. Update inputs and press Enter to retry.", "", ""),
          List.of(new UiEffect.HidePostGenerationMenu(), new UiEffect.RequestAsyncRepaint()),
          UiAction.handled(false));
    }
    if (intent instanceof UiIntent.GenerationFailedIntent failedIntent) {
      return new ReduceResult(
          state.withFeedback(
              "Generation failed.",
              failedIntent.userErrorMessage(),
              failedIntent.verboseErrorDetails()),
          List.of(new UiEffect.HidePostGenerationMenu(), new UiEffect.RequestAsyncRepaint()),
          UiAction.handled(false));
    }
    if (intent instanceof UiIntent.GenerationCancellationRequestedIntent) {
      return new ReduceResult(
          state.withStatusAndError("Cancellation requested. Waiting for cleanup...", ""),
          List.of(),
          UiAction.handled(false));
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
