package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import dev.tamboui.tui.event.ResizeEvent;
import org.junit.jupiter.api.Test;

class CoreTuiShellPilotTest {
  @Test
  void focusTraversalCyclesWithTabAndShiftTab() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());

    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.GROUP_ID);

    controller.onEvent(KeyEvent.ofKey(KeyCode.TAB));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.ARTIFACT_ID);

    controller.onEvent(KeyEvent.ofKey(KeyCode.TAB, KeyModifiers.SHIFT));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.GROUP_ID);
  }

  @Test
  void listNavigationAndSpaceToggleAreRoutedWhenListIsFocused() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());
    moveFocusTo(controller, FocusTarget.EXTENSION_LIST);

    CoreTuiController.UiAction downAction = controller.onEvent(KeyEvent.ofKey(KeyCode.DOWN));
    CoreTuiController.UiAction toggleAction = controller.onEvent(KeyEvent.ofChar(' '));

    assertThat(downAction.handled()).isTrue();
    assertThat(toggleAction.handled()).isTrue();
    assertThat(controller.selectedExtensionIds()).hasSize(1);
  }

  @Test
  void enterSubmitsWhenValidAndBlocksWhenValidationFails() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    assertThat(controller.submitRequested()).isTrue();
    assertThat(controller.statusMessage()).contains("Submit requested");

    moveFocusTo(controller, FocusTarget.GROUP_ID);
    for (int i = 0; i < 30; i++) {
      controller.onEvent(KeyEvent.ofKey(KeyCode.BACKSPACE));
    }
    assertThat(controller.validation().isValid()).isFalse();

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    assertThat(controller.statusMessage()).contains("Submit blocked");
  }

  @Test
  void enterSubmitsInsteadOfTogglingWhenExtensionListIsFocused() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());
    moveFocusTo(controller, FocusTarget.EXTENSION_LIST);

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));

    assertThat(controller.submitRequested()).isTrue();
    assertThat(controller.selectedExtensionIds()).isEmpty();
    assertThat(controller.statusMessage()).contains("Submit requested");
  }

  @Test
  void fixingInputWithoutChangingFocusClearsBlockedSubmitErrorFromFooter() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());
    moveFocusTo(controller, FocusTarget.JAVA_VERSION);

    controller.onEvent(KeyEvent.ofChar('x'));
    assertThat(controller.validation().isValid()).isFalse();

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    assertThat(renderToString(controller)).contains("Error:");

    controller.onEvent(KeyEvent.ofKey(KeyCode.BACKSPACE));

    assertThat(controller.validation().isValid()).isTrue();
    assertThat(controller.statusMessage()).contains("Validation restored");
    assertThat(renderToString(controller)).doesNotContain("Error:");
  }

  @Test
  void blockedSubmitFeedbackRecoversEvenIfStatusMessageChangesBeforeFix() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());
    moveFocusTo(controller, FocusTarget.JAVA_VERSION);

    controller.onEvent(KeyEvent.ofChar('x'));
    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    assertThat(renderToString(controller)).contains("Error:");

    controller.onEvent(ResizeEvent.of(90, 30));
    controller.onEvent(KeyEvent.ofKey(KeyCode.BACKSPACE));

    assertThat(controller.validation().isValid()).isTrue();
    assertThat(controller.statusMessage()).contains("Validation restored");
    assertThat(renderToString(controller)).doesNotContain("Error:");
  }

  @Test
  void slashShortcutJumpsFocusToExtensionSearch() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());

    controller.onEvent(KeyEvent.ofChar('/'));

    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.EXTENSION_SEARCH);
    assertThat(controller.statusMessage()).contains("Focus moved to extensionSearch");
  }

  @Test
  void slashIsInsertedInOutputDirectoryWithoutStealingFocus() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());
    moveFocusTo(controller, FocusTarget.OUTPUT_DIR);

    CoreTuiController.UiAction action = controller.onEvent(KeyEvent.ofChar('/'));

    assertThat(action.handled()).isTrue();
    assertThat(action.shouldQuit()).isFalse();
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.OUTPUT_DIR);
    assertThat(controller.request().outputDirectory()).endsWith("/");
  }

  @Test
  void ctrlFAndCtrlLShortcutsJumpBetweenSearchAndList() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());

    controller.onEvent(KeyEvent.ofChar('f', KeyModifiers.CTRL));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.EXTENSION_SEARCH);

    controller.onEvent(KeyEvent.ofChar('l', KeyModifiers.CTRL));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.EXTENSION_LIST);
  }

  @Test
  void searchAndListSupportDirectArrowHandoff() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());
    moveFocusTo(controller, FocusTarget.EXTENSION_SEARCH);

    controller.onEvent(KeyEvent.ofKey(KeyCode.DOWN));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.EXTENSION_LIST);

    controller.onEvent(KeyEvent.ofKey(KeyCode.UP));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.EXTENSION_SEARCH);
  }

  @Test
  void qNoLongerTriggersQuitByDefault() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());

    CoreTuiController.UiAction action = controller.onEvent(KeyEvent.ofChar('q'));

    assertThat(action.shouldQuit()).isFalse();
    assertThat(action.handled()).isTrue();
    assertThat(controller.request().groupId()).endsWith("q");
  }

  @Test
  void ctrlCStillQuitsFromShell() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());

    CoreTuiController.UiAction action = controller.onEvent(KeyEvent.ofChar('c', KeyModifiers.CTRL));

    assertThat(action.shouldQuit()).isTrue();
    assertThat(action.handled()).isTrue();
  }

  private static void moveFocusTo(CoreTuiController controller, FocusTarget target) {
    for (int i = 0; i < 20 && controller.focusTarget() != target; i++) {
      controller.onEvent(KeyEvent.ofKey(KeyCode.TAB));
    }
    assertThat(controller.focusTarget()).isEqualTo(target);
  }

  private static String renderToString(CoreTuiController controller) {
    Buffer buffer = Buffer.empty(new Rect(0, 0, 120, 32));
    Frame frame = Frame.forTesting(buffer);
    controller.render(frame);
    return buffer.toAnsiStringTrimmed();
  }
}
