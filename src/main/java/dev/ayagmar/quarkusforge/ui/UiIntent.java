package dev.ayagmar.quarkusforge.ui;

sealed interface UiIntent {
  record PostGenerationIntent(PostGenerationTransition transition) implements UiIntent {}

  record SubmitReadyIntent() implements UiIntent {}

  record CancelGenerationIntent() implements UiIntent {}

  record GenerationProgressIntent(GenerationProgressUpdate progressUpdate) implements UiIntent {}

  record GenerationSuccessIntent(java.nio.file.Path generatedPath, String nextCommand)
      implements UiIntent {}

  record GenerationCancelledIntent() implements UiIntent {}

  record GenerationFailedIntent(String userErrorMessage, String verboseErrorDetails)
      implements UiIntent {}

  record GenerationCancellationRequestedIntent() implements UiIntent {}

  record FocusNavigationIntent(dev.tamboui.tui.event.KeyEvent keyEvent, FocusTarget focusTarget)
      implements UiIntent {}

  record MetadataInputIntent(dev.tamboui.tui.event.KeyEvent keyEvent, FocusTarget focusTarget)
      implements UiIntent {}

  record TextInputIntent(dev.tamboui.tui.event.KeyEvent keyEvent, FocusTarget focusTarget)
      implements UiIntent {}

  enum PostGenerationTransition {
    HANDLED,
    QUIT,
    EXPORT_RECIPE,
    GENERATE_AGAIN
  }
}
