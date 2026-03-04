package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import java.util.List;
import org.junit.jupiter.api.Test;

class OverlayRendererTest {

  private static final UiTheme THEME = UiTheme.loadDefault();

  @Test
  void commandPaletteRendersNumberedEntries() {
    Frame frame = frame(120, 32);

    OverlayRenderer.renderCommandPalette(
        frame,
        frame.area(),
        THEME,
        List.of(
            new CommandPaletteEntry(
                "Focus Search", "Ctrl+F", CommandPaletteAction.FOCUS_EXTENSION_SEARCH),
            new CommandPaletteEntry("Reload", "Ctrl+R", CommandPaletteAction.RELOAD_CATALOG)),
        0);

    String rendered = frame.buffer().toAnsiStringTrimmed();
    assertThat(rendered).contains("1. Focus Search [Ctrl+F]");
    assertThat(rendered).contains("2. Reload [Ctrl+R]");
  }

  @Test
  void postGenerationAndVisibilityOverlaysRenderNumberedEntries() {
    Frame postGenerationFrame = frame(120, 32);

    OverlayRenderer.renderPostGenerationOverlay(
        postGenerationFrame, postGenerationFrame.area(), THEME, List.of("Open", "Quit"), 1);
    String postGenerationRendered = postGenerationFrame.buffer().toAnsiStringTrimmed();
    assertThat(postGenerationRendered).contains("1. Open");
    assertThat(postGenerationRendered).contains("2. Quit");

    Frame visibilityFrame = frame(120, 32);
    OverlayRenderer.renderGitHubVisibilityOverlay(
        visibilityFrame, visibilityFrame.area(), THEME, List.of("Private", "Public"), 0);
    String visibilityRendered = visibilityFrame.buffer().toAnsiStringTrimmed();
    assertThat(visibilityRendered).contains("1. Private");
    assertThat(visibilityRendered).contains("2. Public");
  }

  @Test
  void startupAndHelpOverlaysUseParagraphPanelPath() {
    Frame helpFrame = frame(120, 32);

    OverlayRenderer.renderHelpOverlay(
        helpFrame, helpFrame.area(), THEME, List.of("line one", "line two"), "Help [focus]");
    String helpRendered = helpFrame.buffer().toAnsiStringTrimmed();
    assertThat(helpRendered).contains("line one");

    Frame startupFrame = frame(120, 32);
    OverlayRenderer.renderStartupOverlay(
        startupFrame, startupFrame.area(), THEME, List.of("loading metadata..."));
    String startupRendered = startupFrame.buffer().toAnsiStringTrimmed();
    assertThat(startupRendered).contains("loading metadata");
  }

  private static Frame frame(int width, int height) {
    Buffer buffer = Buffer.empty(new Rect(0, 0, width, height));
    return Frame.forTesting(buffer);
  }
}
