package dev.ayagmar.quarkusforge.ui;

import dev.tamboui.tui.event.KeyEvent;

final class UiEventRouter {

  CoreTuiController.UiAction routeKeyEvent(KeyEvent keyEvent, RoutingContext context) {
    CoreTuiController.UiAction helpOverlayAction = context.handleHelpOverlayKey(keyEvent);
    if (helpOverlayAction != null) {
      return helpOverlayAction;
    }
    if (context.shouldToggleHelpOverlay(keyEvent)) {
      context.toggleHelpOverlay();
      return CoreTuiController.UiAction.handled(false);
    }
    if (context.isCommandPaletteToggleKey(keyEvent)) {
      context.toggleCommandPalette();
      return CoreTuiController.UiAction.handled(false);
    }

    CoreTuiController.UiAction commandPaletteAction = context.handleCommandPaletteKey(keyEvent);
    if (commandPaletteAction != null) {
      return commandPaletteAction;
    }

    CoreTuiController.UiAction postGenerationAction = context.handlePostGenerationMenuKey(keyEvent);
    if (postGenerationAction != null) {
      return postGenerationAction;
    }

    CoreTuiController.UiAction cancelAction = context.handleExtensionCancelFlow(keyEvent);
    if (cancelAction != null) {
      return cancelAction;
    }

    CoreTuiController.UiAction quitAction = context.handleQuitFlow(keyEvent);
    if (quitAction != null) {
      return quitAction;
    }

    if (context.isGenerationInProgress()) {
      return context.handleWhileGenerationInProgress(keyEvent);
    }

    CoreTuiController.UiAction globalShortcutAction = context.handleGlobalShortcutFlow(keyEvent);
    if (globalShortcutAction != null) {
      return globalShortcutAction;
    }

    CoreTuiController.UiAction focusNavigationAction = context.handleFocusNavigationFlow(keyEvent);
    if (focusNavigationAction != null) {
      return focusNavigationAction;
    }

    CoreTuiController.UiAction submitAction = context.handleSubmitFlow(keyEvent);
    if (submitAction != null) {
      return submitAction;
    }

    CoreTuiController.UiAction extensionFlowAction = context.handleExtensionFocusFlow(keyEvent);
    if (extensionFlowAction != null) {
      return extensionFlowAction;
    }

    CoreTuiController.UiAction metadataFlowAction = context.handleMetadataSelectorFlow(keyEvent);
    if (metadataFlowAction != null) {
      return metadataFlowAction;
    }

    CoreTuiController.UiAction textInputFlowAction = context.handleTextInputFlow(keyEvent);
    if (textInputFlowAction != null) {
      return textInputFlowAction;
    }

    return CoreTuiController.UiAction.ignored();
  }

  interface RoutingContext {
    CoreTuiController.UiAction handleHelpOverlayKey(KeyEvent keyEvent);

    boolean shouldToggleHelpOverlay(KeyEvent keyEvent);

    void toggleHelpOverlay();

    boolean isCommandPaletteToggleKey(KeyEvent keyEvent);

    void toggleCommandPalette();

    CoreTuiController.UiAction handleCommandPaletteKey(KeyEvent keyEvent);

    CoreTuiController.UiAction handlePostGenerationMenuKey(KeyEvent keyEvent);

    CoreTuiController.UiAction handleExtensionCancelFlow(KeyEvent keyEvent);

    CoreTuiController.UiAction handleQuitFlow(KeyEvent keyEvent);

    boolean isGenerationInProgress();

    CoreTuiController.UiAction handleWhileGenerationInProgress(KeyEvent keyEvent);

    CoreTuiController.UiAction handleGlobalShortcutFlow(KeyEvent keyEvent);

    CoreTuiController.UiAction handleFocusNavigationFlow(KeyEvent keyEvent);

    CoreTuiController.UiAction handleSubmitFlow(KeyEvent keyEvent);

    CoreTuiController.UiAction handleExtensionFocusFlow(KeyEvent keyEvent);

    CoreTuiController.UiAction handleMetadataSelectorFlow(KeyEvent keyEvent);

    CoreTuiController.UiAction handleTextInputFlow(KeyEvent keyEvent);
  }
}
