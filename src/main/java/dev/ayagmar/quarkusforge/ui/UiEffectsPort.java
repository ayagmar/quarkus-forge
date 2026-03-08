package dev.ayagmar.quarkusforge.ui;

import dev.tamboui.tui.event.KeyEvent;
import java.util.List;

interface UiEffectsPort {
  void startCatalogLoad(ExtensionCatalogLoader loader);

  void requestCatalogReload();

  void prepareForGeneration();

  void cancelPendingAsync();

  String exportRecipeAndLock();

  List<UiIntent> executeExtensionCommand(UiIntent.ExtensionCommand command);

  List<UiIntent> applyExtensionNavigationKey(KeyEvent keyEvent);

  List<UiIntent> applyCatalogLoadSuccess(CatalogLoadSuccess success);

  void startGeneration();

  void transitionGenerationState(GenerationState targetState);

  void requestGenerationCancellation();

  void requestAsyncRepaint();

  void moveTextInputCursorToEnd(FocusTarget focusTarget);

  List<UiIntent> applyMetadataSelectorKey(FocusTarget focusTarget, KeyEvent keyEvent);

  List<UiIntent> applyTextInputKey(FocusTarget focusTarget, KeyEvent keyEvent);
}
