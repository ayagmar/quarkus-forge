package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoreUiReducerTest {

  private final UiReducer reducer = new CoreUiReducer();

  @Test
  void validSubmitProducesPrepareAndStartEffects() {
    ReduceResult result =
        reducer.reduce(
            baseState(),
            new UiIntent.SubmitRequestedIntent(
                new UiIntent.SubmitEvaluation(true, 2, null, 0, "", "")));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects())
        .containsExactly(
            new UiEffect.PrepareForGeneration(),
            new UiEffect.TransitionGenerationState(CoreTuiController.GenerationState.VALIDATING),
            new UiEffect.StartGeneration());
    assertThat(result.nextState().statusMessage())
        .isEqualTo("Submit requested with 2 extension(s)");
    assertThat(result.nextState().submitRequested()).isTrue();
    assertThat(result.nextState().submitBlockedByValidation()).isFalse();
    assertThat(result.nextState().submitBlockedByTargetConflict()).isFalse();
  }

  @Test
  void invalidSubmitMovesFocusAndBlocksViaReducerState() {
    ReduceResult result =
        reducer.reduce(
            baseState(),
            new UiIntent.SubmitRequestedIntent(
                new UiIntent.SubmitEvaluation(
                    true, 0, FocusTarget.GROUP_ID, 2, "groupId: must not be blank", "")));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects())
        .containsExactly(
            new UiEffect.PrepareForGeneration(),
            new UiEffect.TransitionGenerationState(CoreTuiController.GenerationState.VALIDATING),
            new UiEffect.TransitionGenerationState(CoreTuiController.GenerationState.ERROR));
    assertThat(result.nextState().focusTarget()).isEqualTo(FocusTarget.GROUP_ID);
    assertThat(result.nextState().statusMessage())
        .isEqualTo("Submit blocked: fix groupId (2 issues)");
    assertThat(result.nextState().errorMessage()).isEqualTo("groupId: must not be blank");
    assertThat(result.nextState().submitBlockedByValidation()).isTrue();
    assertThat(result.nextState().submitBlockedByTargetConflict()).isFalse();
  }

  @Test
  void targetConflictSubmitBlocksWithoutStartingGeneration() {
    ReduceResult result =
        reducer.reduce(
            baseState(),
            new UiIntent.SubmitRequestedIntent(
                new UiIntent.SubmitEvaluation(
                    true, 0, null, 0, "", "Output directory already exists: /tmp/demo")));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects())
        .containsExactly(
            new UiEffect.PrepareForGeneration(),
            new UiEffect.TransitionGenerationState(CoreTuiController.GenerationState.VALIDATING),
            new UiEffect.TransitionGenerationState(CoreTuiController.GenerationState.ERROR));
    assertThat(result.nextState().focusTarget()).isEqualTo(FocusTarget.OUTPUT_DIR);
    assertThat(result.nextState().statusMessage())
        .isEqualTo("Submit blocked: target folder exists (change output/artifact)");
    assertThat(result.nextState().errorMessage())
        .isEqualTo("Output directory already exists: /tmp/demo");
    assertThat(result.nextState().submitBlockedByTargetConflict()).isTrue();
  }

  @Test
  void unconfiguredGenerationRunnerKeepsSubmitStateReducerOwned() {
    ReduceResult result =
        reducer.reduce(
            baseState(),
            new UiIntent.SubmitRequestedIntent(
                new UiIntent.SubmitEvaluation(false, 3, null, 0, "", "")));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects())
        .containsExactly(
            new UiEffect.PrepareForGeneration(),
            new UiEffect.TransitionGenerationState(CoreTuiController.GenerationState.VALIDATING),
            new UiEffect.TransitionGenerationState(CoreTuiController.GenerationState.IDLE));
    assertThat(result.nextState().statusMessage())
        .isEqualTo(
            "Submit requested with 3 extension(s), but generation service is not configured.");
    assertThat(result.nextState().submitRequested()).isTrue();
  }

  @Test
  void submitRecoveryClearsValidationBlockWhenInputBecomesValid() {
    UiState blockedState =
        baseState()
            .withSubmissionState(
                "Submit blocked", "artifactId: invalid", "", true, true, false, true);

    ReduceResult result =
        reducer.reduce(
            blockedState,
            new UiIntent.SubmitEditRecoveryIntent(
                new UiIntent.SubmitEditRecovery(true, true, "", false, "")));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.nextState().statusMessage()).isEqualTo("Validation restored");
    assertThat(result.nextState().errorMessage()).isEmpty();
    assertThat(result.nextState().showErrorDetails()).isFalse();
    assertThat(result.nextState().submitBlockedByValidation()).isFalse();
  }

  @Test
  void submitRecoveryClearsTargetConflictWhenPathIsFree() {
    UiState blockedState =
        baseState()
            .withSubmissionState(
                "Submit blocked", "Output directory already exists", "", true, false, true, true);

    ReduceResult result =
        reducer.reduce(
            blockedState,
            new UiIntent.SubmitEditRecoveryIntent(
                new UiIntent.SubmitEditRecovery(false, true, "", true, "")));

    assertThat(result.nextState().statusMessage()).isEqualTo("Target folder conflict resolved");
    assertThat(result.nextState().errorMessage()).isEmpty();
    assertThat(result.nextState().submitBlockedByTargetConflict()).isFalse();
    assertThat(result.nextState().showErrorDetails()).isFalse();
  }

  @Test
  void cancelGenerationProducesRequestCancellationEffect() {
    ReduceResult result = reducer.reduce(baseState(), new UiIntent.CancelGenerationIntent());

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects()).containsExactly(new UiEffect.RequestGenerationCancellation());
  }

  @Test
  void catalogLoadRequestProducesCatalogEffect() {
    ExtensionCatalogLoader loader = () -> null;

    ReduceResult result =
        reducer.reduce(baseState(), new UiIntent.CatalogLoadRequestedIntent(loader));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects()).containsExactly(new UiEffect.StartCatalogLoad(loader));
  }

  @Test
  void catalogLoadSuccessUpdatesReducerStateAndRequestsCatalogApply() {
    CatalogLoadSuccess success =
        new CatalogLoadSuccess(
            List.of(new ExtensionCatalogItem("io.quarkus:quarkus-rest", "REST", "rest", "Web", 10)),
            null,
            java.util.Map.of(),
            CatalogLoadState.loaded("live", false),
            "Loaded extension catalog from live API");

    ReduceResult result =
        reducer.reduce(baseState(), new UiIntent.CatalogLoadSucceededIntent(success, true));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.nextState().catalogLoad().sourceLabel()).isEqualTo("live");
    assertThat(result.nextState().startupOverlay().visible()).isTrue();
    assertThat(result.nextState().errorMessage()).isEmpty();
    assertThat(result.effects())
        .containsExactly(
            new UiEffect.ApplyCatalogLoadSuccess(success), new UiEffect.RequestAsyncRepaint());
  }

  @Test
  void generationProgressUsesCanonicalStatusMessage() {
    ReduceResult result =
        reducer.reduce(
            baseState(),
            new UiIntent.GenerationProgressIntent(
                "Generation in progress: requesting project archive from Quarkus API..."));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects()).isEmpty();
    assertThat(result.nextState().statusMessage())
        .isEqualTo("Generation in progress: requesting project archive from Quarkus API...");
    assertThat(result.nextState().errorMessage()).isEmpty();
  }

  @Test
  void generationSuccessUpdatesPostGenerationStateDirectly() {
    Path generatedPath = Path.of("build/generated-project");

    ReduceResult result =
        reducer.reduce(
            baseState(), new UiIntent.GenerationSuccessIntent(generatedPath, "mvn quarkus:dev"));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.nextState().statusMessage())
        .isEqualTo("Generation succeeded: " + generatedPath);
    assertThat(result.nextState().postGeneration().visible()).isTrue();
    assertThat(result.nextState().postGeneration().successHint())
        .isEqualTo("cd " + generatedPath + " && mvn quarkus:dev");
    assertThat(result.effects()).containsExactly(new UiEffect.RequestAsyncRepaint());
  }

  @Test
  void generationFailedUpdatesErrorsAndClosesPostGenerationState() {
    UiState currentState =
        baseState()
            .withPostGeneration(
                new UiState.PostGenerationView(
                    true,
                    false,
                    0,
                    0,
                    baseState().postGeneration().actions(),
                    Path.of("/tmp/demo"),
                    "mvn quarkus:dev",
                    null));

    ReduceResult result =
        reducer.reduce(
            currentState,
            new UiIntent.GenerationFailedIntent(
                "Unable to download archive", "HTTP 502 while contacting API"));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.nextState().statusMessage()).isEqualTo("Generation failed.");
    assertThat(result.nextState().errorMessage()).isEqualTo("Unable to download archive");
    assertThat(result.nextState().verboseErrorDetails()).isEqualTo("HTTP 502 while contacting API");
    assertThat(result.nextState().postGeneration().visible()).isFalse();
    assertThat(result.effects()).containsExactly(new UiEffect.RequestAsyncRepaint());
  }

  @Test
  void generationCancelledUpdatesStatusAndClosesPostGenerationState() {
    ReduceResult result = reducer.reduce(baseState(), new UiIntent.GenerationCancelledIntent());

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.nextState().statusMessage())
        .isEqualTo("Generation cancelled. Update inputs and press Enter to retry.");
    assertThat(result.nextState().postGeneration().visible()).isFalse();
    assertThat(result.effects()).containsExactly(new UiEffect.RequestAsyncRepaint());
  }

  @Test
  void cancellationRequestedUpdatesStatusAndClearsError() {
    UiState stateWithError =
        baseState()
            .withSubmissionState("before", "existing error", "verbose", true, false, false, true);

    ReduceResult result =
        reducer.reduce(stateWithError, new UiIntent.GenerationCancellationRequestedIntent());

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.nextState().statusMessage())
        .isEqualTo("Cancellation requested. Waiting for cleanup...");
    assertThat(result.nextState().errorMessage()).isEmpty();
    assertThat(result.nextState().showErrorDetails()).isFalse();
    assertThat(result.effects()).isEmpty();
  }

  @Test
  void postGenerationQuitProducesQuitActionAndExitPlanState() {
    ReduceResult result =
        reducer.reduce(
            postGenerationVisibleState(),
            new UiIntent.PostGenerationIntent(new UiIntent.PostGenerationCommand.Quit()));

    assertThat(result.action()).isEqualTo(UiAction.handled(true));
    assertThat(result.effects()).containsExactly(new UiEffect.CancelPendingAsync());
    assertThat(result.nextState().postGeneration().exitPlan()).isNotNull();
    assertThat(result.nextState().postGeneration().exitPlan().action())
        .isEqualTo(PostGenerationExitAction.QUIT);
  }

  @Test
  void postGenerationExportKeepsMenuOpenAndProducesExportEffect() {
    UiState exportState =
        postGenerationVisibleState()
            .withPostGeneration(
                new UiState.PostGenerationView(
                    true,
                    false,
                    5,
                    0,
                    postGenerationVisibleState().postGeneration().actions(),
                    Path.of("/tmp/demo"),
                    "mvn quarkus:dev",
                    null));

    ReduceResult result =
        reducer.reduce(
            exportState,
            new UiIntent.PostGenerationIntent(
                new UiIntent.PostGenerationCommand.ConfirmSelection()));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects()).containsExactly(new UiEffect.ExportRecipeAndLock());
    assertThat(result.nextState().postGeneration().visible()).isTrue();
  }

  @Test
  void postGenerationGenerateAgainResetsStateAndKeepsTuiOpen() {
    UiState generateAgainState =
        postGenerationVisibleState()
            .withPostGeneration(
                new UiState.PostGenerationView(
                    true,
                    false,
                    3,
                    0,
                    postGenerationVisibleState().postGeneration().actions(),
                    Path.of("/tmp/demo"),
                    "mvn quarkus:dev",
                    null));

    ReduceResult result =
        reducer.reduce(
            generateAgainState,
            new UiIntent.PostGenerationIntent(
                new UiIntent.PostGenerationCommand.ConfirmSelection()));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects()).containsExactly(new UiEffect.PrepareForGeneration());
    assertThat(result.nextState().statusMessage()).isEqualTo("Ready for next generation");
    assertThat(result.nextState().postGeneration().visible()).isFalse();
    assertThat(result.nextState().postGeneration().successHint()).isEmpty();
  }

  @Test
  void postGenerationGithubPublishKeepsSubmenuStateReducerOwned() {
    UiState openGithubActionState = postGenerationVisibleState();

    ReduceResult openSubmenuResult =
        reducer.reduce(
            openGithubActionState,
            new UiIntent.PostGenerationIntent(
                new UiIntent.PostGenerationCommand.ConfirmSelection()));

    assertThat(openSubmenuResult.nextState().postGeneration().githubVisibilityVisible()).isTrue();

    ReduceResult chooseVisibilityResult =
        reducer.reduce(
            openSubmenuResult.nextState(),
            new UiIntent.PostGenerationIntent(
                new UiIntent.PostGenerationCommand.SelectGithubVisibilityIndex(1)));

    assertThat(chooseVisibilityResult.action()).isEqualTo(UiAction.handled(true));
    assertThat(chooseVisibilityResult.nextState().postGeneration().exitPlan()).isNotNull();
    assertThat(chooseVisibilityResult.nextState().postGeneration().exitPlan().githubVisibility())
        .isEqualTo(GitHubVisibility.PUBLIC);
  }

  @Test
  void toggleErrorDetailsCollapsesWhenNoActiveErrorExists() {
    ReduceResult result = reducer.reduce(baseState(), new UiIntent.ToggleErrorDetailsIntent(false));

    assertThat(result.nextState().showErrorDetails()).isFalse();
    assertThat(result.nextState().statusMessage()).isEqualTo("No error details available");
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
            currentState.showErrorDetails(),
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
  void focusNavigationIntentUpdatesFocusStateDirectly() {
    ReduceResult result =
        reducer.reduce(
            baseState(),
            new UiIntent.FocusNavigationIntent(KeyEvent.ofKey(KeyCode.TAB), FocusTarget.GROUP_ID));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects()).isEmpty();
    assertThat(result.nextState().focusTarget()).isEqualTo(FocusTarget.ARTIFACT_ID);
    assertThat(result.nextState().statusMessage()).isEqualTo("Focus moved to artifactId");
    assertThat(result.nextState().errorMessage()).isEmpty();
    assertThat(result.nextState().submitBlockedByValidation()).isFalse();
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

  private static UiState postGenerationVisibleState() {
    UiState state = baseState();
    return state.withPostGeneration(
        new UiState.PostGenerationView(
            true,
            false,
            0,
            0,
            state.postGeneration().actions(),
            Path.of("/tmp/demo"),
            "mvn quarkus:dev",
            null));
  }
}
