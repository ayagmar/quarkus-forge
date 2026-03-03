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
    String preGeneratePlan) {
  FooterSnapshot {
    focusTarget = Objects.requireNonNull(focusTarget);
    statusMessage = normalize(statusMessage);
    activeErrorDetails = normalize(activeErrorDetails);
    verboseErrorDetails = normalize(verboseErrorDetails);
    successHint = normalize(successHint);
    preGeneratePlan = normalize(preGeneratePlan);
  }

  private static String normalize(String value) {
    return value == null ? "" : value;
  }
}
