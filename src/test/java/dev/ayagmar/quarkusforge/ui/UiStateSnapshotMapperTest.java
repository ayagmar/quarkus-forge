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
  void controllerUiStateExposesReducerOwnedSlicesOnly() {
    CoreTuiController controller = UiControllerTestHarness.controller();

    UiState state = controller.uiState();

    assertThat(state.focusTarget()).isEqualTo(FocusTarget.GROUP_ID);
    assertThat(state.statusMessage()).isEqualTo("Ready");
    assertThat(state.catalogLoad().loading()).isFalse();
    assertThat(state.catalogLoad().sourceLabel()).isEqualTo("snapshot");
    assertThat(state.overlays().commandPaletteVisible()).isFalse();
    assertThat(state.postGeneration().visible()).isFalse();
    assertThat(state.extensions().filteredCount()).isEqualTo(controller.filteredExtensionCount());
    assertThat(state.extensions().searchQuery()).isEmpty();
    assertThat(state.extensions().selection()).isNotNull();
  }

  @Test
  void controllerRenderModelTracksGenerationCatalogAndPostGenerationSlices() {
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

    UiRenderModel loadingRenderModel = controller.renderModel();
    assertThat(loadingRenderModel.generation().state()).isEqualTo(GenerationState.SUCCESS);
    assertThat(loadingRenderModel.reducerState().catalogLoad().loading()).isTrue();
    assertThat(loadingRenderModel.reducerState().overlays().startupOverlayVisible()).isTrue();
    assertThat(loadingRenderModel.reducerState().postGeneration().visible()).isTrue();
    assertThat(loadingRenderModel.reducerState().postGeneration().actionLabels()).isNotEmpty();
    assertThat(loadingRenderModel.reducerState().postGeneration().successHint()).contains("cd ");

    loadFuture.complete(
        ExtensionCatalogLoadResult.live(
            List.of(new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest"))));
    scheduler.runAll();

    UiRenderModel loadedRenderModel = controller.renderModel();
    assertThat(loadedRenderModel.reducerState().catalogLoad().loading()).isFalse();
    assertThat(loadedRenderModel.reducerState().catalogLoad().sourceLabel()).isEqualTo("live");
    assertThat(loadedRenderModel.reducerState().extensions().filteredCount()).isEqualTo(1);
    assertThat(loadedRenderModel.startupOverlay().statusLines()).isNotEmpty();
  }

  @Test
  void reducerAndRenderSnapshotsExposeImmutableLists() {
    UiState reducerState =
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
            .withStartupOverlayVisible(true)
            .buildReducerState();
    UiRenderModel renderModel =
        new UiStateFixtureBuilder().withStartupOverlayVisible(true).renderModel();

    assertThatThrownBy(() -> reducerState.postGeneration().actionLabels().add("new"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> renderModel.startupOverlay().statusLines().add("new"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void renderModelKeepsReducerOwnedSlicesAndOverlaysRenderPanels() {
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
    UiStateFixtureBuilder fixture =
        new UiStateFixtureBuilder()
            .withStartupOverlayVisible(true)
            .withStartupOverlayStatusLines(List.of("runtime line"))
            .withPanelState(
                new UiStateSnapshotMapper.PanelState(
                    new ExtensionsPanelSnapshot(
                        true, true, false, false, false, "", "live", false, false, false, 0, "", "",
                        0, 0, 0, List.of(), List.of(), "", ""),
                    footerSnapshot));

    UiRenderModel renderModel = fixture.renderModel();

    assertThat(renderModel.reducerState().overlays().startupOverlayVisible()).isTrue();
    assertThat(renderModel.startupOverlay().visible()).isTrue();
    assertThat(renderModel.startupOverlay().statusLines()).containsExactly("runtime line");
    assertThat(renderModel.generation().state()).isEqualTo(GenerationState.IDLE);
    assertThat(renderModel.reducerState().extensions().searchQuery()).isEmpty();
    assertThat(renderModel.footer()).isEqualTo(footerSnapshot);
    assertThat(renderModel.reducerState().statusMessage()).isEqualTo("Ready");
  }

  @Test
  void renderModelCarriesRenderOnlySlicesSeparately() {
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
    UiStateFixtureBuilder fixture =
        new UiStateFixtureBuilder()
            .withPanelState(
                new UiStateSnapshotMapper.PanelState(
                    new ExtensionsPanelSnapshot(
                        true, true, false, false, false, "", "live", false, false, false, 0, "", "",
                        0, 0, 0, List.of(), List.of(), "", ""),
                    footerSnapshot));

    UiRenderModel renderModel = fixture.renderModel();

    assertThat(renderModel.reducerState().statusMessage()).isEqualTo("Ready");
    assertThat(renderModel.metadataPanel()).isEqualTo(fixture.metadataPanel());
    assertThat(renderModel.extensionsPanel()).isEqualTo(fixture.panelState().extensionsPanel());
    assertThat(renderModel.footer()).isEqualTo(footerSnapshot);
    assertThat(renderModel.generation()).isEqualTo(fixture.generation());
    assertThat(renderModel.startupOverlay()).isEqualTo(fixture.startupOverlay());
  }

  private static final class UiStateFixtureBuilder {
    private final UiStateSnapshotMapper mapper = new UiStateSnapshotMapper();
    private final ForgeUiState initialState = UiTestFixtureFactory.defaultForgeUiState();
    private final MetadataPanelSnapshot metadataPanel =
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
            "",
            MetadataPanelSnapshot.SelectorInfo.EMPTY,
            MetadataPanelSnapshot.SelectorInfo.EMPTY,
            MetadataPanelSnapshot.SelectorInfo.EMPTY);

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
            new UiState.OverlayState(false, false, false, false),
            new UiState.CatalogLoadView(CatalogLoadState.initial()),
            new UiState.PostGenerationView(false, false, 0, 0, List.of(), null, "", null),
            UiState.ExtensionView.snapshot(0, 0, 0, false, false, "", "", "", "", true, false));
    private UiState.GenerationView generation =
        new UiState.GenerationView(GenerationState.IDLE, 0.0, "", false);
    private UiState.StartupOverlayView startupOverlay =
        new UiState.StartupOverlayView(false, List.of());

    UiStateFixtureBuilder withPostGenerationView(UiState.PostGenerationView postGenerationView) {
      reducerState = reducerState.withPostGeneration(postGenerationView);
      return this;
    }

    UiStateFixtureBuilder withPanelState(UiStateSnapshotMapper.PanelState nextPanelState) {
      panelState = nextPanelState;
      return this;
    }

    UiStateFixtureBuilder withStartupOverlayVisible(boolean visible) {
      reducerState = reducerState.withStartupOverlayVisibility(visible);
      startupOverlay = new UiState.StartupOverlayView(visible, startupOverlay.statusLines());
      return this;
    }

    UiStateFixtureBuilder withStartupOverlayStatusLines(List<String> statusLines) {
      startupOverlay = new UiState.StartupOverlayView(startupOverlay.visible(), statusLines);
      return this;
    }

    UiState buildReducerState() {
      return reducerState;
    }

    UiRenderModel renderModel() {
      return mapper.renderModel(
          reducerState,
          reducerState.statusMessage(),
          SubmitAlertSnapshot.HIDDEN,
          metadataPanel,
          panelState,
          generation,
          startupOverlay);
    }

    MetadataPanelSnapshot metadataPanel() {
      return metadataPanel;
    }

    UiStateSnapshotMapper.PanelState panelState() {
      return panelState;
    }

    UiState.GenerationView generation() {
      return generation;
    }

    UiState.StartupOverlayView startupOverlay() {
      return startupOverlay;
    }
  }
}
