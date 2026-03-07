package dev.ayagmar.quarkusforge.ui;

import dev.tamboui.tui.event.KeyEvent;

interface UiEffectsPort {
  void startCatalogLoad(ExtensionCatalogLoader loader);

  void requestCatalogReload();

  void prepareForGeneration();

  void cancelPendingAsync();

  void exportRecipeAndLock();

  String executeExtensionCommand(UiIntent.ExtensionCommand command);

  void applyExtensionNavigationKey(KeyEvent keyEvent);

  void applyCatalogLoadSuccess(CatalogLoadSuccess success);

  void startGeneration();

  void transitionGenerationState(CoreTuiController.GenerationState targetState);

  void requestGenerationCancellation();

  void requestAsyncRepaint();

  void moveTextInputCursorToEnd(FocusTarget focusTarget);

  void applyMetadataSelectorKey(FocusTarget focusTarget, KeyEvent keyEvent);

  void applyTextInputKey(FocusTarget focusTarget, KeyEvent keyEvent);
}
