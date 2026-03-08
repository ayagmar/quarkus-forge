package dev.ayagmar.quarkusforge.ui;

import java.util.Objects;

record UiRenderModel(
    UiState reducerState,
    MetadataPanelSnapshot metadataPanel,
    ExtensionsPanelSnapshot extensionsPanel,
    FooterSnapshot footer,
    UiState.GenerationView generation,
    UiState.StartupOverlayView startupOverlay) {
  UiRenderModel {
    reducerState = Objects.requireNonNull(reducerState);
    metadataPanel = Objects.requireNonNull(metadataPanel);
    extensionsPanel = Objects.requireNonNull(extensionsPanel);
    footer = Objects.requireNonNull(footer);
    generation = Objects.requireNonNull(generation);
    startupOverlay = Objects.requireNonNull(startupOverlay);
  }
}
