package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tamboui.layout.Position;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import org.junit.jupiter.api.Test;

class CoreTuiCursorRenderingTest {

  @Test
  void focusedMetadataInputExposesVisibleCursorAndMovesWithArrowKeys() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.GROUP_ID);

    Frame firstFrame = UiControllerTestHarness.renderFrame(controller, 120, 34);
    Position firstCursor = firstFrame.cursorPosition().orElseThrow();

    controller.onEvent(KeyEvent.ofKey(KeyCode.LEFT));
    Frame secondFrame = UiControllerTestHarness.renderFrame(controller, 120, 34);
    Position secondCursor = secondFrame.cursorPosition().orElseThrow();

    assertThat(secondCursor.y()).isEqualTo(firstCursor.y());
    assertThat(secondCursor.x()).isEqualTo(firstCursor.x() - 1);
  }

  @Test
  void focusedExtensionSearchShowsCursorAndMovesRightWhenTyping() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_SEARCH);

    Frame beforeTyping = UiControllerTestHarness.renderFrame(controller, 120, 34);
    Position cursorBefore = beforeTyping.cursorPosition().orElseThrow();

    controller.onEvent(KeyEvent.ofChar('j'));
    Frame afterTyping = UiControllerTestHarness.renderFrame(controller, 120, 34);
    Position cursorAfter = afterTyping.cursorPosition().orElseThrow();

    assertThat(cursorAfter.y()).isEqualTo(cursorBefore.y());
    assertThat(cursorAfter.x()).isEqualTo(cursorBefore.x() + 1);
  }
}
