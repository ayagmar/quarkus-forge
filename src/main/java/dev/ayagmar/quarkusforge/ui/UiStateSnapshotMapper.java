package dev.ayagmar.quarkusforge.ui;

/** Layers render-only panel/footer snapshots onto the synchronized reducer/read-model state. */
final class UiStateSnapshotMapper {

  record PanelState(ExtensionsPanelSnapshot extensionsPanel, FooterSnapshot footer) {}

  UiRenderModel renderModel(
      UiState reducerState,
      String statusMessage,
      SubmitAlertSnapshot submitAlert,
      MetadataPanelSnapshot metadataPanel,
      PanelState panelState,
      UiState.PostGenerationView postGeneration,
      UiState.GenerationView generation,
      UiState.StartupOverlayView startupOverlay) {
    UiState synchronizedState = reducerState.withStatusMessage(statusMessage);
    return new UiRenderModel(
        synchronizedState,
        submitAlert,
        metadataPanel,
        panelState.extensionsPanel(),
        panelState.footer(),
        postGeneration,
        generation,
        startupOverlay);
  }
}
