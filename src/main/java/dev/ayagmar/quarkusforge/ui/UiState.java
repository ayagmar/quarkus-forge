package dev.ayagmar.quarkusforge.ui;

import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import java.util.List;

/**
 * Immutable UI read-model snapshot used by reducer logic and renderer orchestration.
 *
 * <p>Contains only serializable/view-model state and avoids direct mutable widget ownership.
 */
record UiState(
    ProjectRequest request,
    ValidationReport validation,
    FocusTarget focusTarget,
    String statusMessage,
    String errorMessage,
    String verboseErrorDetails,
    boolean submitRequested,
    boolean submitBlockedByValidation,
    boolean submitBlockedByTargetConflict,
    int commandPaletteSelection,
    MetadataPanelSnapshot metadataPanel,
    ExtensionsPanelSnapshot extensionsPanel,
    FooterSnapshot footer,
    OverlayState overlays,
    GenerationView generation,
    CatalogLoadView catalogLoad,
    PostGenerationView postGeneration,
    StartupOverlayView startupOverlay,
    ExtensionView extensions) {

  UiState withStatusAndError(String nextStatusMessage, String nextErrorMessage) {
    return withFeedback(nextStatusMessage, nextErrorMessage, verboseErrorDetails);
  }

  UiState withFeedback(
      String nextStatusMessage, String nextErrorMessage, String nextVerboseErrorDetails) {
    return new UiState(
        request,
        validation,
        focusTarget,
        nextStatusMessage,
        nextErrorMessage,
        nextVerboseErrorDetails,
        submitRequested,
        submitBlockedByValidation,
        submitBlockedByTargetConflict,
        commandPaletteSelection,
        metadataPanel,
        extensionsPanel,
        footer,
        overlays,
        generation,
        catalogLoad,
        postGeneration,
        startupOverlay,
        extensions);
  }

  record OverlayState(
      boolean generationVisible,
      boolean commandPaletteVisible,
      boolean helpOverlayVisible,
      boolean postGenerationVisible,
      boolean startupOverlayVisible) {}

  record GenerationView(
      CoreTuiController.GenerationState state,
      double progressRatio,
      String progressPhase,
      boolean cancellationRequested) {}

  record CatalogLoadView(boolean loading, String sourceLabel, boolean stale, String errorMessage) {}

  record PostGenerationView(
      boolean visible,
      boolean githubVisibilityVisible,
      int actionSelection,
      int githubVisibilitySelection,
      List<String> actionLabels,
      String successHint) {}

  record StartupOverlayView(boolean visible, List<String> statusLines) {}

  record ExtensionView(
      int filteredCount,
      int totalCount,
      int selectedCount,
      boolean favoritesOnlyEnabled,
      boolean selectedOnlyEnabled,
      String activePresetFilterName,
      String activeCategoryFilterTitle,
      String searchQuery,
      String focusedExtensionId) {}
}
