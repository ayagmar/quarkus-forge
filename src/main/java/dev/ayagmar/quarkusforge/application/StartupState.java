package dev.ayagmar.quarkusforge.application;

import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import java.util.Objects;

public record StartupState(ForgeUiState initialState, StartupMetadataSelection metadataSelection) {
  public StartupState {
    initialState = Objects.requireNonNull(initialState);
    metadataSelection = Objects.requireNonNull(metadataSelection);
  }
}
