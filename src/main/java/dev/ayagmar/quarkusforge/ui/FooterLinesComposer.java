package dev.ayagmar.quarkusforge.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class FooterLinesComposer {
  private static final int MAX_ERROR_DETAIL_CHARS = 4000;

  List<String> compose(int width, FooterSnapshot snapshot) {
    Objects.requireNonNull(snapshot);
    List<String> lines = new ArrayList<>();
    lines.add(statusLine(snapshot));

    if (!snapshot.activeErrorDetails().isBlank()) {
      boolean hasExtraDetails =
          !snapshot.verboseErrorDetails().isBlank()
              && !snapshot.verboseErrorDetails().equals(snapshot.activeErrorDetails());
      String errorLine =
          "Error: "
              + snapshot.activeErrorDetails()
              + (hasExtraDetails ? " (Ctrl+E for details)" : "");
      lines.add(errorLine);

      if (snapshot.showErrorDetails()) {
        String verbose =
            snapshot.verboseErrorDetails().isBlank()
                ? snapshot.activeErrorDetails()
                : snapshot.verboseErrorDetails();
        lines.add("Error details:");
        for (String detailLine : splitAndCapDetails(verbose)) {
          lines.add(detailLine);
        }
      }
    }

    if (!snapshot.successHint().isBlank()) {
      String nextHint = "Next: " + snapshot.successHint();
      if (nextHint.length() > width) {
        nextHint = nextHint.substring(0, Math.max(6, width - 3)) + "...";
      }
      lines.add(nextHint);
    }

    lines.add(footerHintLine(width, snapshot));
    return lines;
  }

  private static String statusLine(FooterSnapshot snapshot) {
    return "Status: " + snapshot.statusMessage();
  }

  private static List<String> splitAndCapDetails(String raw) {
    String normalized = raw == null ? "" : raw;
    if (normalized.length() > MAX_ERROR_DETAIL_CHARS) {
      normalized = normalized.substring(0, MAX_ERROR_DETAIL_CHARS - 3) + "...";
    }

    // Keep newlines meaningful: split into lines, but avoid blank spam.
    String[] parts = normalized.replace("\r", "").split("\n");
    List<String> lines = new ArrayList<>();
    for (String part : parts) {
      String trimmed = part == null ? "" : part.strip();
      if (trimmed.isBlank()) {
        continue;
      }
      lines.add(trimmed);
    }
    return lines.isEmpty() ? List.of(normalized.strip()) : List.copyOf(lines);
  }

  private static String footerHintLine(int width, FooterSnapshot snapshot) {
    if (snapshot.generationInProgress()) {
      if (width >= 100) {
        return "Esc: cancel generation | Enter disabled while generation is loading";
      }
      return "Esc: cancel generation | Enter disabled";
    }
    if (snapshot.helpOverlayVisible()) {
      return "Esc: close help";
    }
    if (snapshot.postGenerationMenuVisible()) {
      return "Up/Down or j/k: select action | Enter: confirm | Esc: quit";
    }
    if (snapshot.commandPaletteVisible()) {
      return "Up/Down: navigate | Enter: run | 1-9: quick run | Esc: close";
    }
    if (snapshot.focusTarget() == FocusTarget.EXTENSION_LIST) {
      if (width >= 100) {
        return "Up/Down/Home/End or j/k: list nav | Space: toggle | f: fav"
            + " | PgUp/PgDn: category jump | Left/Right or h/l: section hierarchy"
            + " | v: category filter | c/C: close/open | X: clear selected"
            + " | Esc: clear filters/quit | ?: help";
      }
      return "Up/Down or j/k: nav | Space: toggle | PgUp/PgDn: category"
          + " | v: filter | X: clear selected | Esc: clear filters/quit | ?: help";
    }
    if (snapshot.focusTarget() == FocusTarget.EXTENSION_SEARCH) {
      return "Type: search | Down: list | Esc: clear filters or return to list"
          + " | Ctrl+K: favorites | Ctrl+R: reload | ?: help";
    }
    if (snapshot.focusTarget() == FocusTarget.SUBMIT) {
      return "Enter/Alt+G: submit | Tab: focus | j/k: move | Ctrl+E: errors | ?: help | Esc: quit";
    }
    return "Tab/Shift+Tab: focus | Enter/Alt+G: submit | /: search | ?: help | Ctrl+P: commands | Esc: quit";
  }

}
