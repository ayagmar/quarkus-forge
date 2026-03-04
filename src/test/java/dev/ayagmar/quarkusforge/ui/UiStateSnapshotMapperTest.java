package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ayagmar.quarkusforge.api.ExtensionDto;
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
    UiStateSnapshotMapper mapper = new UiStateSnapshotMapper();
    UiState state =
        mapper.map(
            UiTestFixtureFactory.defaultForgeUiState().request(),
            UiTestFixtureFactory.defaultForgeUiState().validation(),
            FocusTarget.GROUP_ID,
            "Ready",
            "",
            "",
            false,
            false,
            false,
            0,
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
                ""),
            new UiState.OverlayState(false, false, false, false, false),
            new UiState.GenerationView(CoreTuiController.GenerationState.IDLE, 0.0, "", false),
            new UiState.CatalogLoadView(false, "snapshot", false, ""),
            new UiState.PostGenerationView(false, false, 0, 0, List.of("Open", "Quit"), ""),
            new UiState.StartupOverlayView(false, List.of("line")),
            new UiState.ExtensionView(0, 0, 0, false, false, "", "", "", ""));

    assertThatThrownBy(() -> state.postGeneration().actionLabels().add("new"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> state.startupOverlay().statusLines().add("new"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
