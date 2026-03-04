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
      lines.add(truncateForWidth("Next: " + snapshot.successHint(), width));
    }

    if (!snapshot.preGeneratePlan().isBlank()) {
      lines.add(truncateForWidth("Plan: " + snapshot.preGeneratePlan(), width));
    }

    if (!snapshot.resolvedTargetPath().isBlank()) {
      lines.add("Resolved target: " + snapshot.resolvedTargetPath());
    }

    if (!snapshot.focusedFieldValue().isBlank()) {
      lines.add("Value: " + snapshot.focusedFieldValue());
    }

    if (!snapshot.focusedFieldIssue().isBlank()) {
      lines.add("Field issue: " + snapshot.focusedFieldIssue());
    }

    String contextualHint = contextualFocusHint(snapshot);
    if (!contextualHint.isBlank()) {
      lines.add("Hint: " + contextualHint);
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

  private static String truncateForWidth(String text, int width) {
    if (width <= 0 || text.isEmpty()) {
      return "";
    }
    if (text.length() <= width) {
      return text;
    }
    if (width <= 3) {
      return text.substring(0, width);
    }
    return text.substring(0, width - 3) + "...";
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
            + " | Alt+S: selected-only | v: category filter | Ctrl+Y: preset filter"
            + " | c/C: close/open | X: clear selected"
            + " | Esc: clear filters/quit | ?: help";
      }
      return "Up/Down or j/k: nav | Space: toggle | PgUp/PgDn: category"
          + " | Alt+S: selected | v: category | Ctrl+Y: preset | X: clear selected"
          + " | Esc: clear filters/quit | ?: help";
    }
    if (snapshot.focusTarget() == FocusTarget.EXTENSION_SEARCH) {
      return "Type: search | Down: list | Esc: clear filters or return to list"
          + " | Ctrl+K: favorites | Alt+S: selected | Ctrl+Y: presets | Ctrl+R: reload | ?: help";
    }
    if (snapshot.focusTarget() == FocusTarget.SUBMIT) {
      return "Enter/Alt+G: submit | Alt+N/Alt+P: invalid fields | j/k: move"
          + " | Ctrl+E: errors | ?: help | Esc: quit";
    }
    return "Tab/Shift+Tab: focus | Enter/Alt+G: submit | Alt+N/Alt+P: invalid fields"
        + " | /: search | ?: help | Ctrl+P: commands | Esc: quit";
  }

  private static String contextualFocusHint(FooterSnapshot snapshot) {
    if (snapshot.generationInProgress()) {
      return "Generation is running; wait or press Esc to cancel.";
    }
    if (snapshot.helpOverlayVisible()
        || snapshot.commandPaletteVisible()
        || snapshot.postGenerationMenuVisible()) {
      return "";
    }
    return switch (snapshot.focusTarget()) {
      case GROUP_ID -> "Use a reverse-domain id, e.g. com.acme.";
      case ARTIFACT_ID -> "Lowercase project id; becomes the generated folder name.";
      case VERSION -> "Project semantic version, e.g. 1.0.0-SNAPSHOT.";
      case PACKAGE_NAME -> "Base Java package for generated source files.";
      case OUTPUT_DIR -> "Parent directory where the project folder will be created.";
      case PLATFORM_STREAM, BUILD_TOOL, JAVA_VERSION ->
          "Use Left/Right (or h/l) to change, Home/End for first/last option.";
      case EXTENSION_SEARCH -> "Type to filter extensions; Esc clears filters in steps.";
      case EXTENSION_LIST -> "Space toggles extension; f favorites; x clears all selections.";
      case SUBMIT -> "Press Enter to generate; invalid fields will be focused automatically.";
    };
  }
}
