package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.persistence.ExtensionFavoritesStore;
import dev.tamboui.widgets.input.TextInputState;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class UiRenderStateAssemblerTest {
  @Test
  void renderModelCombinesReducerAndRuntimeRenderSlices() {
    ForgeUiState initialState = UiTestFixtureFactory.defaultForgeUiState();
    RenderFixture fixture = RenderFixture.create(initialState);

    UiRenderModel renderModel =
        fixture.assembler.renderModel(
            fixture.reducerState, "Ready", initialState.metadataCompatibility(), false);

    assertThat(renderModel.metadataPanel().title()).isEqualTo("Project Metadata");
    assertThat(renderModel.metadataPanel().focused()).isTrue();
    assertThat(renderModel.metadataPanel().groupId()).isEqualTo(initialState.request().groupId());
    assertThat(renderModel.submitAlert()).isEqualTo(SubmitAlertSnapshot.HIDDEN);
    assertThat(renderModel.extensionsPanel().catalogSource()).isEqualTo("snapshot");
    assertThat(renderModel.extensionsPanel().filteredExtensionCount()).isEqualTo(7);
    assertThat(renderModel.footer().statusMessage()).isEqualTo("Ready");
    assertThat(renderModel.footer().preGeneratePlan()).contains("forge-app");
    assertThat(renderModel.startupOverlay().statusLines()).isNotEmpty();
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

  @Test
  void footerSuppressesTargetPathAndPlanWhilePostGenerationMenuIsVisible() {
    ForgeUiState initialState = UiTestFixtureFactory.defaultForgeUiState();
    RenderFixture fixture = RenderFixture.create(initialState);
    UiState reducerState =
        fixture.reducerState.withPostGeneration(
            new UiState.PostGenerationView(
                true, false, 0, 0, List.of(), Path.of("output/forge-app"), "", null));

    UiRenderModel renderModel =
        fixture.assembler.renderModel(
            reducerState, "Ready", initialState.metadataCompatibility(), false);

    assertThat(renderModel.footer().resolvedTargetPath()).isEmpty();
    assertThat(renderModel.footer().preGeneratePlan()).isEmpty();
  }

  @Test
  void renderModelExposesCurrentRenderSlices() {
    ForgeUiState initialState = UiTestFixtureFactory.defaultForgeUiState();
    RenderFixture fixture = RenderFixture.create(initialState);

    UiRenderModel renderModel =
        fixture.assembler.renderModel(
            fixture.reducerState, "Ready", initialState.metadataCompatibility(), false);

    assertThat(renderModel.reducerState().statusMessage()).isEqualTo("Ready");
    assertThat(renderModel.submitAlert()).isEqualTo(SubmitAlertSnapshot.HIDDEN);
    assertThat(renderModel.metadataPanel().title()).isEqualTo("Project Metadata");
    assertThat(renderModel.extensionsPanel().catalogSource()).isEqualTo("snapshot");
    assertThat(renderModel.footer().statusMessage()).isEqualTo("Ready");
    assertThat(renderModel.generation().state()).isEqualTo(GenerationState.IDLE);
    assertThat(renderModel.startupOverlay().statusLines()).isNotEmpty();
  }

  @Test
  void renderModelSurfacesSubmitBlockAlertAndFocusedIssue() {
    ForgeUiState initialState = UiTestFixtureFactory.defaultForgeUiState();
    RenderFixture fixture = RenderFixture.create(initialState);
    UiState reducerState =
        new UiState(
            fixture.reducerState.request(),
            fixture.reducerState.validation(),
            FocusTarget.OUTPUT_DIR,
            "Submit blocked: target folder exists (change output/artifact)",
            "Output directory already exists: /tmp/demo",
            "",
            false,
            true,
            false,
            true,
            fixture.reducerState.commandPaletteSelection(),
            fixture.reducerState.overlays(),
            fixture.reducerState.catalogLoad(),
            fixture.reducerState.postGeneration(),
            fixture.reducerState.extensions());

    UiRenderModel renderModel =
        fixture.assembler.renderModel(
            reducerState,
            reducerState.statusMessage(),
            initialState.metadataCompatibility(),
            false);

    assertThat(renderModel.submitAlert().visible()).isTrue();
    assertThat(renderModel.submitAlert().title()).isEqualTo("Submit blocked");
    assertThat(renderModel.submitAlert().lines())
        .anyMatch(line -> line.contains("Target folder already exists"))
        .anyMatch(line -> line.contains("Focus moved to Output directory"));
    assertThat(renderModel.metadataPanel().focusedFieldIssue())
        .contains("Output directory already exists: /tmp/demo");
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
        new UiState.OverlayState(false, false, false, false),
        new UiState.CatalogLoadView(CatalogLoadState.initial()),
        new UiState.PostGenerationView(false, false, 0, 0, List.of(), null, "", null),
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
