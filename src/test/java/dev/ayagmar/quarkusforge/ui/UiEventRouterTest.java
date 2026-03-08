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
    context.helpOverlayAction = UiAction.handled(false);

    UiAction action = new UiEventRouter().routeKeyEvent(ANY_KEY, context);

    assertThat(action).isEqualTo(UiAction.handled(false));
    assertThat(context.calls).containsExactly("help");
  }

  @Test
  void togglesHelpOverlayBeforeOtherRoutingFlows() {
    TestRoutingContext context = new TestRoutingContext();
    context.shouldToggleHelp = true;

    UiAction action = new UiEventRouter().routeKeyEvent(ANY_KEY, context);

    assertThat(action).isEqualTo(UiAction.handled(false));
    assertThat(context.helpToggled).isTrue();
    assertThat(context.calls).containsExactly("help", "shouldToggleHelp");
  }

  @Test
  void routesToGenerationInProgressBranchBeforeGlobalShortcuts() {
    TestRoutingContext context = new TestRoutingContext();
    context.generationInProgress = true;
    context.generationInProgressAction = UiAction.handled(false);

    UiAction action = new UiEventRouter().routeKeyEvent(ANY_KEY, context);

    assertThat(action).isEqualTo(UiAction.handled(false));
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

    UiAction action = new UiEventRouter().routeKeyEvent(ANY_KEY, context);

    assertThat(action).isEqualTo(UiAction.ignored());
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

  @Test
  void commandPaletteToggleWinsBeforeCommandPaletteHandler() {
    TestRoutingContext context = new TestRoutingContext();
    context.commandPaletteToggle = true;

    UiAction action = new UiEventRouter().routeKeyEvent(ANY_KEY, context);

    assertThat(action).isEqualTo(UiAction.handled(false));
    assertThat(context.commandPaletteToggled).isTrue();
    assertThat(context.calls).containsExactly("help", "shouldToggleHelp", "isCommandPaletteToggle");
  }

  @Test
  void postGenerationHandlerWinsBeforeCancelAndQuitFlows() {
    TestRoutingContext context = new TestRoutingContext();
    context.postGenerationAction = UiAction.handled(false);

    UiAction action = new UiEventRouter().routeKeyEvent(ANY_KEY, context);

    assertThat(action).isEqualTo(UiAction.handled(false));
    assertThat(context.calls)
        .containsExactly(
            "help",
            "shouldToggleHelp",
            "isCommandPaletteToggle",
            "commandPalette",
            "postGeneration");
  }

  @Test
  void commandPaletteHandlerWinsBeforeGlobalShortcutAndFocusFlows() {
    TestRoutingContext context = new TestRoutingContext();
    context.commandPaletteAction = UiAction.handled(false);

    UiAction action = new UiEventRouter().routeKeyEvent(ANY_KEY, context);

    assertThat(action).isEqualTo(UiAction.handled(false));
    assertThat(context.calls)
        .containsExactly("help", "shouldToggleHelp", "isCommandPaletteToggle", "commandPalette");
  }

  @Test
  void extensionCancelWinsBeforeQuitFlow() {
    TestRoutingContext context = new TestRoutingContext();
    context.cancelAction = UiAction.handled(false);

    UiAction action = new UiEventRouter().routeKeyEvent(ANY_KEY, context);

    assertThat(action).isEqualTo(UiAction.handled(false));
    assertThat(context.calls)
        .containsExactly(
            "help",
            "shouldToggleHelp",
            "isCommandPaletteToggle",
            "commandPalette",
            "postGeneration",
            "extensionCancel");
  }

  private static final class TestRoutingContext implements UiRoutingContext {
    private final List<String> calls = new ArrayList<>();
    private UiAction helpOverlayAction;
    private boolean shouldToggleHelp;
    private boolean helpToggled;
    private boolean commandPaletteToggle;
    private boolean commandPaletteToggled;
    private UiAction commandPaletteAction;
    private UiAction postGenerationAction;
    private UiAction cancelAction;
    private boolean generationInProgress;
    private UiAction generationInProgressAction;

    @Override
    public UiAction handleHelpOverlayKey(KeyEvent keyEvent) {
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
      return commandPaletteToggle;
    }

    @Override
    public void toggleCommandPalette() {
      commandPaletteToggled = true;
    }

    @Override
    public UiAction handleCommandPaletteKey(KeyEvent keyEvent) {
      calls.add("commandPalette");
      return commandPaletteAction;
    }

    @Override
    public UiAction handlePostGenerationMenuKey(KeyEvent keyEvent) {
      calls.add("postGeneration");
      return postGenerationAction;
    }

    @Override
    public UiAction handleExtensionCancelFlow(KeyEvent keyEvent) {
      calls.add("extensionCancel");
      return cancelAction;
    }

    @Override
    public UiAction handleQuitFlow(KeyEvent keyEvent) {
      calls.add("quit");
      return null;
    }

    @Override
    public boolean isGenerationInProgress() {
      calls.add("isGenerationInProgress");
      return generationInProgress;
    }

    @Override
    public UiAction handleWhileGenerationInProgress(KeyEvent keyEvent) {
      calls.add("generationInProgress");
      return generationInProgressAction;
    }

    @Override
    public UiAction handleGlobalShortcutFlow(KeyEvent keyEvent) {
      calls.add("globalShortcut");
      return null;
    }

    @Override
    public UiAction handleFocusNavigationFlow(KeyEvent keyEvent) {
      calls.add("focusNavigation");
      return null;
    }

    @Override
    public UiAction handleSubmitFlow(KeyEvent keyEvent) {
      calls.add("submit");
      return null;
    }

    @Override
    public UiAction handleExtensionFocusFlow(KeyEvent keyEvent) {
      calls.add("extensionFocus");
      return null;
    }

    @Override
    public UiAction handleMetadataSelectorFlow(KeyEvent keyEvent) {
      calls.add("metadata");
      return null;
    }

    @Override
    public UiAction handleTextInputFlow(KeyEvent keyEvent) {
      calls.add("textInput");
      return null;
    }
  }
}
