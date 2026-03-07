package dev.ayagmar.quarkusforge.ui;

import dev.tamboui.tui.event.KeyEvent;

interface UiEffectsPort {
  void startCatalogLoad(ExtensionCatalogLoader loader);

  void requestCatalogReload();

  void prepareForGeneration();

  void cancelPendingAsync();

  void exportRecipeAndLock();

  void executeCommandPaletteAction(CommandPaletteAction action);

  void applyCatalogLoadSuccess(CatalogLoadSuccess success);

  void startGeneration();

  void transitionGenerationState(CoreTuiController.GenerationState targetState);

  void requestGenerationCancellation();

  void requestAsyncRepaint();

  void applyMetadataSelectorKey(FocusTarget focusTarget, KeyEvent keyEvent);

  void applyTextInputKey(FocusTarget focusTarget, KeyEvent keyEvent);
}
