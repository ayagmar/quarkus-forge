package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import org.junit.jupiter.api.Test;

class CoreUiReducerTest {

  private final UiReducer reducer = new CoreUiReducer();

  @Test
  void postGenerationQuitProducesQuitActionAndCancelEffect() {
    ReduceResult result =
        reducer.reduce(
            UiControllerTestHarness.controller().uiState(),
            new UiIntent.PostGenerationIntent(UiIntent.PostGenerationTransition.QUIT));

    assertThat(result.action()).isEqualTo(UiAction.handled(true));
    assertThat(result.effects()).containsExactly(new UiEffect.CancelPendingAsync());
  }

  @Test
  void postGenerationExportRecipeProducesExportEffect() {
    ReduceResult result =
        reducer.reduce(
            UiControllerTestHarness.controller().uiState(),
            new UiIntent.PostGenerationIntent(UiIntent.PostGenerationTransition.EXPORT_RECIPE));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects()).containsExactly(new UiEffect.ExportRecipeAndLock());
  }

  @Test
  void postGenerationGenerateAgainResetsStatusAndErrorViaReducerState() {
    UiState baseState =
        UiControllerTestHarness.controller()
            .uiState()
            .withStatusAndError("Some previous status", "Some previous error");

    ReduceResult result =
        reducer.reduce(
            baseState,
            new UiIntent.PostGenerationIntent(UiIntent.PostGenerationTransition.GENERATE_AGAIN));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects()).containsExactly(new UiEffect.ResetGenerationAfterOutcome());
    assertThat(result.nextState().statusMessage()).isEqualTo("Ready for next generation");
    assertThat(result.nextState().errorMessage()).isEmpty();
  }

  @Test
  void metadataIntentIsIgnoredWhenSelectorHasNoOptions() {
    UiState currentState = UiControllerTestHarness.controller().uiState();
    UiState noOptionsState =
        new UiState(
            currentState.request(),
            currentState.validation(),
            FocusTarget.BUILD_TOOL,
            currentState.statusMessage(),
            currentState.errorMessage(),
            currentState.verboseErrorDetails(),
            currentState.submitRequested(),
            currentState.submitBlockedByValidation(),
            currentState.submitBlockedByTargetConflict(),
            currentState.commandPaletteSelection(),
            new MetadataPanelSnapshot(
                currentState.metadataPanel().title(),
                currentState.metadataPanel().focused(),
                currentState.metadataPanel().invalid(),
                currentState.metadataPanel().groupId(),
                currentState.metadataPanel().artifactId(),
                currentState.metadataPanel().version(),
                currentState.metadataPanel().packageName(),
                currentState.metadataPanel().outputDir(),
                currentState.metadataPanel().platformStream(),
                currentState.metadataPanel().buildTool(),
                currentState.metadataPanel().javaVersion(),
                currentState.metadataPanel().platformStreamInfo(),
                MetadataPanelSnapshot.SelectorInfo.EMPTY,
                currentState.metadataPanel().javaVersionInfo()),
            currentState.extensionsPanel(),
            currentState.footer(),
            currentState.overlays(),
            currentState.generation(),
            currentState.catalogLoad(),
            currentState.postGeneration(),
            currentState.startupOverlay(),
            currentState.extensions());

    ReduceResult result =
        reducer.reduce(
            noOptionsState,
            new UiIntent.MetadataInputIntent(KeyEvent.ofKey(KeyCode.LEFT), FocusTarget.BUILD_TOOL));

    assertThat(result.action()).isEqualTo(UiAction.ignored());
    assertThat(result.effects()).isEmpty();
  }
}
