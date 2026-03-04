package dev.ayagmar.quarkusforge.ui;

import java.util.Objects;

record FooterSnapshot(
    boolean generationInProgress,
    FocusTarget focusTarget,
    boolean commandPaletteVisible,
    boolean helpOverlayVisible,
    boolean postGenerationMenuVisible,
    String statusMessage,
    String activeErrorDetails,
    String verboseErrorDetails,
    boolean showErrorDetails,
    String successHint,
    String preGeneratePlan,
    String resolvedTargetPath,
    String focusedFieldValue,
    String focusedFieldIssue) {
  FooterSnapshot {
    focusTarget = Objects.requireNonNull(focusTarget);
    statusMessage = normalize(statusMessage);
    activeErrorDetails = normalize(activeErrorDetails);
    verboseErrorDetails = normalize(verboseErrorDetails);
    successHint = normalize(successHint);
    preGeneratePlan = normalize(preGeneratePlan);
    resolvedTargetPath = normalize(resolvedTargetPath);
    focusedFieldValue = normalize(focusedFieldValue);
    focusedFieldIssue = normalize(focusedFieldIssue);
  }

  private static String normalize(String value) {
    return value == null ? "" : value;
  }
}
