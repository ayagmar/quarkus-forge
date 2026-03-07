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
                new UiState.PostGenerationView(
                    false,
                    false,
                    0,
                    0,
                    List.of(
                        new UiTextConstants.PostGenerationAction(
                            "Open", PostGenerationExitAction.OPEN_TERMINAL),
                        new UiTextConstants.PostGenerationAction(
                            "Quit", PostGenerationExitAction.QUIT)),
                    null,
                    "",
                    null))
            .withStartupOverlayView(new UiState.StartupOverlayView(false, List.of("line")))
            .build();

    assertThatThrownBy(() -> state.postGeneration().actionLabels().add("new"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> state.startupOverlay().statusLines().add("new"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void mapperKeepsReducerOwnedRuntimeSlicesAndOnlyOverlaysRenderPanels() {
    FooterSnapshot footerSnapshot =
        new FooterSnapshot(
            true,
            FocusTarget.SUBMIT,
            false,
            false,
            false,
            "Rendering",
            "",
            "",
            false,
            "",
            "",
            "",
            "",
            "");
    UiState state =
        new UiStateFixtureBuilder()
            .withStartupOverlayView(new UiState.StartupOverlayView(true, List.of("runtime line")))
            .withPanelState(
                new UiStateSnapshotMapper.PanelState(
                    new ExtensionsPanelSnapshot(
                        true, true, false, false, false, "", "live", false, false, false, 0, "", "",
                        0, 0, 0, List.of(), List.of(), "", ""),
                    footerSnapshot))
            .build();

    assertThat(state.startupOverlay().visible()).isTrue();
    assertThat(state.startupOverlay().statusLines()).containsExactly("runtime line");
    assertThat(state.generation().state()).isEqualTo(CoreTuiController.GenerationState.IDLE);
    assertThat(state.extensions().searchQuery()).isEmpty();
    assertThat(state.footer()).isEqualTo(footerSnapshot);
    assertThat(state.statusMessage()).isEqualTo("Ready");
  }

  private static final class UiStateFixtureBuilder {
    private final UiStateSnapshotMapper mapper = new UiStateSnapshotMapper();
    private final ForgeUiState initialState = UiTestFixtureFactory.defaultForgeUiState();
    private final MetadataPanelSnapshot metadataPanelSnapshot =
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
            new MetadataPanelSnapshot.SelectorInfo(0, 0));

    private ProjectRequest request = initialState.request();
    private ValidationReport validation = initialState.validation();

    private UiStateSnapshotMapper.PanelState panelState =
        new UiStateSnapshotMapper.PanelState(
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
    private UiState reducerState =
        new UiState(
            request,
            validation,
            FocusTarget.GROUP_ID,
            "Ready",
            "",
            "",
            false,
            false,
            false,
            false,
            0,
            metadataPanelSnapshot,
            panelState.extensionsPanel(),
            panelState.footer(),
            new UiState.OverlayState(false, false, false, false, false),
            new UiState.GenerationView(CoreTuiController.GenerationState.IDLE, 0.0, "", false),
            new UiState.CatalogLoadView(CatalogLoadState.initial()),
            new UiState.PostGenerationView(false, false, 0, 0, List.of(), null, "", null),
            new UiState.StartupOverlayView(false, List.of()),
            new UiState.ExtensionView(0, 0, 0, false, false, "", "", "", ""));

    UiStateFixtureBuilder withPostGenerationView(UiState.PostGenerationView postGenerationView) {
      reducerState = reducerState.withPostGeneration(postGenerationView);
      return this;
    }

    UiStateFixtureBuilder withStartupOverlayView(UiState.StartupOverlayView startupOverlayView) {
      reducerState = reducerState.withStartupOverlay(startupOverlayView);
      return this;
    }

    UiStateFixtureBuilder withPanelState(UiStateSnapshotMapper.PanelState nextPanelState) {
      panelState = nextPanelState;
      return this;
    }

    UiState build() {
      return mapper.map(reducerState, reducerState.statusMessage(), panelState);
    }
  }
}
