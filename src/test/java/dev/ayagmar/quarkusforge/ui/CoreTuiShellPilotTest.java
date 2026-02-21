package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.api.MetadataSnapshotLoader;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityValidator;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ProjectRequestValidator;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.junit.jupiter.api.Test;

class CoreTuiShellPilotTest {
  @Test
  void focusTraversalCyclesWithTabAndShiftTab() {
    CoreTuiController controller = CoreTuiController.from(validInitialState());

    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.GROUP_ID);

    controller.onEvent(KeyEvent.ofKey(KeyCode.TAB));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.ARTIFACT_ID);

    controller.onEvent(KeyEvent.ofKey(KeyCode.TAB, KeyModifiers.SHIFT));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.GROUP_ID);
  }

  @Test
  void listNavigationAndSpaceToggleAreRoutedWhenListIsFocused() {
    CoreTuiController controller = CoreTuiController.from(validInitialState());
    moveFocusTo(controller, FocusTarget.EXTENSION_LIST);

    CoreTuiController.UiAction downAction = controller.onEvent(KeyEvent.ofKey(KeyCode.DOWN));
    CoreTuiController.UiAction toggleAction = controller.onEvent(KeyEvent.ofChar(' '));

    assertThat(downAction.handled()).isTrue();
    assertThat(toggleAction.handled()).isTrue();
    assertThat(controller.selectedExtensionIds()).hasSize(1);
  }

  @Test
  void enterSubmitsWhenValidAndBlocksWhenValidationFails() {
    CoreTuiController controller = CoreTuiController.from(validInitialState());

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

  private static void moveFocusTo(CoreTuiController controller, FocusTarget target) {
    for (int i = 0; i < 20 && controller.focusTarget() != target; i++) {
      controller.onEvent(KeyEvent.ofKey(KeyCode.TAB));
    }
    assertThat(controller.focusTarget()).isEqualTo(target);
  }

  private static ForgeUiState validInitialState() {
    ProjectRequest request =
        new ProjectRequest(
            "com.example",
            "forge-app",
            "1.0.0-SNAPSHOT",
            "com.example.forge.app",
            "./generated",
            "maven",
            "25");
    ValidationReport validation =
        new ProjectRequestValidator()
            .validate(request)
            .merge(
                new MetadataCompatibilityValidator()
                    .validate(request, MetadataSnapshotLoader.loadDefault()));
    return new ForgeUiState(request, validation);
  }
}
