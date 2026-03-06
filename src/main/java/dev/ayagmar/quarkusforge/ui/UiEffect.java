package dev.ayagmar.quarkusforge.ui;

/** Reducer-emitted side effects executed outside pure transition logic. */
sealed interface UiEffect {
  record PrepareForGeneration() implements UiEffect {}

  record CancelPendingAsync() implements UiEffect {}

  record ExportRecipeAndLock() implements UiEffect {}

  record StartGeneration() implements UiEffect {}

  record TransitionGenerationState(CoreTuiController.GenerationState targetState)
      implements UiEffect {}

  record RequestGenerationCancellation() implements UiEffect {}

  record RequestAsyncRepaint() implements UiEffect {}

  record ApplyMetadataSelectorKey(FocusTarget focusTarget, dev.tamboui.tui.event.KeyEvent keyEvent)
      implements UiEffect {}

  record ApplyTextInputKey(FocusTarget focusTarget, dev.tamboui.tui.event.KeyEvent keyEvent)
      implements UiEffect {}
}
