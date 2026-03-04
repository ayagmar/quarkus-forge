package dev.ayagmar.quarkusforge.ui;

sealed interface UiEffect {
  record CancelPendingAsync() implements UiEffect {}

  record ExportRecipeAndLock() implements UiEffect {}

  record ResetGenerationAfterOutcome() implements UiEffect {}

  record StartGeneration() implements UiEffect {}

  record RequestGenerationCancellation() implements UiEffect {}

  record ShowPostGenerationSuccess(java.nio.file.Path generatedPath, String nextCommand)
      implements UiEffect {}

  record HidePostGenerationMenu() implements UiEffect {}

  record RequestAsyncRepaint() implements UiEffect {}
}
