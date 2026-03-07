package dev.ayagmar.quarkusforge.ui;

/** Layers render-only panel/footer snapshots onto the synchronized reducer/read-model state. */
final class UiStateSnapshotMapper {

  record PanelState(ExtensionsPanelSnapshot extensionsPanel, FooterSnapshot footer) {}

  UiState map(UiState reducerState, String statusMessage, PanelState panelState) {
    return reducerState.withRenderSnapshot(
        statusMessage, panelState.extensionsPanel(), panelState.footer());
  }
}
