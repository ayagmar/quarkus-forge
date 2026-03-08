package dev.ayagmar.quarkusforge.ui;

/** Layers render-only panel/footer snapshots onto the synchronized reducer/read-model state. */
final class UiStateSnapshotMapper {

  record PanelState(ExtensionsPanelSnapshot extensionsPanel, FooterSnapshot footer) {}

  UiRenderModel renderModel(
      UiState reducerState,
      String statusMessage,
      MetadataPanelSnapshot metadataPanel,
      PanelState panelState,
      UiState.GenerationView generation,
      UiState.StartupOverlayView startupOverlay) {
    UiState synchronizedState = reducerState.withStatusMessage(statusMessage);
    return new UiRenderModel(
        synchronizedState,
        metadataPanel,
        panelState.extensionsPanel(),
        panelState.footer(),
        generation,
        startupOverlay);
  }
}
