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
            configuredState(2),
            new UiIntent.SubmitRequestedIntent(new UiIntent.SubmitRequestContext(true, 2, "")));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects())
        .containsExactly(
            new UiEffect.PrepareForGeneration(),
            new UiEffect.TransitionGenerationState(GenerationState.VALIDATING),
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
            invalidConfiguredState(),
            new UiIntent.SubmitRequestedIntent(new UiIntent.SubmitRequestContext(true, 0, "")));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects())
        .containsExactly(
            new UiEffect.PrepareForGeneration(),
            new UiEffect.TransitionGenerationState(GenerationState.VALIDATING),
            new UiEffect.TransitionGenerationState(GenerationState.ERROR));
    assertThat(result.nextState().focusTarget()).isEqualTo(FocusTarget.GROUP_ID);
    assertThat(result.nextState().statusMessage())
        .isEqualTo("Submit blocked: fix groupId (1 issue)");
    assertThat(result.nextState().errorMessage()).isEqualTo("groupId: must not be blank");
    assertThat(result.nextState().submitBlockedByValidation()).isTrue();
    assertThat(result.nextState().submitBlockedByTargetConflict()).isFalse();
  }

  @Test
  void targetConflictSubmitBlocksWithoutStartingGeneration() {
    ReduceResult result =
        reducer.reduce(
            configuredState(0),
            new UiIntent.SubmitRequestedIntent(
                new UiIntent.SubmitRequestContext(
                    true, 0, "Output directory already exists: /tmp/demo")));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects())
        .containsExactly(
            new UiEffect.PrepareForGeneration(),
            new UiEffect.TransitionGenerationState(GenerationState.VALIDATING),
            new UiEffect.TransitionGenerationState(GenerationState.ERROR));
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
            new UiIntent.SubmitRequestedIntent(new UiIntent.SubmitRequestContext(false, 3, "")));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects())
        .containsExactly(
            new UiEffect.PrepareForGeneration(),
            new UiEffect.TransitionGenerationState(GenerationState.VALIDATING),
            new UiEffect.TransitionGenerationState(GenerationState.IDLE));
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
            validState(blockedState),
            new UiIntent.SubmitEditRecoveryIntent(new UiIntent.SubmitRecoveryContext("")));

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
            new UiIntent.SubmitEditRecoveryIntent(new UiIntent.SubmitRecoveryContext("")));

    assertThat(result.nextState().statusMessage()).isEqualTo("Target folder conflict resolved");
    assertThat(result.nextState().errorMessage()).isEmpty();
    assertThat(result.nextState().submitBlockedByTargetConflict()).isFalse();
    assertThat(result.nextState().showErrorDetails()).isFalse();
  }

  @Test
  void submitRecoveryKeepsValidationBlockWhenEditedStateIsStillInvalid() {
    UiState blockedState =
        invalidConfiguredState()
            .withSubmissionState(
                "Submit blocked", "groupId: must not be blank", "", true, true, false, true);

    ReduceResult result =
        reducer.reduce(
            blockedState,
            new UiIntent.SubmitEditRecoveryIntent(new UiIntent.SubmitRecoveryContext("")));

    assertThat(result.nextState().statusMessage()).isEqualTo("Submit blocked");
    assertThat(result.nextState().errorMessage()).isEqualTo("groupId: must not be blank");
    assertThat(result.nextState().submitBlockedByValidation()).isTrue();
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
    assertThat(result.nextState().overlays().startupOverlayVisible()).isTrue();
    assertThat(result.nextState().errorMessage()).isEmpty();
    assertThat(result.effects())
        .containsExactly(
            new UiEffect.ApplyCatalogLoadSuccess(success), new UiEffect.RequestAsyncRepaint());
  }

  @Test
  void generationOverlayVisibilityIntentUpdatesReducerOwnedOverlayState() {
    ReduceResult result =
        reducer.reduce(baseState(), new UiIntent.GenerationOverlayVisibilityIntent(true));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects()).isEmpty();
    assertThat(result.nextState().overlays().generationVisible()).isTrue();
  }

  @Test
  void generationProgressClearsErrorsWithoutRecomputingStatusText() {
    UiState state =
        baseState()
            .withSubmissionState(
                "Submit requested with 2 extension(s)",
                "Output directory already exists",
                "verbose",
                true,
                true,
                true,
                true);

    ReduceResult result = reducer.reduce(state, new UiIntent.GenerationProgressIntent());

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects()).isEmpty();
    assertThat(result.nextState().statusMessage())
        .isEqualTo("Submit requested with 2 extension(s)");
    assertThat(result.nextState().errorMessage()).isEmpty();
    assertThat(result.nextState().verboseErrorDetails()).isEmpty();
    assertThat(result.nextState().showErrorDetails()).isFalse();
    assertThat(result.nextState().submitBlockedByValidation()).isFalse();
    assertThat(result.nextState().submitBlockedByTargetConflict()).isFalse();
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
    int exportSelection = postGenerationActionIndex(PostGenerationExitAction.EXPORT_RECIPE_LOCK);
    UiState exportState =
        postGenerationVisibleState()
            .withPostGeneration(
                new UiState.PostGenerationView(
                    true,
                    false,
                    exportSelection,
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
    int generateAgainSelection = postGenerationActionIndex(PostGenerationExitAction.GENERATE_AGAIN);
    UiState generateAgainState =
        postGenerationVisibleState()
            .withPostGeneration(
                new UiState.PostGenerationView(
                    true,
                    false,
                    generateAgainSelection,
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
  void postGenerationInvalidActionSelectionIsIgnored() {
    ReduceResult result =
        reducer.reduce(
            postGenerationVisibleState(),
            new UiIntent.PostGenerationIntent(
                new UiIntent.PostGenerationCommand.SelectActionIndex(99)));

    assertThat(result.nextState()).isEqualTo(postGenerationVisibleState());
    assertThat(result.effects()).isEmpty();
    assertThat(result.action()).isEqualTo(UiAction.handled(false));
  }

  @Test
  void postGenerationInvalidGithubVisibilitySelectionIsIgnored() {
    UiState githubVisibilityState =
        postGenerationVisibleState()
            .withPostGeneration(
                postGenerationVisibleState().postGeneration().showGithubVisibilityMenu());

    ReduceResult result =
        reducer.reduce(
            githubVisibilityState,
            new UiIntent.PostGenerationIntent(
                new UiIntent.PostGenerationCommand.SelectGithubVisibilityIndex(99)));

    assertThat(result.nextState()).isEqualTo(githubVisibilityState);
    assertThat(result.effects()).isEmpty();
    assertThat(result.action()).isEqualTo(UiAction.handled(false));
  }

  @Test
  void hiddenPostGenerationCommandsAreIgnored() {
    ReduceResult result =
        reducer.reduce(
            baseState(),
            new UiIntent.PostGenerationIntent(
                new UiIntent.PostGenerationCommand.ConfirmSelection()));

    assertThat(result.nextState()).isEqualTo(baseState());
    assertThat(result.effects()).isEmpty();
    assertThat(result.action()).isEqualTo(UiAction.handled(false));
  }

  @Test
  void toggleErrorDetailsCollapsesWhenNoActiveErrorExists() {
    ReduceResult result = reducer.reduce(baseState(), new UiIntent.ToggleErrorDetailsIntent());

    assertThat(result.nextState().showErrorDetails()).isFalse();
    assertThat(result.nextState().statusMessage()).isEqualTo("No error details available");
  }

  @Test
  void commandPaletteToggleOpensAndClosesOverlaysThroughReducerState() {
    UiState helpVisibleState =
        stateWithOverlayState(
            baseState(), new UiState.OverlayState(false, false, true, false), 3, "Ready");

    ReduceResult openResult =
        reducer.reduce(
            helpVisibleState,
            new UiIntent.CommandPaletteIntent(
                new UiIntent.CommandPaletteCommand.ToggleVisibility()));

    assertThat(openResult.action()).isEqualTo(UiAction.handled(false));
    assertThat(openResult.effects()).isEmpty();
    assertThat(openResult.nextState().overlays().commandPaletteVisible()).isTrue();
    assertThat(openResult.nextState().overlays().helpOverlayVisible()).isFalse();
    assertThat(openResult.nextState().commandPaletteSelection()).isZero();
    assertThat(openResult.nextState().statusMessage()).isEqualTo("Command palette opened");

    ReduceResult closeResult =
        reducer.reduce(
            openResult.nextState(),
            new UiIntent.CommandPaletteIntent(
                new UiIntent.CommandPaletteCommand.ToggleVisibility()));

    assertThat(closeResult.nextState().overlays().commandPaletteVisible()).isFalse();
    assertThat(closeResult.nextState().statusMessage()).isEqualTo("Command palette closed");
  }

  @Test
  void commandPaletteConfirmClosesOverlayAndRoutesFocusActionThroughSharedReducerPath() {
    UiState paletteState =
        stateWithOverlayState(
            baseState(), new UiState.OverlayState(false, true, false, false), 0, "Ready");

    ReduceResult confirmResult =
        reducer.reduce(
            paletteState,
            new UiIntent.CommandPaletteIntent(
                new UiIntent.CommandPaletteCommand.ConfirmSelection()));

    assertThat(confirmResult.effects())
        .containsExactly(new UiEffect.MoveTextInputCursorToEnd(FocusTarget.EXTENSION_SEARCH));
    assertThat(confirmResult.nextState().overlays().commandPaletteVisible()).isFalse();
    assertThat(confirmResult.nextState().focusTarget()).isEqualTo(FocusTarget.EXTENSION_SEARCH);
    assertThat(confirmResult.nextState().statusMessage())
        .isEqualTo("Focus moved to extensionSearch");
  }

  @Test
  void sharedJumpToFavoriteActionFocusesListAndEmitsEffect() {
    ReduceResult result =
        reducer.reduce(
            baseState(), new UiIntent.SharedActionIntent(CommandPaletteAction.JUMP_TO_FAVORITE));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.nextState().focusTarget()).isEqualTo(FocusTarget.EXTENSION_LIST);
    assertThat(result.nextState().statusMessage()).isEqualTo("Focus moved to extensionList");
    assertThat(result.effects())
        .containsExactly(
            new UiEffect.ExecuteExtensionCommand(UiIntent.ExtensionCommand.JUMP_TO_FAVORITE));
  }

  @Test
  void extensionCancelIntentUsesEscUnwindPriorityBeforeQuit() {
    UiState state =
        stateWithExtensionView(
            stateWithFocus(baseState(), FocusTarget.EXTENSION_SEARCH),
            new UiState.ExtensionView(1, 2, 1, true, true, "web", "Core", "rest", "", true, false));

    ReduceResult result = reducer.reduce(state, new UiIntent.ExtensionCancelIntent());

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects())
        .containsExactly(
            new UiEffect.ExecuteExtensionCommand(UiIntent.ExtensionCommand.CLEAR_SEARCH));
  }

  @Test
  void extensionCancelIntentFallsBackToListFocusWhenOnlySearchFocusRemains() {
    UiState state =
        stateWithExtensionView(
            stateWithFocus(baseState(), FocusTarget.EXTENSION_SEARCH),
            new UiState.ExtensionView(2, 2, 0, false, false, "", "", "", "", true, false));

    ReduceResult result = reducer.reduce(state, new UiIntent.ExtensionCancelIntent());

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects()).isEmpty();
    assertThat(result.nextState().focusTarget()).isEqualTo(FocusTarget.EXTENSION_LIST);
    assertThat(result.nextState().statusMessage()).isEqualTo("Focus moved to extensionList");
  }

  @Test
  void sharedCategoryActionsMoveFocusToListBeforeRunningExtensionEffect() {
    ReduceResult result =
        reducer.reduce(
            baseState(),
            new UiIntent.SharedActionIntent(CommandPaletteAction.CYCLE_CATEGORY_FILTER));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.nextState().focusTarget()).isEqualTo(FocusTarget.EXTENSION_LIST);
    assertThat(result.nextState().statusMessage()).isEqualTo("Focus moved to extensionList");
    assertThat(result.effects())
        .containsExactly(
            new UiEffect.ExecuteExtensionCommand(UiIntent.ExtensionCommand.CYCLE_CATEGORY_FILTER));
  }

  @Test
  void extensionStatusIntentPreservesExistingVisibleErrorWhileUpdatingStatus() {
    UiState state =
        baseState()
            .withSubmissionState(
                "Submit blocked", "groupId: invalid", "verbose", true, true, false, true);

    ReduceResult result =
        reducer.reduce(state, new UiIntent.ExtensionStatusIntent("Closed category: Core"));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects()).isEmpty();
    assertThat(result.nextState().statusMessage()).isEqualTo("Closed category: Core");
    assertThat(result.nextState().errorMessage()).isEqualTo("groupId: invalid");
    assertThat(result.nextState().showErrorDetails()).isTrue();
  }

  @Test
  void extensionStateUpdatedIntentMakesExtensionViewReducerOwned() {
    UiState.ExtensionView nextExtensions =
        new UiState.ExtensionView(
            1, 2, 1, true, false, "web", "Core", "rest", "io.quarkus:rest", false, false);
    UiState state =
        stateWithExtensionView(
            baseState(),
            new UiState.ExtensionView(7, 7, 0, false, false, "", "", "", "", true, true));

    ReduceResult result =
        reducer.reduce(state, new UiIntent.ExtensionStateUpdatedIntent(nextExtensions));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects()).isEmpty();
    assertThat(result.nextState().extensions()).isEqualTo(nextExtensions);
  }

  @Test
  void extensionInteractionIntentRoutesListMovementThroughEffect() {
    ReduceResult result =
        reducer.reduce(
            stateWithFocus(baseState(), FocusTarget.EXTENSION_LIST),
            new UiIntent.ExtensionInteractionIntent(KeyEvent.ofChar('j')));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects())
        .containsExactly(new UiEffect.ApplyExtensionNavigationKey(KeyEvent.ofChar('j')));
    assertThat(result.nextState())
        .isEqualTo(stateWithFocus(baseState(), FocusTarget.EXTENSION_LIST));
  }

  @Test
  void extensionInteractionIntentMovesSearchFocusToListOnDown() {
    ReduceResult result =
        reducer.reduce(
            stateWithFocus(baseState(), FocusTarget.EXTENSION_SEARCH),
            new UiIntent.ExtensionInteractionIntent(KeyEvent.ofKey(KeyCode.DOWN)));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects()).isEmpty();
    assertThat(result.nextState().focusTarget()).isEqualTo(FocusTarget.EXTENSION_LIST);
    assertThat(result.nextState().statusMessage()).isEqualTo("Focus moved to extensionList");
  }

  @Test
  void extensionInteractionIntentMovesTopListSelectionBackToSearch() {
    UiState state =
        stateWithExtensionView(
            stateWithFocus(baseState(), FocusTarget.EXTENSION_LIST),
            new UiState.ExtensionView(7, 7, 0, false, false, "", "", "", "", true, true));

    ReduceResult result =
        reducer.reduce(state, new UiIntent.ExtensionInteractionIntent(KeyEvent.ofChar('k')));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects())
        .containsExactly(new UiEffect.MoveTextInputCursorToEnd(FocusTarget.EXTENSION_SEARCH));
    assertThat(result.nextState().focusTarget()).isEqualTo(FocusTarget.EXTENSION_SEARCH);
    assertThat(result.nextState().statusMessage()).isEqualTo("Focus moved to extensionSearch");
  }

  @Test
  void extensionInteractionIntentTogglesSectionHeadersThroughSharedActionPath() {
    UiState state =
        stateWithExtensionView(
            stateWithFocus(baseState(), FocusTarget.EXTENSION_LIST),
            new UiState.ExtensionView(7, 7, 0, false, false, "", "", "", "", true, true));

    ReduceResult result =
        reducer.reduce(
            state, new UiIntent.ExtensionInteractionIntent(KeyEvent.ofKey(KeyCode.ENTER)));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.nextState().focusTarget()).isEqualTo(FocusTarget.EXTENSION_LIST);
    assertThat(result.nextState().statusMessage()).isEqualTo("Ready");
    assertThat(result.effects())
        .containsExactly(
            new UiEffect.ExecuteExtensionCommand(
                UiIntent.ExtensionCommand.TOGGLE_CATEGORY_AT_SELECTION));
  }

  @Test
  void extensionInteractionIntentTogglesSelectedExtensionWhenHeaderIsNotFocused() {
    UiState state =
        stateWithExtensionView(
            stateWithFocus(baseState(), FocusTarget.EXTENSION_LIST),
            new UiState.ExtensionView(
                7, 7, 0, false, false, "", "", "", "io.quarkus:quarkus-arc", false, false));

    ReduceResult result =
        reducer.reduce(
            state, new UiIntent.ExtensionInteractionIntent(KeyEvent.ofKey(KeyCode.ENTER)));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects())
        .containsExactly(
            new UiEffect.ExecuteExtensionCommand(
                UiIntent.ExtensionCommand.TOGGLE_SELECTION_AT_CURSOR));
  }

  @Test
  void extensionInteractionIntentIgnoresNonNavigationKeysOutsideExtensionShortcuts() {
    ReduceResult result =
        reducer.reduce(
            stateWithFocus(baseState(), FocusTarget.EXTENSION_LIST),
            new UiIntent.ExtensionInteractionIntent(KeyEvent.ofChar('z')));

    assertThat(result.action()).isEqualTo(UiAction.ignored());
    assertThat(result.effects()).isEmpty();
  }

  @Test
  void commandPaletteToggleUsesReducerStateForBlockedMessages() {
    UiState generationVisibleState =
        stateWithOverlayState(
            baseState(), new UiState.OverlayState(true, false, false, false), 0, "Ready");

    ReduceResult result =
        reducer.reduce(
            generationVisibleState,
            new UiIntent.CommandPaletteIntent(
                new UiIntent.CommandPaletteCommand.ToggleVisibility()));

    assertThat(result.nextState().overlays().commandPaletteVisible()).isFalse();
    assertThat(result.nextState().statusMessage())
        .isEqualTo("Generation in progress. Press Esc to cancel.");
  }

  @Test
  void helpOverlayToggleOpensAndClosesOverlaysThroughReducerState() {
    UiState paletteVisibleState =
        stateWithOverlayState(
            baseState(), new UiState.OverlayState(false, true, false, false), 4, "Ready");

    ReduceResult openResult =
        reducer.reduce(
            paletteVisibleState,
            new UiIntent.HelpOverlayIntent(new UiIntent.HelpOverlayCommand.ToggleVisibility()));

    assertThat(openResult.action()).isEqualTo(UiAction.handled(false));
    assertThat(openResult.nextState().overlays().helpOverlayVisible()).isTrue();
    assertThat(openResult.nextState().overlays().commandPaletteVisible()).isFalse();
    assertThat(openResult.nextState().commandPaletteSelection()).isEqualTo(4);
    assertThat(openResult.nextState().statusMessage()).isEqualTo("Help opened");

    ReduceResult dismissResult =
        reducer.reduce(
            openResult.nextState(),
            new UiIntent.HelpOverlayIntent(new UiIntent.HelpOverlayCommand.Dismiss()));

    assertThat(dismissResult.nextState().overlays().helpOverlayVisible()).isFalse();
    assertThat(dismissResult.nextState().statusMessage()).isEqualTo("Help closed");
  }

  @Test
  void helpOverlayToggleUsesReducerStateForBlockedMessages() {
    UiState postGenerationVisibleState =
        stateWithOverlayState(
            postGenerationVisibleState(),
            new UiState.OverlayState(false, false, false, false),
            0,
            "Ready");

    ReduceResult result =
        reducer.reduce(
            postGenerationVisibleState,
            new UiIntent.HelpOverlayIntent(new UiIntent.HelpOverlayCommand.ToggleVisibility()));

    assertThat(result.nextState().overlays().helpOverlayVisible()).isFalse();
    assertThat(result.nextState().statusMessage()).isEqualTo("Post-generation actions are open.");
  }

  @Test
  void metadataIntentIsIgnoredWhenSelectorHasNoOptions() {
    UiState noOptionsState = stateWithFocus(baseState(), FocusTarget.BUILD_TOOL);

    ReduceResult result =
        reducer.reduce(
            noOptionsState,
            new UiIntent.MetadataInputIntent(
                KeyEvent.ofKey(KeyCode.LEFT), FocusTarget.BUILD_TOOL, false));

    assertThat(result.action()).isEqualTo(UiAction.ignored());
    assertThat(result.effects()).isEmpty();
  }

  @Test
  void metadataIntentWithOptionsProducesSelectorEffect() {
    ReduceResult result =
        reducer.reduce(
            stateWithFocus(baseState(), FocusTarget.BUILD_TOOL),
            new UiIntent.MetadataInputIntent(
                KeyEvent.ofKey(KeyCode.LEFT), FocusTarget.BUILD_TOOL, true));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects()).hasSize(1);
    assertThat(result.effects().getFirst()).isInstanceOf(UiEffect.ApplyMetadataSelectorKey.class);
  }

  @Test
  void textInputIntentWithSupportedKeyProducesApplyKeyEffect() {
    ReduceResult result =
        reducer.reduce(
            stateWithFocus(baseState(), FocusTarget.ARTIFACT_ID),
            new UiIntent.TextInputIntent(KeyEvent.ofChar('a'), FocusTarget.ARTIFACT_ID));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects()).hasSize(1);
    assertThat(result.effects().getFirst()).isInstanceOf(UiEffect.ApplyTextInputKey.class);
  }

  @Test
  void metadataIntentUsesReducerFocusInsteadOfIntentFocus() {
    KeyEvent keyEvent = KeyEvent.ofKey(KeyCode.LEFT);

    ReduceResult result =
        reducer.reduce(
            stateWithFocus(baseState(), FocusTarget.BUILD_TOOL),
            new UiIntent.MetadataInputIntent(keyEvent, FocusTarget.JAVA_VERSION, true));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects())
        .containsExactly(new UiEffect.ApplyMetadataSelectorKey(FocusTarget.BUILD_TOOL, keyEvent));
  }

  @Test
  void textInputIntentUsesReducerFocusInsteadOfIntentFocus() {
    KeyEvent keyEvent = KeyEvent.ofChar('a');

    ReduceResult result =
        reducer.reduce(
            stateWithFocus(baseState(), FocusTarget.ARTIFACT_ID),
            new UiIntent.TextInputIntent(keyEvent, FocusTarget.VERSION));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects())
        .containsExactly(new UiEffect.ApplyTextInputKey(FocusTarget.ARTIFACT_ID, keyEvent));
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
  void sharedFocusExtensionSearchClearsValidationBlockThroughReducerState() {
    UiState blockedState =
        invalidConfiguredState()
            .withSubmissionState(
                "Submit blocked", "groupId: must not be blank", "verbose", true, true, false, true);

    ReduceResult result =
        reducer.reduce(
            blockedState,
            new UiIntent.SharedActionIntent(CommandPaletteAction.FOCUS_EXTENSION_SEARCH));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects())
        .containsExactly(new UiEffect.MoveTextInputCursorToEnd(FocusTarget.EXTENSION_SEARCH));
    assertThat(result.nextState().focusTarget()).isEqualTo(FocusTarget.EXTENSION_SEARCH);
    assertThat(result.nextState().statusMessage()).isEqualTo("Focus moved to extensionSearch");
    assertThat(result.nextState().errorMessage()).isEmpty();
    assertThat(result.nextState().showErrorDetails()).isFalse();
    assertThat(result.nextState().submitBlockedByValidation()).isFalse();
    assertThat(result.nextState().verboseErrorDetails()).isEqualTo("verbose");
  }

  @Test
  void sharedFocusExtensionListPreservesTargetConflictStateWhileClearingVisibleError() {
    UiState blockedState =
        baseState()
            .withSubmissionState(
                "Submit blocked",
                "Output directory already exists",
                "verbose",
                true,
                false,
                true,
                true);

    ReduceResult result =
        reducer.reduce(
            blockedState,
            new UiIntent.SharedActionIntent(CommandPaletteAction.FOCUS_EXTENSION_LIST));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects()).isEmpty();
    assertThat(result.nextState().focusTarget()).isEqualTo(FocusTarget.EXTENSION_LIST);
    assertThat(result.nextState().statusMessage()).isEqualTo("Focus moved to extensionList");
    assertThat(result.nextState().errorMessage()).isEmpty();
    assertThat(result.nextState().showErrorDetails()).isFalse();
    assertThat(result.nextState().submitBlockedByTargetConflict()).isTrue();
    assertThat(result.nextState().verboseErrorDetails()).isEqualTo("verbose");
  }

  @Test
  void unrelatedIntentReturnsIgnoredWithoutEffects() {
    ReduceResult result =
        reducer.reduce(
            baseState(),
            new UiIntent.MetadataInputIntent(KeyEvent.ofChar('x'), FocusTarget.SUBMIT, false));

    assertThat(result.action()).isEqualTo(UiAction.ignored());
    assertThat(result.effects()).isEmpty();
  }

  private static UiState baseState() {
    return UiControllerTestHarness.controller().uiState();
  }

  private static UiState configuredState(int selectedExtensionCount) {
    return stateWithSelectedCount(
        UiControllerTestHarness.controller(
                UiScheduler.immediate(),
                java.time.Duration.ZERO,
                (generationRequest, outputDirectory, cancelled, progressListener) -> null)
            .uiState(),
        selectedExtensionCount);
  }

  private static UiState invalidConfiguredState() {
    CoreTuiController controller =
        UiControllerTestHarness.controller(
            UiScheduler.immediate(),
            java.time.Duration.ZERO,
            (generationRequest, outputDirectory, cancelled, progressListener) -> null);
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.GROUP_ID);
    int groupIdLength = controller.request().groupId().length();
    for (int i = 0; i < groupIdLength; i++) {
      controller.onEvent(KeyEvent.ofKey(KeyCode.BACKSPACE));
    }
    return controller.uiState();
  }

  private static UiState validState(UiState referenceState) {
    return stateWithValidation(referenceState, baseState().validation());
  }

  private static UiState stateWithFocus(UiState state, FocusTarget focusTarget) {
    return copyState(
        state,
        focusTarget,
        state.validation(),
        state.extensions(),
        state.overlays(),
        state.commandPaletteSelection(),
        state.statusMessage());
  }

  private static UiState stateWithSelectedCount(UiState state, int selectedExtensionCount) {
    return copyState(
        state,
        state.focusTarget(),
        state.validation(),
        new UiState.ExtensionView(
            state.extensions().filteredCount(),
            state.extensions().totalCount(),
            selectedExtensionCount,
            state.extensions().favoritesOnlyEnabled(),
            state.extensions().selectedOnlyEnabled(),
            state.extensions().activePresetFilterName(),
            state.extensions().activeCategoryFilterTitle(),
            state.extensions().searchQuery(),
            state.extensions().focusedExtensionId(),
            state.extensions().listSelectionAtTop(),
            state.extensions().categoryHeaderSelected()),
        state.overlays(),
        state.commandPaletteSelection(),
        state.statusMessage());
  }

  private static UiState stateWithValidation(
      UiState state, dev.ayagmar.quarkusforge.domain.ValidationReport validation) {
    return copyState(
        state,
        state.focusTarget(),
        validation,
        state.extensions(),
        state.overlays(),
        state.commandPaletteSelection(),
        state.statusMessage());
  }

  private static UiState stateWithExtensionView(UiState state, UiState.ExtensionView extensions) {
    return copyState(
        state,
        state.focusTarget(),
        state.validation(),
        extensions,
        state.overlays(),
        state.commandPaletteSelection(),
        state.statusMessage());
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

  private static int postGenerationActionIndex(PostGenerationExitAction action) {
    return postGenerationVisibleState().postGeneration().actions().stream()
        .map(UiTextConstants.PostGenerationAction::action)
        .toList()
        .indexOf(action);
  }

  private static UiState stateWithOverlayState(
      UiState state,
      UiState.OverlayState overlays,
      int commandPaletteSelection,
      String statusMessage) {
    return copyState(
        state,
        state.focusTarget(),
        state.validation(),
        state.extensions(),
        overlays,
        commandPaletteSelection,
        statusMessage);
  }

  private static UiState copyState(
      UiState state,
      FocusTarget focusTarget,
      dev.ayagmar.quarkusforge.domain.ValidationReport validation,
      UiState.ExtensionView extensions,
      UiState.OverlayState overlays,
      int commandPaletteSelection,
      String statusMessage) {
    return new UiState(
        state.request(),
        validation,
        focusTarget,
        statusMessage,
        state.errorMessage(),
        state.verboseErrorDetails(),
        state.showErrorDetails(),
        state.submitRequested(),
        state.submitBlockedByValidation(),
        state.submitBlockedByTargetConflict(),
        commandPaletteSelection,
        overlays,
        state.catalogLoad(),
        state.postGeneration(),
        extensions);
  }
}
