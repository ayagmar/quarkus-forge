package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class UiEventRouterTest {
  private static final KeyEvent ANY_KEY = KeyEvent.ofKey(KeyCode.ENTER);

  @Test
  void helpOverlayHandlerWinsWhenItReturnsAction() {
    TestRoutingContext context = new TestRoutingContext();
    context.helpOverlayAction = CoreTuiController.UiAction.handled(false);

    CoreTuiController.UiAction action = new UiEventRouter().routeKeyEvent(ANY_KEY, context);

    assertThat(action).isEqualTo(CoreTuiController.UiAction.handled(false));
    assertThat(context.calls).containsExactly("help");
  }

  @Test
  void togglesHelpOverlayBeforeOtherRoutingFlows() {
    TestRoutingContext context = new TestRoutingContext();
    context.shouldToggleHelp = true;

    CoreTuiController.UiAction action = new UiEventRouter().routeKeyEvent(ANY_KEY, context);

    assertThat(action).isEqualTo(CoreTuiController.UiAction.handled(false));
    assertThat(context.helpToggled).isTrue();
    assertThat(context.calls).containsExactly("help", "shouldToggleHelp");
  }

  @Test
  void routesToGenerationInProgressBranchBeforeGlobalShortcuts() {
    TestRoutingContext context = new TestRoutingContext();
    context.generationInProgress = true;
    context.generationInProgressAction = CoreTuiController.UiAction.handled(false);

    CoreTuiController.UiAction action = new UiEventRouter().routeKeyEvent(ANY_KEY, context);

    assertThat(action).isEqualTo(CoreTuiController.UiAction.handled(false));
    assertThat(context.calls)
        .containsExactly(
            "help",
            "shouldToggleHelp",
            "isCommandPaletteToggle",
            "commandPalette",
            "postGeneration",
            "extensionCancel",
            "quit",
            "isGenerationInProgress",
            "generationInProgress");
  }

  @Test
  void returnsIgnoredWhenNoRouteHandlesEvent() {
    TestRoutingContext context = new TestRoutingContext();

    CoreTuiController.UiAction action = new UiEventRouter().routeKeyEvent(ANY_KEY, context);

    assertThat(action).isEqualTo(CoreTuiController.UiAction.ignored());
    assertThat(context.calls)
        .containsExactly(
            "help",
            "shouldToggleHelp",
            "isCommandPaletteToggle",
            "commandPalette",
            "postGeneration",
            "extensionCancel",
            "quit",
            "isGenerationInProgress",
            "globalShortcut",
            "focusNavigation",
            "submit",
            "extensionFocus",
            "metadata",
            "textInput");
  }

  private static final class TestRoutingContext implements UiEventRouter.RoutingContext {
    private final List<String> calls = new ArrayList<>();
    private CoreTuiController.UiAction helpOverlayAction;
    private boolean shouldToggleHelp;
    private boolean helpToggled;
    private boolean generationInProgress;
    private CoreTuiController.UiAction generationInProgressAction;

    @Override
    public CoreTuiController.UiAction handleHelpOverlayKey(KeyEvent keyEvent) {
      calls.add("help");
      return helpOverlayAction;
    }

    @Override
    public boolean shouldToggleHelpOverlay(KeyEvent keyEvent) {
      calls.add("shouldToggleHelp");
      return shouldToggleHelp;
    }

    @Override
    public void toggleHelpOverlay() {
      helpToggled = true;
    }

    @Override
    public boolean isCommandPaletteToggleKey(KeyEvent keyEvent) {
      calls.add("isCommandPaletteToggle");
      return false;
    }

    @Override
    public void toggleCommandPalette() {
      throw new AssertionError("unexpected toggleCommandPalette");
    }

    @Override
    public CoreTuiController.UiAction handleCommandPaletteKey(KeyEvent keyEvent) {
      calls.add("commandPalette");
      return null;
    }

    @Override
    public CoreTuiController.UiAction handlePostGenerationMenuKey(KeyEvent keyEvent) {
      calls.add("postGeneration");
      return null;
    }

    @Override
    public CoreTuiController.UiAction handleExtensionCancelFlow(KeyEvent keyEvent) {
      calls.add("extensionCancel");
      return null;
    }

    @Override
    public CoreTuiController.UiAction handleQuitFlow(KeyEvent keyEvent) {
      calls.add("quit");
      return null;
    }

    @Override
    public boolean isGenerationInProgress() {
      calls.add("isGenerationInProgress");
      return generationInProgress;
    }

    @Override
    public CoreTuiController.UiAction handleWhileGenerationInProgress(KeyEvent keyEvent) {
      calls.add("generationInProgress");
      return generationInProgressAction;
    }

    @Override
    public CoreTuiController.UiAction handleGlobalShortcutFlow(KeyEvent keyEvent) {
      calls.add("globalShortcut");
      return null;
    }

    @Override
    public CoreTuiController.UiAction handleFocusNavigationFlow(KeyEvent keyEvent) {
      calls.add("focusNavigation");
      return null;
    }

    @Override
    public CoreTuiController.UiAction handleSubmitFlow(KeyEvent keyEvent) {
      calls.add("submit");
      return null;
    }

    @Override
    public CoreTuiController.UiAction handleExtensionFocusFlow(KeyEvent keyEvent) {
      calls.add("extensionFocus");
      return null;
    }

    @Override
    public CoreTuiController.UiAction handleMetadataSelectorFlow(KeyEvent keyEvent) {
      calls.add("metadata");
      return null;
    }

    @Override
    public CoreTuiController.UiAction handleTextInputFlow(KeyEvent keyEvent) {
      calls.add("textInput");
      return null;
    }
  }
}
