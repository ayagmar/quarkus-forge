package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FooterLinesComposerTest {
  private final FooterLinesComposer composer = new FooterLinesComposer();

  @Test
  void generationInProgressHintUsesViewportSpecificCopy() {
    FooterSnapshot snapshot = snapshotBuilder().generationInProgress(true).build();

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
    FooterSnapshot snapshot = snapshotBuilder().focusTarget(FocusTarget.EXTENSION_LIST).build();

    List<String> lines = composer.compose(120, snapshot);

    assertThat(lines.getLast()).contains("PgUp/PgDn: category jump");
    assertThat(lines.getLast()).contains("Left/Right or h/l: section hierarchy");
    assertThat(lines.getLast()).contains("v: category filter");
    assertThat(lines.getLast()).contains("X: clear selected");
  }

  @Test
  void extensionSearchHintIncludesEscapeBehavior() {
    FooterSnapshot snapshot = snapshotBuilder().focusTarget(FocusTarget.EXTENSION_SEARCH).build();

    List<String> lines = composer.compose(120, snapshot);

    assertThat(lines.getLast()).contains("Esc: clear filters or return to list");
  }

  @Test
  void extensionListHintIncludesEscapeClearOrQuitBehavior() {
    FooterSnapshot snapshot = snapshotBuilder().focusTarget(FocusTarget.EXTENSION_LIST).build();

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
    FooterSnapshot snapshot = snapshotBuilder().activeErrorDetails("something went wrong").build();

    List<String> lines = composer.compose(120, snapshot);

    assertThat(lines).anyMatch(line -> line.equals("Error: something went wrong"));
  }

  @Test
  void errorLineShowsDetailsHintWhenVerboseDiffers() {
    FooterSnapshot snapshot =
        snapshotBuilder()
            .activeErrorDetails("compact")
            .verboseErrorDetails("compact with stack")
            .build();

    List<String> lines = composer.compose(120, snapshot);

    assertThat(lines).anyMatch(line -> line.contains("Ctrl+E for details"));
  }

  @Test
  void contextualHintLineIsIncludedForFocusedField() {
    FooterSnapshot snapshot = snapshotBuilder().focusTarget(FocusTarget.BUILD_TOOL).build();

    List<String> lines = composer.compose(120, snapshot);

    assertThat(lines).anyMatch(line -> line.startsWith("Hint: Use Left/Right"));
  }

  @Test
  void contextualHintLineIsSuppressedWhileOverlayIsVisible() {
    FooterSnapshot snapshot =
        snapshotBuilder().focusTarget(FocusTarget.BUILD_TOOL).helpOverlayVisible(true).build();

    List<String> lines = composer.compose(120, snapshot);

    assertThat(lines).noneMatch(line -> line.startsWith("Hint:"));
    assertThat(lines.getLast()).isEqualTo("Esc: close help");
  }

  @Test
  void preGeneratePlanLineIsIncludedWhenPresent() {
    FooterSnapshot snapshot =
        snapshotBuilder().preGeneratePlan("/tmp/demo | maven | Java 25 | 3 ext").build();

    List<String> lines = composer.compose(120, snapshot);

    assertThat(lines).anyMatch(line -> line.startsWith("Plan: /tmp/demo | maven"));
  }

  @Test
  void resolvedTargetLineIsIncludedWhenPresent() {
    FooterSnapshot snapshot = snapshotBuilder().resolvedTargetPath("/tmp/demo/forge-app").build();

    List<String> lines = composer.compose(120, snapshot);

    assertThat(lines).contains("Resolved target: /tmp/demo/forge-app");
  }

  @Test
  void focusedValueAndIssueLinesAreIncludedWhenPresent() {
    FooterSnapshot snapshot =
        snapshotBuilder()
            .focusedFieldValue("~/Projects/Quarkus")
            .focusedFieldIssue("must not be blank")
            .build();

    List<String> lines = composer.compose(120, snapshot);

    assertThat(lines).contains("Value: ~/Projects/Quarkus");
    assertThat(lines).contains("Field issue: must not be blank");
  }

  @Test
  void narrowViewportTruncationDoesNotThrowForShortHints() {
    FooterSnapshot snapshot = snapshotBuilder().successHint("1234").preGeneratePlan("abcd").build();

    List<String> lines = composer.compose(3, snapshot);

    assertThat(lines).contains("Nex", "Pla");
    assertThat(lines).allSatisfy(line -> assertThat(line.length()).isGreaterThan(0));
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
    private String preGeneratePlan = "";
    private String resolvedTargetPath = "";
    private String focusedFieldValue = "";
    private String focusedFieldIssue = "";

    FooterSnapshotBuilder generationInProgress(boolean value) {
      generationInProgress = value;
      return this;
    }

    FooterSnapshotBuilder activeErrorDetails(String value) {
      activeErrorDetails = value;
      return this;
    }

    FooterSnapshotBuilder verboseErrorDetails(String value) {
      verboseErrorDetails = value;
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

    FooterSnapshotBuilder helpOverlayVisible(boolean value) {
      helpOverlayVisible = value;
      return this;
    }

    FooterSnapshotBuilder successHint(String value) {
      successHint = value;
      return this;
    }

    FooterSnapshotBuilder preGeneratePlan(String value) {
      preGeneratePlan = value;
      return this;
    }

    FooterSnapshotBuilder resolvedTargetPath(String value) {
      resolvedTargetPath = value;
      return this;
    }

    FooterSnapshotBuilder focusedFieldValue(String value) {
      focusedFieldValue = value;
      return this;
    }

    FooterSnapshotBuilder focusedFieldIssue(String value) {
      focusedFieldIssue = value;
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
          successHint,
          preGeneratePlan,
          resolvedTargetPath,
          focusedFieldValue,
          focusedFieldIssue);
    }
  }
}
