package dev.ayagmar.quarkusforge.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class FooterLinesComposer {
  List<String> compose(int width, FooterSnapshot snapshot) {
    Objects.requireNonNull(snapshot);
    List<String> lines = new ArrayList<>();
    lines.add(statusLine(snapshot));

    if (!snapshot.activeErrorDetails().isBlank()) {
      if (snapshot.showErrorDetails()) {
        lines.add("Error details:");
        String detail = snapshot.activeErrorDetails();
        if (detail.length() > width) {
          detail = detail.substring(0, Math.max(0, width - 3)) + "...";
        }
        lines.add(detail);
      } else {
        lines.add("Error: " + snapshot.activeErrorDetails());
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

  record FooterSnapshot(
      boolean generationInProgress,
      FocusTarget focusTarget,
      boolean metadataSelectorFocus,
      boolean commandPaletteVisible,
      boolean helpOverlayVisible,
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
