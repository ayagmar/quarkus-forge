package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.persistence.ExtensionFavoritesStore;
import dev.tamboui.widgets.input.TextInputState;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class UiRenderStateAssemblerTest {
  private static final MetadataPanelSnapshot EMPTY_METADATA_PANEL =
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
          MetadataPanelSnapshot.SelectorInfo.EMPTY,
          MetadataPanelSnapshot.SelectorInfo.EMPTY,
          MetadataPanelSnapshot.SelectorInfo.EMPTY);
  private static final ExtensionsPanelSnapshot EMPTY_EXTENSIONS_PANEL =
      new ExtensionsPanelSnapshot(
          false, false, false, false, false, "", "", false, false, false, 0, "", "", 0, 0, 0,
          List.of(), List.of(), "", "");
  private static final FooterSnapshot EMPTY_FOOTER =
      new FooterSnapshot(
          false, FocusTarget.GROUP_ID, false, false, false, "", "", "", false, "", "", "", "", "");

  @Test
  void uiStateCombinesReducerAndRuntimeRenderSlices() {
    ForgeUiState initialState = UiTestFixtureFactory.defaultForgeUiState();
    RenderFixture fixture = RenderFixture.create(initialState);

    UiState snapshot =
        fixture.assembler.uiState(
            fixture.reducerState, "Ready", initialState.metadataCompatibility(), false);

    assertThat(snapshot.metadataPanel().title()).isEqualTo("Project Metadata");
    assertThat(snapshot.metadataPanel().focused()).isTrue();
    assertThat(snapshot.metadataPanel().groupId()).isEqualTo(initialState.request().groupId());
    assertThat(snapshot.extensionsPanel().catalogSource()).isEqualTo("snapshot");
    assertThat(snapshot.extensionsPanel().filteredExtensionCount()).isEqualTo(7);
    assertThat(snapshot.footer().statusMessage()).isEqualTo("Ready");
    assertThat(snapshot.footer().preGeneratePlan()).contains("forge-app");
    assertThat(snapshot.startupOverlay().statusLines()).isNotEmpty();
  }

  @Test
  void renderContextUsesCurrentSelectorsSearchAndSelectionLookups() {
    ForgeUiState initialState = UiTestFixtureFactory.defaultForgeUiState();
    RenderFixture fixture = RenderFixture.create(initialState);
    fixture.inputStates.get(FocusTarget.EXTENSION_SEARCH).setText("rest");
    fixture.navigation.select("io.quarkus:quarkus-rest");
    fixture.preferences.toggleFavorite("io.quarkus:quarkus-rest");

    CoreUiRenderAdapter.RenderContext context =
        fixture.assembler.renderContext(fixture.reducerState, initialState.metadataCompatibility());

    assertThat(context.extensionSearchState().text()).isEqualTo("rest");
    assertThat(context.metadataFields().selectorOptions(FocusTarget.BUILD_TOOL)).contains("maven");
    assertThat(context.selectedLookup().matches("io.quarkus:quarkus-rest")).isTrue();
    assertThat(context.favoriteLookup().matches("io.quarkus:quarkus-rest")).isTrue();
  }

  private record RenderFixture(
      UiRenderStateAssembler assembler,
      UiState reducerState,
      EnumMap<FocusTarget, TextInputState> inputStates,
      ExtensionCatalogNavigation navigation,
      ExtensionCatalogPreferences preferences) {
    private static RenderFixture create(ForgeUiState initialState) {
      EnumMap<FocusTarget, TextInputState> inputStates = initialInputStates(initialState);
      MetadataSelectorManager metadataSelectors = new MetadataSelectorManager();
      metadataSelectors.sync(
          initialState.metadataCompatibility().metadataSnapshot(),
          initialState.request().platformStream(),
          initialState.request().buildTool(),
          initialState.request().javaVersion());
      ExtensionCatalogPreferences preferences =
          new ExtensionCatalogPreferences(ExtensionFavoritesStore.inMemory(), Runnable::run);
      ExtensionCatalogNavigation navigation = new ExtensionCatalogNavigation();
      ExtensionCatalogProjection projection =
          new ExtensionCatalogProjection(UiScheduler.immediate(), Duration.ZERO, "");
      projection.initialize(navigation, preferences);
      UiRenderStateAssembler assembler =
          new UiRenderStateAssembler(
              inputStates,
              metadataSelectors,
              preferences,
              navigation,
              projection,
              new GenerationStateTracker(),
              new UiStateSnapshotMapper());
      return new RenderFixture(
          assembler, initialReducerState(initialState), inputStates, navigation, preferences);
    }
  }

  private static UiState initialReducerState(ForgeUiState initialState) {
    return new UiState(
        initialState.request(),
        initialState.validation(),
        FocusTarget.GROUP_ID,
        "Ready",
        "",
        "",
        false,
        false,
        false,
        false,
        0,
        EMPTY_METADATA_PANEL,
        EMPTY_EXTENSIONS_PANEL,
        EMPTY_FOOTER,
        new UiState.OverlayState(false, false, false, false, false),
        new UiState.GenerationView(CoreTuiController.GenerationState.IDLE, 0.0, "", false),
        new UiState.CatalogLoadView(CatalogLoadState.initial()),
        new UiState.PostGenerationView(false, false, 0, 0, List.of(), null, "", null),
        new UiState.StartupOverlayView(false, List.of()),
        UiTestFixtureFactory.defaultExtensionView());
  }

  private static EnumMap<FocusTarget, TextInputState> initialInputStates(
      ForgeUiState initialState) {
    EnumMap<FocusTarget, TextInputState> inputStates = new EnumMap<>(FocusTarget.class);
    for (FocusTarget target : FocusTarget.values()) {
      inputStates.put(target, new TextInputState(""));
    }
    inputStates.get(FocusTarget.GROUP_ID).setText(initialState.request().groupId());
    inputStates.get(FocusTarget.ARTIFACT_ID).setText(initialState.request().artifactId());
    inputStates.get(FocusTarget.VERSION).setText(initialState.request().version());
    inputStates.get(FocusTarget.PACKAGE_NAME).setText(initialState.request().packageName());
    inputStates.get(FocusTarget.OUTPUT_DIR).setText(initialState.request().outputDirectory());
    return inputStates;
  }
}
