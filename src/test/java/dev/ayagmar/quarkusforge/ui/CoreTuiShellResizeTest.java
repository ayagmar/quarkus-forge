package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.ResizeEvent;
import org.junit.jupiter.api.Test;

class CoreTuiShellResizeTest {
  @Test
  void rendersNarrowStandardAndWideLayouts() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());

    String narrow = UiControllerTestHarness.renderToString(controller, 80, 28);
    String standard = UiControllerTestHarness.renderToString(controller, 120, 28);
    String wide = UiControllerTestHarness.renderToString(controller, 160, 28);

    assertThat(narrow).contains("Project Metadata").contains("Extensions").contains("Status");
    assertThat(standard).contains("Project Metadata").contains("Extensions").contains("Status");
    assertThat(wide).contains("Project Metadata").contains("Extensions").contains("Status");
  }

  @Test
  void blockedSubmitAlertSurvivesNarrowAndStandardLayouts() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.GROUP_ID);
    for (int i = 0; i < 30; i++) {
      controller.onEvent(KeyEvent.ofKey(KeyCode.BACKSPACE));
    }
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.SUBMIT);
    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));

    String narrow = UiControllerTestHarness.renderToString(controller, 80, 28);
    String standard = UiControllerTestHarness.renderToString(controller, 120, 34);

    assertThat(narrow)
        .contains("Submit blocked")
        .contains("Issue:")
        .contains("Project Metadata")
        .contains("Extensions");
    assertThat(standard)
        .contains("Submit blocked")
        .contains("Issue: must not be blank")
        .contains("Status");
  }

  @Test
  void resizeEventUpdatesStructuredStatusMessage() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());

    controller.onEvent(ResizeEvent.of(90, 30));

    assertThat(controller.statusMessage()).contains("90x30");
  }
}
