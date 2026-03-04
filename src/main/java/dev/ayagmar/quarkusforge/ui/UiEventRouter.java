package dev.ayagmar.quarkusforge.ui;

import dev.tamboui.tui.event.KeyEvent;

/** Enforces deterministic key-routing priority across overlays, flows, and input handlers. */
final class UiEventRouter {

  UiAction routeKeyEvent(KeyEvent keyEvent, UiRoutingContext context) {
    UiAction helpOverlayAction = context.handleHelpOverlayKey(keyEvent);
    if (helpOverlayAction != null) {
      return helpOverlayAction;
    }
    if (context.shouldToggleHelpOverlay(keyEvent)) {
      context.toggleHelpOverlay();
      return UiAction.handled(false);
    }
    if (context.isCommandPaletteToggleKey(keyEvent)) {
      context.toggleCommandPalette();
      return UiAction.handled(false);
    }

    UiAction commandPaletteAction = context.handleCommandPaletteKey(keyEvent);
    if (commandPaletteAction != null) {
      return commandPaletteAction;
    }

    UiAction postGenerationAction = context.handlePostGenerationMenuKey(keyEvent);
    if (postGenerationAction != null) {
      return postGenerationAction;
    }

    UiAction cancelAction = context.handleExtensionCancelFlow(keyEvent);
    if (cancelAction != null) {
      return cancelAction;
    }

    UiAction quitAction = context.handleQuitFlow(keyEvent);
    if (quitAction != null) {
      return quitAction;
    }

    if (context.isGenerationInProgress()) {
      return context.handleWhileGenerationInProgress(keyEvent);
    }

    UiAction globalShortcutAction = context.handleGlobalShortcutFlow(keyEvent);
    if (globalShortcutAction != null) {
      return globalShortcutAction;
    }

    UiAction focusNavigationAction = context.handleFocusNavigationFlow(keyEvent);
    if (focusNavigationAction != null) {
      return focusNavigationAction;
    }

    UiAction submitAction = context.handleSubmitFlow(keyEvent);
    if (submitAction != null) {
      return submitAction;
    }

    UiAction extensionFlowAction = context.handleExtensionFocusFlow(keyEvent);
    if (extensionFlowAction != null) {
      return extensionFlowAction;
    }

    UiAction metadataFlowAction = context.handleMetadataSelectorFlow(keyEvent);
    if (metadataFlowAction != null) {
      return metadataFlowAction;
    }

    UiAction textInputFlowAction = context.handleTextInputFlow(keyEvent);
    if (textInputFlowAction != null) {
      return textInputFlowAction;
    }

    return UiAction.ignored();
  }
}
