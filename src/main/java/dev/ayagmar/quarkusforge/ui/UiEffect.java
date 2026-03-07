package dev.ayagmar.quarkusforge.ui;

/** Reducer-emitted side effects executed outside pure transition logic. */
sealed interface UiEffect {
  record StartCatalogLoad(ExtensionCatalogLoader loader) implements UiEffect {}

  record RequestCatalogReload() implements UiEffect {}

  record PrepareForGeneration() implements UiEffect {}

  record CancelPendingAsync() implements UiEffect {}

  record ExportRecipeAndLock() implements UiEffect {}

  record ExecuteExtensionCommand(UiIntent.ExtensionCommand command) implements UiEffect {}

  record ApplyExtensionNavigationKey(dev.tamboui.tui.event.KeyEvent keyEvent) implements UiEffect {}

  record ApplyCatalogLoadSuccess(CatalogLoadSuccess success) implements UiEffect {}

  record StartGeneration() implements UiEffect {}

  record TransitionGenerationState(CoreTuiController.GenerationState targetState)
      implements UiEffect {}

  record RequestGenerationCancellation() implements UiEffect {}

  record RequestAsyncRepaint() implements UiEffect {}

  record MoveTextInputCursorToEnd(FocusTarget focusTarget) implements UiEffect {}

  record ApplyMetadataSelectorKey(FocusTarget focusTarget, dev.tamboui.tui.event.KeyEvent keyEvent)
      implements UiEffect {}

  record ApplyTextInputKey(FocusTarget focusTarget, dev.tamboui.tui.event.KeyEvent keyEvent)
      implements UiEffect {}
}
