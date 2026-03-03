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
import dev.tamboui.widgets.list.ListState;
import dev.tamboui.widgets.list.ListWidget;
import dev.tamboui.widgets.paragraph.Paragraph;
import java.util.List;

final class OverlayPanelRenderer {
  private OverlayPanelRenderer() {}

  static void renderCenteredListOverlay(
      Frame frame,
      Rect viewport,
      UiTheme theme,
      List<String> options,
      String hint,
      String title,
      int minWidth,
      boolean focusBorder,
      int selectedIndex) {
    if (viewport.width() < 3 || viewport.height() < 3) {
      return;
    }
    int maxOptionLength = options.stream().mapToInt(String::length).max().orElse(40);
    int maxOverlayWidth = Math.max(1, viewport.width() - 2);
    int maxOverlayHeight = Math.max(1, viewport.height() - 2);
    int width =
        Math.min(Math.max(minWidth, Math.max(maxOptionLength, hint.length()) + 6), maxOverlayWidth);
    int height = Math.min(options.size() + 4, maxOverlayHeight);
    Rect overlayArea = centeredRect(viewport, width, height);

    Style borderStyle = focusBorder ? focusedBorderStyle(theme) : defaultBorderStyle(theme);
    Block overlayBlock =
        Block.builder()
            .title(title)
            .borders(Borders.ALL)
            .borderType(BorderType.ROUNDED)
            .borderStyle(borderStyle)
            .build();
    frame.renderWidget(overlayBlock, overlayArea);

    Rect inner = overlayBlock.inner(overlayArea);
    if (inner.isEmpty() || inner.height() < 2) {
      return;
    }

    List<Rect> rows =
        Layout.vertical().constraints(Constraint.fill(), Constraint.length(1)).split(inner);

    ListWidget list =
        ListWidget.builder()
            .items(options.toArray(new String[0]))
            .style(Style.EMPTY.fg(theme.color("text")).bg(theme.color("base")))
            .highlightStyle(Style.EMPTY.fg(theme.color("focus")).reversed().bold())
            .highlightSymbol("> ")
            .build();
    ListState state = new ListState();
    if (!options.isEmpty()) {
      int clampedIndex = Math.max(0, Math.min(selectedIndex, options.size() - 1));
      state.select(clampedIndex);
    }
    frame.renderStatefulWidget(list, rows.getFirst(), state);

    frame.renderWidget(
        Paragraph.builder()
            .text(hint)
            .style(Style.EMPTY.fg(theme.color("muted")).bg(theme.color("base")))
            .overflow(Overflow.ELLIPSIS)
            .build(),
        rows.get(1));
  }

  static void renderCenteredParagraphOverlay(
      Frame frame,
      Rect viewport,
      UiTheme theme,
      List<String> lines,
      String title,
      int minWidth,
      boolean focusBorder) {
    if (viewport.width() < 3 || viewport.height() < 3) {
      return;
    }
    int maxLineLength = lines.stream().mapToInt(String::length).max().orElse(40);
    int maxOverlayWidth = Math.max(1, viewport.width() - 2);
    int maxOverlayHeight = Math.max(1, viewport.height() - 2);
    int width = Math.min(Math.max(minWidth, maxLineLength + 4), maxOverlayWidth);
    int height = Math.min(lines.size() + 2, maxOverlayHeight);
    Rect overlayArea = centeredRect(viewport, width, height);

    Style borderStyle = focusBorder ? focusedBorderStyle(theme) : defaultBorderStyle(theme);

    Paragraph overlay =
        Paragraph.builder()
            .text(String.join("\n", lines))
            .style(Style.EMPTY.fg(theme.color("text")).bg(theme.color("base")))
            .overflow(Overflow.ELLIPSIS)
            .block(
                Block.builder()
                    .title(title)
                    .borders(Borders.ALL)
                    .borderType(BorderType.ROUNDED)
                    .borderStyle(borderStyle)
                    .build())
            .build();
    frame.renderWidget(overlay, overlayArea);
  }

  static Rect centeredRect(Rect viewport, int width, int height) {
    return new Rect(
        viewport.x() + Math.max(0, (viewport.width() - width) / 2),
        viewport.y() + Math.max(0, (viewport.height() - height) / 2),
        width,
        height);
  }

  private static Style focusedBorderStyle(UiTheme theme) {
    return Style.EMPTY.fg(theme.color("focus")).bold();
  }

  private static Style defaultBorderStyle(UiTheme theme) {
    return Style.EMPTY.fg(theme.color("accent")).bold();
  }
}
