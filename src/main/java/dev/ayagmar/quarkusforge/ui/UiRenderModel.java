package dev.ayagmar.quarkusforge.ui;

import java.util.Objects;

record UiRenderModel(
    UiState reducerState,
    SubmitAlertSnapshot submitAlert,
    MetadataPanelSnapshot metadataPanel,
    ExtensionsPanelSnapshot extensionsPanel,
    FooterSnapshot footer,
    UiState.PostGenerationView postGeneration,
    UiState.GenerationView generation,
    UiState.StartupOverlayView startupOverlay) {
  UiRenderModel {
    reducerState = Objects.requireNonNull(reducerState);
    submitAlert = Objects.requireNonNull(submitAlert);
    metadataPanel = Objects.requireNonNull(metadataPanel);
    extensionsPanel = Objects.requireNonNull(extensionsPanel);
    footer = Objects.requireNonNull(footer);
    postGeneration = Objects.requireNonNull(postGeneration);
    generation = Objects.requireNonNull(generation);
    startupOverlay = Objects.requireNonNull(startupOverlay);
  }
}
