package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FooterLinesComposerTest {
  private final FooterLinesComposer composer = new FooterLinesComposer();

  @Test
  void generationInProgressHintUsesViewportSpecificCopy() {
    FooterSnapshot snapshot =
        snapshotBuilder().generationInProgress(true).build();

    List<String> narrow = composer.compose(80, snapshot);
    List<String> wide = composer.compose(120, snapshot);

    assertThat(narrow.getLast()).isEqualTo("Esc: cancel generation | Enter disabled");
    assertThat(wide.getLast())
        .isEqualTo("Esc: cancel generation | Enter disabled while generation is loading");
  }

  @Test
  void expandedErrorDetailsAreIncludedWhenEnabled() {
    FooterSnapshot snapshot =
        snapshotBuilder()
            .activeErrorDetails(
                "live metadata failed because catalog endpoint did not return expected JSON payload")
            .showErrorDetails(true)
            .build();

    List<String> lines = composer.compose(90, snapshot);

    assertThat(lines).contains("Error details:");
    assertThat(String.join("\n", lines)).contains("expected JSON payload");
  }

  @Test
  void expandedErrorDetailsAreNotViewportTruncated() {
    FooterSnapshot snapshot =
        snapshotBuilder()
            .activeErrorDetails("0123456789abcdefghijklmnopqrstuvwxyz")
            .showErrorDetails(true)
            .build();

    List<String> lines = composer.compose(16, snapshot);

    assertThat(lines).contains("Error details:");
    assertThat(String.join("\n", lines)).contains("0123456789abcdefghijklmnopqrstuvwxyz");
  }

  @Test
  void defaultHintIncludesHelpAndCommandPaletteShortcuts() {
    FooterSnapshot snapshot = snapshotBuilder().build();

    List<String> lines = composer.compose(120, snapshot);

    assertThat(lines.getLast()).contains("Enter/Alt+G: submit");
    assertThat(lines.getLast()).contains("?: help");
    assertThat(lines.getLast()).contains("Ctrl+P: commands");
  }

  @Test
  void extensionListHintIncludesSectionJumpShortcut() {
    FooterSnapshot snapshot =
        snapshotBuilder().focusTarget(FocusTarget.EXTENSION_LIST).build();

    List<String> lines = composer.compose(120, snapshot);

    assertThat(lines.getLast()).contains("PgUp/PgDn: category jump");
    assertThat(lines.getLast()).contains("Left/Right or h/l: section hierarchy");
    assertThat(lines.getLast()).contains("v: category filter");
    assertThat(lines.getLast()).contains("X: clear selected");
  }

  @Test
  void extensionSearchHintIncludesEscapeBehavior() {
    FooterSnapshot snapshot =
        snapshotBuilder().focusTarget(FocusTarget.EXTENSION_SEARCH).build();

    List<String> lines = composer.compose(120, snapshot);

    assertThat(lines.getLast()).contains("Esc: clear filters or return to list");
  }

  @Test
  void extensionListHintIncludesEscapeClearOrQuitBehavior() {
    FooterSnapshot snapshot =
        snapshotBuilder().focusTarget(FocusTarget.EXTENSION_LIST).build();

    List<String> lines = composer.compose(120, snapshot);

    assertThat(lines.getLast()).contains("Esc: clear filters/quit");
  }

  @Test
  void nextHintIsTruncatedOnNarrowViewports() {
    FooterSnapshot snapshot =
        snapshotBuilder()
            .successHint("Use generated project path with long nested segments for follow-up steps")
            .build();

    List<String> lines = composer.compose(70, snapshot);

    assertThat(lines).anyMatch(line -> line.startsWith("Next: ") && line.contains("..."));
  }

  @Test
  void statusLineIsAlwaysPresent() {
    FooterSnapshot snapshot = snapshotBuilder().build();

    List<String> lines = composer.compose(120, snapshot);

    assertThat(lines.getFirst()).startsWith("Status: ");
    assertThat(lines.getFirst()).contains("Ready");
  }

  @Test
  void errorLineShownWhenActiveErrorExists() {
    FooterSnapshot snapshot =
        snapshotBuilder().activeErrorDetails("something went wrong").build();

    List<String> lines = composer.compose(120, snapshot);

    assertThat(lines).anyMatch(line -> line.equals("Error: something went wrong"));
  }

  private static FooterSnapshotBuilder snapshotBuilder() {
    return new FooterSnapshotBuilder();
  }

  private static final class FooterSnapshotBuilder {
    private boolean generationInProgress;
    private FocusTarget focusTarget = FocusTarget.GROUP_ID;
    private boolean commandPaletteVisible;
    private boolean helpOverlayVisible;
    private boolean postGenerationMenuVisible;
    private String statusMessage = "Ready";
    private String activeErrorDetails = "";
    private String verboseErrorDetails = "";
    private boolean showErrorDetails;
    private String successHint = "";

    FooterSnapshotBuilder generationInProgress(boolean value) {
      generationInProgress = value;
      return this;
    }

    FooterSnapshotBuilder activeErrorDetails(String value) {
      activeErrorDetails = value;
      return this;
    }

    FooterSnapshotBuilder focusTarget(FocusTarget value) {
      focusTarget = value;
      return this;
    }

    FooterSnapshotBuilder showErrorDetails(boolean value) {
      showErrorDetails = value;
      return this;
    }

    FooterSnapshotBuilder successHint(String value) {
      successHint = value;
      return this;
    }

    FooterSnapshot build() {
      return new FooterSnapshot(
          generationInProgress,
          focusTarget,
          commandPaletteVisible,
          helpOverlayVisible,
          postGenerationMenuVisible,
          statusMessage,
          activeErrorDetails,
          verboseErrorDetails,
          showErrorDetails,
          successHint);
    }
  }
}
