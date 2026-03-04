package dev.ayagmar.quarkusforge.ui;

sealed interface UiEffect {
  record CancelPendingAsync() implements UiEffect {}

  record ExportRecipeAndLock() implements UiEffect {}

  record ResetGenerationAfterOutcome() implements UiEffect {}
}
