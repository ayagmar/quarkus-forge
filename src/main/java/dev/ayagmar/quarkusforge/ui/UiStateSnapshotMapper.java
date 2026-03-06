package dev.ayagmar.quarkusforge.ui;

import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import java.util.List;

/** Maps controller-owned mutable state into an immutable {@link UiState} snapshot. */
final class UiStateSnapshotMapper {

  record ValidationState(ValidationReport validation, boolean submitBlockedByValidation) {}

  record SubmissionState(
      boolean submitRequested,
      boolean submitBlockedByTargetConflict,
      String statusMessage,
      String errorMessage,
      String verboseErrorDetails,
      boolean showErrorDetails) {}

  record ViewState(
      UiState.OverlayState overlays,
      UiState.GenerationView generation,
      UiState.CatalogLoadView catalogLoad,
      UiState.PostGenerationView postGeneration,
      UiState.StartupOverlayView startupOverlay,
      UiState.ExtensionView extensions) {}

  record PanelState(
      MetadataPanelSnapshot metadataPanel,
      ExtensionsPanelSnapshot extensionsPanel,
      FooterSnapshot footer) {}

  UiState map(
      ProjectRequest request,
      FocusTarget focusTarget,
      int commandPaletteSelection,
      ValidationState validationState,
      SubmissionState submissionState,
      ViewState viewState,
      PanelState panelState) {
    UiState.PostGenerationView postGeneration = viewState.postGeneration();
    UiState.StartupOverlayView startupOverlay = viewState.startupOverlay();
    return new UiState(
        request,
        validationState.validation(),
        focusTarget,
        submissionState.statusMessage(),
        submissionState.errorMessage(),
        submissionState.verboseErrorDetails(),
        submissionState.showErrorDetails(),
        submissionState.submitRequested(),
        validationState.submitBlockedByValidation(),
        submissionState.submitBlockedByTargetConflict(),
        commandPaletteSelection,
        panelState.metadataPanel(),
        panelState.extensionsPanel(),
        panelState.footer(),
        viewState.overlays(),
        viewState.generation(),
        viewState.catalogLoad(),
        new UiState.PostGenerationView(
            postGeneration.visible(),
            postGeneration.githubVisibilityVisible(),
            postGeneration.actionSelection(),
            postGeneration.githubVisibilitySelection(),
            List.copyOf(postGeneration.actions()),
            postGeneration.lastGeneratedProjectPath(),
            postGeneration.lastGeneratedNextCommand(),
            postGeneration.exitPlan()),
        new UiState.StartupOverlayView(
            startupOverlay.visible(), List.copyOf(startupOverlay.statusLines())),
        viewState.extensions());
  }
}
