package dev.ayagmar.quarkusforge.ui;

import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import java.util.List;

final class UiStateSnapshotMapper {

  UiState map(
      ProjectRequest request,
      ValidationReport validation,
      FocusTarget focusTarget,
      String statusMessage,
      String errorMessage,
      String verboseErrorDetails,
      boolean submitRequested,
      boolean submitBlockedByValidation,
      boolean submitBlockedByTargetConflict,
      MetadataPanelSnapshot metadataPanel,
      ExtensionsPanelSnapshot extensionsPanel,
      FooterSnapshot footer,
      UiState.OverlayState overlays,
      UiState.GenerationView generation,
      UiState.CatalogLoadView catalogLoad,
      UiState.PostGenerationView postGeneration,
      UiState.StartupOverlayView startupOverlay,
      UiState.ExtensionView extensions) {
    return new UiState(
        request,
        validation,
        focusTarget,
        statusMessage,
        errorMessage,
        verboseErrorDetails,
        submitRequested,
        submitBlockedByValidation,
        submitBlockedByTargetConflict,
        metadataPanel,
        extensionsPanel,
        footer,
        overlays,
        generation,
        catalogLoad,
        new UiState.PostGenerationView(
            postGeneration.visible(),
            postGeneration.githubVisibilityVisible(),
            postGeneration.actionSelection(),
            postGeneration.githubVisibilitySelection(),
            List.copyOf(postGeneration.actionLabels()),
            postGeneration.successHint()),
        new UiState.StartupOverlayView(
            startupOverlay.visible(), List.copyOf(startupOverlay.statusLines())),
        extensions);
  }
}
