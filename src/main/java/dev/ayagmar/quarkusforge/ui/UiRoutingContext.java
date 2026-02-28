package dev.ayagmar.quarkusforge.ui;

import dev.tamboui.tui.event.KeyEvent;

interface UiRoutingContext {
  UiAction handleHelpOverlayKey(KeyEvent keyEvent);

  boolean shouldToggleHelpOverlay(KeyEvent keyEvent);

  void toggleHelpOverlay();

  boolean isCommandPaletteToggleKey(KeyEvent keyEvent);

  void toggleCommandPalette();

  UiAction handleCommandPaletteKey(KeyEvent keyEvent);

  UiAction handlePostGenerationMenuKey(KeyEvent keyEvent);

  UiAction handleExtensionCancelFlow(KeyEvent keyEvent);

  UiAction handleQuitFlow(KeyEvent keyEvent);

  boolean isGenerationInProgress();

  UiAction handleWhileGenerationInProgress(KeyEvent keyEvent);

  UiAction handleGlobalShortcutFlow(KeyEvent keyEvent);

  UiAction handleFocusNavigationFlow(KeyEvent keyEvent);

  UiAction handleSubmitFlow(KeyEvent keyEvent);

  UiAction handleExtensionFocusFlow(KeyEvent keyEvent);

  UiAction handleMetadataSelectorFlow(KeyEvent keyEvent);

  UiAction handleTextInputFlow(KeyEvent keyEvent);
}
