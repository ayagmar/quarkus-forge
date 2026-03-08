package dev.ayagmar.quarkusforge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ayagmar.quarkusforge.api.MetadataSnapshotLoader;
import dev.ayagmar.quarkusforge.domain.CliPrefill;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import org.junit.jupiter.api.Test;

class StartupStateTest {

  @Test
  void preservesInitialStateAndMetadataSelection() {
    MetadataCompatibilityContext metadataCompatibility =
        MetadataCompatibilityContext.success(MetadataSnapshotLoader.loadDefault());
    ForgeUiState initialState =
        InputResolutionService.resolveInitialState(
            new CliPrefill(
                "com.example", "demo", "1.0.0", "com.example.demo", ".", "", "maven", "21"),
            metadataCompatibility);
    StartupMetadataSelection metadataSelection =
        new StartupMetadataSelection(metadataCompatibility, "live", "loaded");

    StartupState startupState = new StartupState(initialState, metadataSelection);

    assertThat(startupState.initialState()).isSameAs(initialState);
    assertThat(startupState.metadataSelection()).isSameAs(metadataSelection);
  }

  @Test
  void rejectsNullInitialState() {
    MetadataCompatibilityContext metadataCompatibility =
        MetadataCompatibilityContext.success(MetadataSnapshotLoader.loadDefault());
    StartupMetadataSelection metadataSelection =
        new StartupMetadataSelection(metadataCompatibility, "live", "loaded");

    assertThatThrownBy(() -> new StartupState(null, metadataSelection))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejectsNullMetadataSelection() {
    MetadataCompatibilityContext metadataCompatibility =
        MetadataCompatibilityContext.success(MetadataSnapshotLoader.loadDefault());
    ForgeUiState initialState =
        InputResolutionService.resolveInitialState(
            new CliPrefill(
                "com.example", "demo", "1.0.0", "com.example.demo", ".", "", "maven", "21"),
            metadataCompatibility);

    assertThatThrownBy(() -> new StartupState(initialState, null))
        .isInstanceOf(NullPointerException.class);
  }
}
