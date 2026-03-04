package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ayagmar.quarkusforge.api.ExtensionDto;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class UiStateSnapshotMapperTest {

  @Test
  void snapshotContainsCurrentControllerReadModel() {
    CoreTuiController controller = UiControllerTestHarness.controller();

    UiState state = controller.uiState();

    assertThat(state.focusTarget()).isEqualTo(FocusTarget.GROUP_ID);
    assertThat(state.statusMessage()).isEqualTo("Ready");
    assertThat(state.generation().state()).isEqualTo(CoreTuiController.GenerationState.IDLE);
    assertThat(state.catalogLoad().loading()).isFalse();
    assertThat(state.catalogLoad().sourceLabel()).isEqualTo("snapshot");
    assertThat(state.overlays().commandPaletteVisible()).isFalse();
    assertThat(state.postGeneration().visible()).isFalse();
    assertThat(state.extensions().filteredCount()).isEqualTo(controller.filteredExtensionCount());
    assertThat(state.extensions().searchQuery()).isEmpty();
  }

  @Test
  void snapshotTracksGenerationCatalogAndPostGenerationSlices() {
    UiControllerTestHarness.QueueingScheduler scheduler =
        new UiControllerTestHarness.QueueingScheduler();
    UiControllerTestHarness.ControlledGenerationRunner generationRunner =
        new UiControllerTestHarness.ControlledGenerationRunner();
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), scheduler, Duration.ZERO, generationRunner);
    CompletableFuture<ExtensionCatalogLoadResult> loadFuture = new CompletableFuture<>();

    controller.loadExtensionCatalogAsync(() -> loadFuture);
    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    generationRunner.complete(Path.of("build/generated-project"));
    scheduler.runAll();

    UiState loadingSnapshot = controller.uiState();
    assertThat(loadingSnapshot.generation().state())
        .isEqualTo(CoreTuiController.GenerationState.SUCCESS);
    assertThat(loadingSnapshot.catalogLoad().loading()).isTrue();
    assertThat(loadingSnapshot.overlays().startupOverlayVisible()).isTrue();
    assertThat(loadingSnapshot.postGeneration().visible()).isTrue();
    assertThat(loadingSnapshot.postGeneration().actionLabels()).isNotEmpty();
    assertThat(loadingSnapshot.postGeneration().successHint()).contains("cd ");

    loadFuture.complete(
        ExtensionCatalogLoadResult.live(
            List.of(new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest"))));
    scheduler.runAll();

    UiState loadedSnapshot = controller.uiState();
    assertThat(loadedSnapshot.catalogLoad().loading()).isFalse();
    assertThat(loadedSnapshot.catalogLoad().sourceLabel()).isEqualTo("live");
    assertThat(loadedSnapshot.extensions().filteredCount()).isEqualTo(1);
    assertThat(loadedSnapshot.startupOverlay().statusLines()).isNotEmpty();
  }

  @Test
  void postGenerationActionLabelsAreExposedAsImmutableSnapshot() {
    UiState state =
        new UiStateFixtureBuilder()
            .withPostGenerationView(
                new UiState.PostGenerationView(false, false, 0, 0, List.of("Open", "Quit"), ""))
            .withStartupOverlayView(new UiState.StartupOverlayView(false, List.of("line")))
            .build();

    assertThatThrownBy(() -> state.postGeneration().actionLabels().add("new"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> state.startupOverlay().statusLines().add("new"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private static final class UiStateFixtureBuilder {
    private final UiStateSnapshotMapper mapper = new UiStateSnapshotMapper();
    private final ForgeUiState initialState = UiTestFixtureFactory.defaultForgeUiState();

    private ProjectRequest request = initialState.request();
    private ValidationReport validation = initialState.validation();
    private FocusTarget focusTarget = FocusTarget.GROUP_ID;
    private int commandPaletteSelection = 0;

    private UiStateSnapshotMapper.ValidationState validationState =
        new UiStateSnapshotMapper.ValidationState(validation, false);
    private UiStateSnapshotMapper.SubmissionState submissionState =
        new UiStateSnapshotMapper.SubmissionState(false, false, "Ready", "", "");
    private UiStateSnapshotMapper.PanelState panelState =
        new UiStateSnapshotMapper.PanelState(
            new MetadataPanelSnapshot(
                "",
                false,
                false,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                new MetadataPanelSnapshot.SelectorInfo(0, 0),
                new MetadataPanelSnapshot.SelectorInfo(0, 0),
                new MetadataPanelSnapshot.SelectorInfo(0, 0)),
            new ExtensionsPanelSnapshot(
                false,
                false,
                false,
                false,
                false,
                "",
                "snapshot",
                false,
                false,
                false,
                0,
                "",
                "",
                0,
                0,
                0,
                List.of(),
                List.of(),
                "",
                ""),
            new FooterSnapshot(
                false,
                FocusTarget.GROUP_ID,
                false,
                false,
                false,
                "Ready",
                "",
                "",
                false,
                "",
                "",
                "",
                "",
                ""));
    private UiStateSnapshotMapper.ViewState viewState =
        new UiStateSnapshotMapper.ViewState(
            new UiState.OverlayState(false, false, false, false, false),
            new UiState.GenerationView(CoreTuiController.GenerationState.IDLE, 0.0, "", false),
            new UiState.CatalogLoadView(false, "snapshot", false, ""),
            new UiState.PostGenerationView(false, false, 0, 0, List.of(), ""),
            new UiState.StartupOverlayView(false, List.of()),
            new UiState.ExtensionView(0, 0, 0, false, false, "", "", "", ""));

    UiStateFixtureBuilder withPostGenerationView(UiState.PostGenerationView postGenerationView) {
      viewState =
          new UiStateSnapshotMapper.ViewState(
              viewState.overlays(),
              viewState.generation(),
              viewState.catalogLoad(),
              postGenerationView,
              viewState.startupOverlay(),
              viewState.extensions());
      return this;
    }

    UiStateFixtureBuilder withStartupOverlayView(UiState.StartupOverlayView startupOverlayView) {
      viewState =
          new UiStateSnapshotMapper.ViewState(
              viewState.overlays(),
              viewState.generation(),
              viewState.catalogLoad(),
              viewState.postGeneration(),
              startupOverlayView,
              viewState.extensions());
      return this;
    }

    UiState build() {
      return mapper.map(
          request,
          focusTarget,
          commandPaletteSelection,
          validationState,
          submissionState,
          viewState,
          panelState);
    }
  }
}
