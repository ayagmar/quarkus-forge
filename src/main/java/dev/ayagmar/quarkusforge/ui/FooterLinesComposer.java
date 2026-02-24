package dev.ayagmar.quarkusforge.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class FooterLinesComposer {
  private static final int NARROW_WIDTH_THRESHOLD = 100;

  List<String> compose(int width, FooterSnapshot snapshot) {
    Objects.requireNonNull(snapshot);
    List<String> lines = new ArrayList<>();
    lines.add(footerHintLine(snapshot, width));
    lines.add(
        "Mode: " + snapshot.modeLabel() + " | Generation: " + snapshot.generationStateLabel());
    lines.add("Status: " + snapshot.statusMessage());
    lines.add(
        "Validation: " + snapshot.validationLabel() + " | Focus: " + snapshot.focusTargetName());

    String activeError = snapshot.activeErrorDetails();
    if (!activeError.isBlank()) {
      lines.add("Error: " + activeError);
    }

    int expandedErrorLines =
        expandedErrorDetailLines(activeError, snapshot.showErrorDetails(), width);
    if (expandedErrorLines > 0) {
      lines.add("Error details:");
      lines.addAll(wrapToWidth(activeError, Math.max(24, width - 6), expandedErrorLines));
    }

    if (!snapshot.successHint().isBlank()) {
      lines.add("Next: " + truncate(snapshot.successHint(), Math.max(24, width - 16)));
    }
    return lines;
  }

  private static String footerHintLine(FooterSnapshot snapshot, int width) {
    if (snapshot.generationInProgress()) {
      return width < NARROW_WIDTH_THRESHOLD
          ? "Esc: cancel generation | Enter disabled"
          : "Esc: cancel generation | Enter disabled while generation is loading";
    }
    if (snapshot.commandPaletteVisible()) {
      return width < NARROW_WIDTH_THRESHOLD
          ? "Palette: Up/Down | Enter run | Esc/? close"
          : "Command palette: Up/Down/Home/End or j/k: navigate | Enter: run | 1-9: quick run | Esc or ?: close";
    }
    if (snapshot.focusTarget() == FocusTarget.EXTENSION_LIST) {
      return width < NARROW_WIDTH_THRESHOLD
          ? "Up/Down or j/k: nav | Space: select | F: favorite | c: category | ?: commands"
          : "Up/Down/Home/End or j/k: list nav | Space: select | F: favorite | c: close/open category | C: open all | Ctrl+J: jump favorite | Ctrl+K: favorite filter | Ctrl+R: reload | Ctrl+E: error details | ?: commands";
    }
    if (snapshot.focusTarget() == FocusTarget.EXTENSION_SEARCH) {
      return width < NARROW_WIDTH_THRESHOLD
          ? "Type: filter | Down: list | Ctrl+K: fav filter | ?: commands"
          : "Type: filter extensions | Down: list | Ctrl+R: reload | Ctrl+J: jump favorite | Ctrl+K: favorite filter | Ctrl+E: error details | ?: commands";
    }
    if (snapshot.focusTarget() == FocusTarget.SUBMIT) {
      return width < NARROW_WIDTH_THRESHOLD
          ? "Enter: submit | j/k: focus | Ctrl+E: error details | ?: commands"
          : "Enter: submit | Tab/Shift+Tab or j/k: focus | Ctrl+E: error details | Esc: cancel/quit | ?: commands";
    }
    if (snapshot.metadataSelectorFocus()) {
      return width < NARROW_WIDTH_THRESHOLD
          ? "Left/Right or h/l: pick | Up/Down or j/k: cycle | ?: commands"
          : "Left/Right/Home/End or h/l/j/k: pick value | Tab/Shift+Tab: focus | Enter: submit | Ctrl+E: error details | ?: commands";
    }
    return width < NARROW_WIDTH_THRESHOLD
        ? "Tab: focus | Enter: submit | Ctrl+E: error details | ?: commands"
        : "Tab/Shift+Tab: focus | Enter: submit | /: search | Ctrl+K: favorite filter | Ctrl+E: error details | Esc: cancel/quit | ?: commands";
  }

  private static int expandedErrorDetailLines(
      String activeError, boolean showErrorDetails, int width) {
    if (!showErrorDetails || activeError.isBlank()) {
      return 0;
    }
    return wrapToWidth(activeError, Math.max(24, width - 6), 6).size();
  }

  private static List<String> wrapToWidth(String text, int width, int maxLines) {
    List<String> lines = new ArrayList<>();
    if (text == null || text.isBlank() || width <= 0 || maxLines <= 0) {
      return lines;
    }

    String remaining = text.strip();
    while (!remaining.isBlank() && lines.size() < maxLines) {
      if (remaining.length() <= width) {
        lines.add(remaining);
        break;
      }

      int breakIndex = remaining.lastIndexOf(' ', width);
      if (breakIndex <= 0) {
        breakIndex = width;
      }
      lines.add(remaining.substring(0, breakIndex).stripTrailing());
      remaining = remaining.substring(Math.min(remaining.length(), breakIndex + 1)).stripLeading();
    }

    if (!remaining.isBlank() && lines.size() == maxLines) {
      int lastIndex = lines.size() - 1;
      lines.set(lastIndex, truncate(lines.get(lastIndex), Math.max(4, width)));
    }
    return lines;
  }

  private static String truncate(String value, int maxLength) {
    if (value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength - 3) + "...";
  }

  record FooterSnapshot(
      boolean generationInProgress,
      FocusTarget focusTarget,
      boolean metadataSelectorFocus,
      boolean commandPaletteVisible,
      String modeLabel,
      String generationStateLabel,
      String statusMessage,
      String validationLabel,
      String focusTargetName,
      String activeErrorDetails,
      boolean showErrorDetails,
      String successHint) {
    FooterSnapshot {
      focusTarget = Objects.requireNonNull(focusTarget);
      modeLabel = normalize(modeLabel);
      generationStateLabel = normalize(generationStateLabel);
      statusMessage = normalize(statusMessage);
      validationLabel = normalize(validationLabel);
      focusTargetName = normalize(focusTargetName);
      activeErrorDetails = normalize(activeErrorDetails);
      successHint = normalize(successHint);
    }

    private static String normalize(String value) {
      return value == null ? "" : value;
    }
  }
}
