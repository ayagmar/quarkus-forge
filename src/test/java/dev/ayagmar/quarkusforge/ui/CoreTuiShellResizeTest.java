package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.ResizeEvent;
import org.junit.jupiter.api.Test;

class CoreTuiShellResizeTest {
  @Test
  void rendersNarrowStandardAndWideLayouts() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());

    String narrow = renderToString(controller, 80, 28);
    String standard = renderToString(controller, 120, 28);
    String wide = renderToString(controller, 160, 28);

    assertThat(narrow).contains("Project Metadata").contains("Extensions").contains("Status");
    assertThat(standard).contains("Project Metadata").contains("Extensions").contains("Status");
    assertThat(wide).contains("Project Metadata").contains("Extensions").contains("Status");
  }

  @Test
  void resizeEventUpdatesStructuredStatusMessage() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());

    controller.onEvent(ResizeEvent.of(90, 30));

    assertThat(controller.statusMessage()).contains("90x30");
  }

  private static String renderToString(CoreTuiController controller, int width, int height) {
    Buffer buffer = Buffer.empty(new Rect(0, 0, width, height));
    Frame frame = Frame.forTesting(buffer);
    controller.render(frame);
    return buffer.toAnsiStringTrimmed();
  }
}
