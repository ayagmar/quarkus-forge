package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FooterLinesComposerTest {
  private final FooterLinesComposer composer = new FooterLinesComposer();

  @Test
  void generationInProgressHintUsesViewportSpecificCopy() {
    FooterLinesComposer.FooterSnapshot snapshot =
        snapshotBuilder().generationInProgress(true).build();

    List<String> narrow = composer.compose(80, snapshot);
    List<String> wide = composer.compose(120, snapshot);

    assertThat(narrow.getFirst()).isEqualTo("Esc: cancel generation | Enter disabled");
    assertThat(wide.getFirst())
        .isEqualTo("Esc: cancel generation | Enter disabled while generation is loading");
  }

  @Test
  void expandedErrorDetailsAreIncludedWhenEnabled() {
    FooterLinesComposer.FooterSnapshot snapshot =
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
  void defaultHintIncludesHelpAndCommandPaletteShortcuts() {
    FooterLinesComposer.FooterSnapshot snapshot = snapshotBuilder().build();

    List<String> lines = composer.compose(120, snapshot);

    assertThat(lines.getFirst()).contains("?: help");
    assertThat(lines.getFirst()).contains("Ctrl+P: commands");
  }

  @Test
  void extensionListHintIncludesSectionJumpShortcut() {
    FooterLinesComposer.FooterSnapshot snapshot =
        snapshotBuilder().focusTarget(FocusTarget.EXTENSION_LIST).build();

    List<String> lines = composer.compose(120, snapshot);

    assertThat(lines.getFirst()).contains("PgUp/PgDn: category jump");
    assertThat(lines.getFirst()).contains("Left/Right or h/l: section hierarchy");
    assertThat(lines.getFirst()).contains("X: clear selected");
  }

  @Test
  void extensionSearchHintIncludesEscapeBehavior() {
    FooterLinesComposer.FooterSnapshot snapshot =
        snapshotBuilder().focusTarget(FocusTarget.EXTENSION_SEARCH).build();

    List<String> lines = composer.compose(120, snapshot);

    assertThat(lines.getFirst()).contains("Esc: clear filters or return to list");
  }

  @Test
  void extensionListHintIncludesEscapeClearOrQuitBehavior() {
    FooterLinesComposer.FooterSnapshot snapshot =
        snapshotBuilder().focusTarget(FocusTarget.EXTENSION_LIST).build();

    List<String> lines = composer.compose(120, snapshot);

    assertThat(lines.getFirst()).contains("Esc: clear filters/quit");
  }

  @Test
  void nextHintIsTruncatedOnNarrowViewports() {
    FooterLinesComposer.FooterSnapshot snapshot =
        snapshotBuilder()
            .successHint("Use generated project path with long nested segments for follow-up steps")
            .build();

    List<String> lines = composer.compose(70, snapshot);

    assertThat(lines).anyMatch(line -> line.startsWith("Next: ") && line.contains("..."));
  }

  private static FooterSnapshotBuilder snapshotBuilder() {
    return new FooterSnapshotBuilder();
  }

  private static final class FooterSnapshotBuilder {
    private boolean generationInProgress;
    private FocusTarget focusTarget = FocusTarget.GROUP_ID;
    private boolean metadataSelectorFocus;
    private boolean commandPaletteVisible;
    private boolean helpOverlayVisible;
    private String modeLabel = "READY";
    private String generationStateLabel = "IDLE";
    private String statusMessage = "Ready";
    private String validationLabel = "OK";
    private String focusTargetName = "groupId";
    private String activeErrorDetails = "";
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

    FooterLinesComposer.FooterSnapshot build() {
      return new FooterLinesComposer.FooterSnapshot(
          generationInProgress,
          focusTarget,
          metadataSelectorFocus,
          commandPaletteVisible,
          helpOverlayVisible,
          modeLabel,
          generationStateLabel,
          statusMessage,
          validationLabel,
          focusTargetName,
          activeErrorDetails,
          showErrorDetails,
          successHint);
    }
  }
}
