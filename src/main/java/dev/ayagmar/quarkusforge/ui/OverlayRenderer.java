package dev.ayagmar.quarkusforge.ui;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Overflow;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.gauge.LineGauge;
import dev.tamboui.widgets.paragraph.Paragraph;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders centered overlays (command palette, help, generation progress, post-generation menu,
 * startup splash, GitHub visibility picker) on top of the main TUI viewport.
 *
 * <p>Pure rendering — no state mutation. Each method receives only the data it needs.
 */
final class OverlayRenderer {
  private OverlayRenderer() {}

  static void renderCommandPalette(
      Frame frame,
      Rect viewport,
      UiTheme theme,
      List<CommandPaletteEntry> entries,
      int selectedIndex) {
    if (viewport.width() < 28 || viewport.height() < 10) {
      return;
    }
    List<String> lines = new ArrayList<>();
    for (int i = 0; i < entries.size(); i++) {
      CommandPaletteEntry entry = entries.get(i);
      lines.add((i + 1) + ". " + entry.label() + " [" + entry.shortcut() + "]");
    }
    OverlayPanelRenderer.renderCenteredListOverlay(
        frame,
        viewport,
        theme,
        lines,
        "Enter: run | 1-9: quick run | Up/Down: navigate | Esc or Ctrl+P: close",
        "Command Palette [focus]",
        56,
        true,
        selectedIndex);
  }

  static void renderHelpOverlay(
      Frame frame, Rect viewport, UiTheme theme, List<String> helpLines, String title) {
    if (viewport.width() < 36 || viewport.height() < 12) {
      return;
    }
    OverlayPanelRenderer.renderCenteredParagraphOverlay(
        frame, viewport, theme, helpLines, title, 68, true);
  }

  static void renderStartupOverlay(
      Frame frame, Rect viewport, UiTheme theme, List<String> statusLines) {
    if (viewport.width() < 44 || viewport.height() < 12) {
      return;
    }
    OverlayPanelRenderer.renderCenteredParagraphOverlay(
        frame, viewport, theme, statusLines, "Startup", 64, false);
  }

  static void renderPostGenerationOverlay(
      Frame frame, Rect viewport, UiTheme theme, List<String> actionLabels, int selectedIndex) {
    if (viewport.width() < 36 || viewport.height() < 10) {
      return;
    }
    List<String> lines = new ArrayList<>();
    for (int i = 0; i < actionLabels.size(); i++) {
      lines.add((i + 1) + ". " + actionLabels.get(i));
    }
    OverlayPanelRenderer.renderCenteredListOverlay(
        frame,
        viewport,
        theme,
        lines,
        "Enter: select | Up/Down or j/k: navigate | Esc: quit",
        "Project Generated [focus]",
        58,
        true,
        selectedIndex);
  }

  static void renderGitHubVisibilityOverlay(
      Frame frame, Rect viewport, UiTheme theme, List<String> visibilityLabels, int selectedIndex) {
    if (viewport.width() < 44 || viewport.height() < 10) {
      return;
    }
    List<String> lines = new ArrayList<>();
    for (int index = 0; index < visibilityLabels.size(); index++) {
      lines.add((index + 1) + ". " + visibilityLabels.get(index));
    }
    OverlayPanelRenderer.renderCenteredListOverlay(
        frame,
        viewport,
        theme,
        lines,
        "Enter: confirm | Up/Down or j/k: navigate | Esc: back",
        "Publish to GitHub [focus]",
        66,
        true,
        selectedIndex);
  }

  private static final String[] SPINNER_FRAMES = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};

  static void renderGenerationOverlay(
      Frame frame, Rect viewport, UiTheme theme, double progressRatio, String progressPhase) {
    if (viewport.width() < 30 || viewport.height() < 8) {
      return;
    }
    int width = Math.min(60, viewport.width() - 4);
    int height = 7;
    Rect overlayArea = OverlayPanelRenderer.centeredRect(viewport, width, height);

    int percent = (int) (progressRatio * 100);
    String percentLabel = percent + "%";

    Block overlayBlock =
        Block.builder()
            .title("Generating Project (" + percentLabel + ")")
            .borders(Borders.ALL)
            .borderType(BorderType.ROUNDED)
            .borderStyle(Style.EMPTY.fg(theme.color("accent")).bold())
            .build();

    Rect inner = overlayBlock.inner(overlayArea);
    if (inner.isEmpty() || inner.height() < 4) {
      return;
    }

    frame.renderWidget(overlayBlock, overlayArea);

    List<Rect> rows =
        Layout.vertical()
            .constraints(
                Constraint.length(1), Constraint.length(1), Constraint.length(1), Constraint.fill())
            .split(inner);

    String spinner =
        SPINNER_FRAMES[Math.floorMod(System.nanoTime() / 100_000_000L, SPINNER_FRAMES.length)];
    Paragraph phaseLine =
        Paragraph.builder()
            .text("  " + spinner + " " + progressPhase)
            .style(Style.EMPTY.fg(theme.color("text")).bg(theme.color("base")))
            .overflow(Overflow.ELLIPSIS)
            .build();
    frame.renderWidget(phaseLine, rows.get(0));

    LineGauge gauge =
        LineGauge.builder()
            .ratio(progressRatio)
            .label("  ")
            .lineSet(LineGauge.THICK)
            .filledStyle(Style.EMPTY.fg(theme.color("accent")))
            .unfilledStyle(Style.EMPTY.fg(theme.color("muted")))
            .style(Style.EMPTY.bg(theme.color("base")))
            .build();
    frame.renderWidget(gauge, rows.get(1));

    Paragraph emptyLine =
        Paragraph.builder().text("").style(Style.EMPTY.bg(theme.color("base"))).build();
    frame.renderWidget(emptyLine, rows.get(2));

    Paragraph hintLine =
        Paragraph.builder()
            .text("  Esc: cancel")
            .style(Style.EMPTY.fg(theme.color("muted")).bg(theme.color("base")))
            .overflow(Overflow.ELLIPSIS)
            .build();
    frame.renderWidget(hintLine, rows.get(3));
  }
}
