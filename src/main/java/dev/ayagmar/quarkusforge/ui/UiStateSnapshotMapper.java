package dev.ayagmar.quarkusforge.ui;

/** Layers render-only panel/footer snapshots onto the synchronized reducer/read-model state. */
final class UiStateSnapshotMapper {

  record PanelState(ExtensionsPanelSnapshot extensionsPanel, FooterSnapshot footer) {}

  UiRenderModel renderModel(UiState reducerState, String statusMessage, PanelState panelState) {
    UiState synchronizedState = reducerState.withStatusMessage(statusMessage);
    return new UiRenderModel(
        synchronizedState,
        synchronizedState.metadataPanel(),
        panelState.extensionsPanel(),
        panelState.footer(),
        synchronizedState.generation(),
        synchronizedState.startupOverlay());
  }

  UiState map(UiState reducerState, String statusMessage, PanelState panelState) {
    return renderModel(reducerState, statusMessage, panelState).snapshotState();
  }
}
