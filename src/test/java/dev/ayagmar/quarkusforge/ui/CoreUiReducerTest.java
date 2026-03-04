package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import org.junit.jupiter.api.Test;

class CoreUiReducerTest {

  private final UiReducer reducer = new CoreUiReducer();

  @Test
  void submitReadyProducesStartGenerationEffect() {
    ReduceResult result = reducer.reduce(baseState(), new UiIntent.SubmitReadyIntent());

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects()).containsExactly(new UiEffect.StartGeneration());
  }

  @Test
  void cancelGenerationProducesRequestCancellationEffect() {
    ReduceResult result = reducer.reduce(baseState(), new UiIntent.CancelGenerationIntent());

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects()).containsExactly(new UiEffect.RequestGenerationCancellation());
  }

  @Test
  void generationProgressUpdatesStatusWithMessage() {
    ReduceResult result =
        reducer.reduce(
            baseState(),
            new UiIntent.GenerationProgressIntent(
                GenerationProgressUpdate.extractingArchive("extracting archive...")));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects()).isEmpty();
    assertThat(result.nextState().statusMessage())
        .isEqualTo("Generation in progress: extracting archive...");
    assertThat(result.nextState().errorMessage()).isEmpty();
  }

  @Test
  void generationProgressUsesWorkingFallbackWhenMessageBlank() {
    ReduceResult result =
        reducer.reduce(
            baseState(),
            new UiIntent.GenerationProgressIntent(
                new GenerationProgressUpdate(GenerationProgressStep.REQUESTING_ARCHIVE, "")));

    assertThat(result.nextState().statusMessage()).isEqualTo("Generation in progress: working...");
  }

  @Test
  void generationSuccessUpdatesStatusAndProducesPostGenerationEffects() {
    ReduceResult result =
        reducer.reduce(
            baseState(),
            new UiIntent.GenerationSuccessIntent(
                java.nio.file.Path.of("build/generated-project"), "mvn quarkus:dev"));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.nextState().statusMessage())
        .isEqualTo("Generation succeeded: build/generated-project");
    assertThat(result.nextState().errorMessage()).isEmpty();
    assertThat(result.effects())
        .containsExactly(
            new UiEffect.ShowPostGenerationSuccess(
                java.nio.file.Path.of("build/generated-project"), "mvn quarkus:dev"),
            new UiEffect.RequestAsyncRepaint());
  }

  @Test
  void generationFailedUpdatesErrorsAndProducesHideMenuEffects() {
    ReduceResult result =
        reducer.reduce(
            baseState(),
            new UiIntent.GenerationFailedIntent(
                "Unable to download archive", "HTTP 502 while contacting API"));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.nextState().statusMessage()).isEqualTo("Generation failed.");
    assertThat(result.nextState().errorMessage()).isEqualTo("Unable to download archive");
    assertThat(result.nextState().verboseErrorDetails()).isEqualTo("HTTP 502 while contacting API");
    assertThat(result.effects())
        .containsExactly(new UiEffect.HidePostGenerationMenu(), new UiEffect.RequestAsyncRepaint());
  }

  @Test
  void generationCancelledUpdatesStatusAndProducesHideMenuEffects() {
    ReduceResult result = reducer.reduce(baseState(), new UiIntent.GenerationCancelledIntent());

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.nextState().statusMessage())
        .isEqualTo("Generation cancelled. Update inputs and press Enter to retry.");
    assertThat(result.effects())
        .containsExactly(new UiEffect.HidePostGenerationMenu(), new UiEffect.RequestAsyncRepaint());
  }

  @Test
  void cancellationRequestedUpdatesStatusAndClearsError() {
    UiState stateWithError = baseState().withStatusAndError("before", "existing error");

    ReduceResult result =
        reducer.reduce(stateWithError, new UiIntent.GenerationCancellationRequestedIntent());

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.nextState().statusMessage())
        .isEqualTo("Cancellation requested. Waiting for cleanup...");
    assertThat(result.nextState().errorMessage()).isEmpty();
    assertThat(result.effects()).isEmpty();
  }

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
    UiState currentState = baseState();
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

  @Test
  void metadataIntentWithOptionsProducesSelectorEffect() {
    ReduceResult result =
        reducer.reduce(
            baseState(),
            new UiIntent.MetadataInputIntent(KeyEvent.ofKey(KeyCode.LEFT), FocusTarget.BUILD_TOOL));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects()).hasSize(1);
    assertThat(result.effects().getFirst()).isInstanceOf(UiEffect.ApplyMetadataSelectorKey.class);
  }

  @Test
  void textInputIntentWithSupportedKeyProducesApplyKeyEffect() {
    ReduceResult result =
        reducer.reduce(
            baseState(),
            new UiIntent.TextInputIntent(KeyEvent.ofChar('a'), FocusTarget.ARTIFACT_ID));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects()).hasSize(1);
    assertThat(result.effects().getFirst()).isInstanceOf(UiEffect.ApplyTextInputKey.class);
  }

  @Test
  void textInputIntentWithUnsupportedKeyIsIgnored() {
    ReduceResult result =
        reducer.reduce(
            baseState(),
            new UiIntent.TextInputIntent(
                KeyEvent.ofChar('a', dev.tamboui.tui.event.KeyModifiers.CTRL),
                FocusTarget.ARTIFACT_ID));

    assertThat(result.action()).isEqualTo(UiAction.ignored());
    assertThat(result.effects()).isEmpty();
  }

  @Test
  void focusNavigationIntentProducesMoveFocusEffect() {
    ReduceResult result =
        reducer.reduce(
            baseState(),
            new UiIntent.FocusNavigationIntent(KeyEvent.ofKey(KeyCode.TAB), FocusTarget.GROUP_ID));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects()).containsExactly(new UiEffect.MoveFocus(1));
  }

  @Test
  void unrelatedIntentReturnsIgnoredWithoutEffects() {
    ReduceResult result =
        reducer.reduce(
            baseState(),
            new UiIntent.MetadataInputIntent(KeyEvent.ofChar('x'), FocusTarget.SUBMIT));

    assertThat(result.action()).isEqualTo(UiAction.ignored());
    assertThat(result.effects()).isEmpty();
  }

  private static UiState baseState() {
    return UiControllerTestHarness.controller().uiState();
  }
}
