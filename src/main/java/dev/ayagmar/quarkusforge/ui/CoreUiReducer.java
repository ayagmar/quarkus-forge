package dev.ayagmar.quarkusforge.ui;

import dev.tamboui.tui.event.KeyEvent;
import java.util.List;

/** Default reducer implementation for migrated UI state-machine slices. */
final class CoreUiReducer implements UiReducer {

  @Override
  public ReduceResult reduce(UiState state, UiIntent intent) {
    return switch (intent) {
      case UiIntent.PostGenerationIntent postGenerationIntent ->
          reducePostGeneration(state, postGenerationIntent.command());
      case UiIntent.CatalogLoadRequestedIntent loadIntent ->
          new ReduceResult(
              state,
              List.of(new UiEffect.StartCatalogLoad(loadIntent.loader())),
              UiAction.handled(false));
      case UiIntent.CatalogReloadRequestedIntent _ ->
          new ReduceResult(
              state, List.of(new UiEffect.RequestCatalogReload()), UiAction.handled(false));
      case UiIntent.CatalogLoadStartedIntent startedIntent ->
          new ReduceResult(
              state.withCatalogLoad(
                  new UiState.CatalogLoadView(startedIntent.nextState()),
                  new UiState.StartupOverlayView(
                      startedIntent.startupOverlayVisible(), state.startupOverlay().statusLines()),
                  "Loading extension catalog...",
                  state.errorMessage(),
                  state.verboseErrorDetails(),
                  state.showErrorDetails()),
              List.of(new UiEffect.RequestAsyncRepaint()),
              UiAction.handled(false));
      case UiIntent.CatalogLoadCancelledIntent cancelledIntent ->
          new ReduceResult(
              state.withCatalogLoad(
                  new UiState.CatalogLoadView(cancelledIntent.nextState()),
                  new UiState.StartupOverlayView(
                      cancelledIntent.startupOverlayVisible(),
                      state.startupOverlay().statusLines()),
                  state.statusMessage(),
                  state.errorMessage(),
                  state.verboseErrorDetails(),
                  state.showErrorDetails()),
              List.of(),
              UiAction.handled(false));
      case UiIntent.CatalogReloadUnavailableIntent _ ->
          new ReduceResult(
              state.withStatusAndError("Catalog reload unavailable", state.errorMessage()),
              List.of(),
              UiAction.handled(false));
      case UiIntent.CatalogLoadSucceededIntent succeededIntent ->
          new ReduceResult(
              state.withCatalogLoad(
                  new UiState.CatalogLoadView(succeededIntent.success().nextState()),
                  new UiState.StartupOverlayView(
                      succeededIntent.startupOverlayVisible(),
                      state.startupOverlay().statusLines()),
                  succeededIntent.success().statusMessage(),
                  "",
                  "",
                  false),
              List.of(
                  new UiEffect.ApplyCatalogLoadSuccess(succeededIntent.success()),
                  new UiEffect.RequestAsyncRepaint()),
              UiAction.handled(false));
      case UiIntent.CatalogLoadFailedIntent failedIntent ->
          new ReduceResult(
              state.withCatalogLoad(
                  new UiState.CatalogLoadView(failedIntent.failure().nextState()),
                  new UiState.StartupOverlayView(
                      failedIntent.startupOverlayVisible(), state.startupOverlay().statusLines()),
                  failedIntent.failure().statusMessage(),
                  failedIntent.failure().errorMessage(),
                  failedIntent.failure().errorMessage(),
                  false),
              List.of(new UiEffect.RequestAsyncRepaint()),
              UiAction.handled(false));
      case UiIntent.StartupOverlayVisibilityIntent visibilityIntent ->
          new ReduceResult(
              state.withStartupOverlay(
                  new UiState.StartupOverlayView(
                      visibilityIntent.visible(), state.startupOverlay().statusLines())),
              List.of(),
              UiAction.handled(false));
      case UiIntent.SubmitRequestedIntent submitIntent ->
          reduceSubmitRequest(state, submitIntent.context());
      case UiIntent.SubmitEditRecoveryIntent recoveryIntent ->
          reduceSubmitRecovery(state, recoveryIntent.context());
      case UiIntent.CancelGenerationIntent _ ->
          new ReduceResult(
              state,
              List.of(new UiEffect.RequestGenerationCancellation()),
              UiAction.handled(false));
      case UiIntent.GenerationProgressIntent _ -> {
        yield new ReduceResult(
            state.withSubmissionState(
                state.statusMessage(), "", "", state.submitRequested(), false, false, false),
            List.of(),
            UiAction.handled(false));
      }
      case UiIntent.GenerationSuccessIntent successIntent ->
          new ReduceResult(
              state
                  .withSubmissionState(
                      "Generation succeeded: " + successIntent.generatedPath(),
                      "",
                      "",
                      state.submitRequested(),
                      false,
                      false,
                      false)
                  .withPostGeneration(
                      successPostGenerationState(
                          state.postGeneration(),
                          successIntent.generatedPath(),
                          successIntent.nextCommand())),
              List.of(new UiEffect.RequestAsyncRepaint()),
              UiAction.handled(false));
      case UiIntent.GenerationCancelledIntent _ ->
          new ReduceResult(
              state
                  .withSubmissionState(
                      "Generation cancelled. Update inputs and press Enter to retry.",
                      "",
                      "",
                      state.submitRequested(),
                      false,
                      false,
                      false)
                  .withPostGeneration(hiddenPostGenerationState(state.postGeneration())),
              List.of(new UiEffect.RequestAsyncRepaint()),
              UiAction.handled(false));
      case UiIntent.GenerationFailedIntent failedIntent ->
          new ReduceResult(
              state
                  .withSubmissionState(
                      "Generation failed.",
                      failedIntent.userErrorMessage(),
                      failedIntent.verboseErrorDetails(),
                      state.submitRequested(),
                      false,
                      false,
                      false)
                  .withPostGeneration(hiddenPostGenerationState(state.postGeneration())),
              List.of(new UiEffect.RequestAsyncRepaint()),
              UiAction.handled(false));
      case UiIntent.GenerationCancellationRequestedIntent _ ->
          new ReduceResult(
              state.withSubmissionState(
                  "Cancellation requested. Waiting for cleanup...",
                  "",
                  "",
                  state.submitRequested(),
                  false,
                  false,
                  false),
              List.of(),
              UiAction.handled(false));
      case UiIntent.FocusNavigationIntent navigationIntent ->
          reduceFocusNavigation(state, navigationIntent.keyEvent(), navigationIntent.focusTarget());
      case UiIntent.MetadataInputIntent metadataIntent ->
          reduceMetadataInput(state, metadataIntent.keyEvent(), metadataIntent.focusTarget());
      case UiIntent.TextInputIntent textInputIntent ->
          reduceTextInput(state, textInputIntent.keyEvent(), textInputIntent.focusTarget());
      case UiIntent.ToggleErrorDetailsIntent toggleIntent ->
          reduceToggleErrorDetails(state, toggleIntent.activeErrorPresent());
      default -> new ReduceResult(state, List.of(), UiAction.ignored());
    };
  }

  private static ReduceResult reduceSubmitRequest(
      UiState state, UiIntent.SubmitRequestContext context) {
    UiState preparedState =
        state.withPostGeneration(resetPostGenerationState(state.postGeneration()));
    String firstValidationError = UiIntent.firstValidationError(state.validation());
    if (!firstValidationError.isBlank()) {
      FocusTarget firstInvalidTarget = ValidationFocusTargets.firstInvalid(state.validation());
      FocusTarget nextFocusTarget =
          firstInvalidTarget == null ? state.focusTarget() : firstInvalidTarget;
      int issueCount = state.validation().errors().size();
      String statusMessage =
          firstInvalidTarget == null
              ? "Submit blocked: invalid input ("
                  + issueCount
                  + " issue"
                  + (issueCount == 1 ? "" : "s")
                  + ")"
              : "Submit blocked: fix "
                  + UiFocusTargets.nameOf(firstInvalidTarget)
                  + " ("
                  + issueCount
                  + " issue"
                  + (issueCount == 1 ? "" : "s")
                  + ")";
      return new ReduceResult(
          preparedState.withSubmitFeedback(
              nextFocusTarget, statusMessage, firstValidationError, true, false),
          List.of(
              new UiEffect.PrepareForGeneration(),
              new UiEffect.TransitionGenerationState(CoreTuiController.GenerationState.VALIDATING),
              new UiEffect.TransitionGenerationState(CoreTuiController.GenerationState.ERROR)),
          UiAction.handled(false));
    }
    if (context.hasTargetConflict()) {
      return new ReduceResult(
          preparedState.withSubmitFeedback(
              FocusTarget.OUTPUT_DIR,
              "Submit blocked: target folder exists (change output/artifact)",
              context.targetConflictErrorMessage(),
              false,
              true),
          List.of(
              new UiEffect.PrepareForGeneration(),
              new UiEffect.TransitionGenerationState(CoreTuiController.GenerationState.VALIDATING),
              new UiEffect.TransitionGenerationState(CoreTuiController.GenerationState.ERROR)),
          UiAction.handled(false));
    }
    if (!context.generationConfigured()) {
      return new ReduceResult(
          preparedState.withSubmissionState(
              "Submit requested with "
                  + context.selectedExtensionCount()
                  + " extension(s), but generation service is not configured.",
              "",
              "",
              true,
              false,
              false,
              false),
          List.of(
              new UiEffect.PrepareForGeneration(),
              new UiEffect.TransitionGenerationState(CoreTuiController.GenerationState.VALIDATING),
              new UiEffect.TransitionGenerationState(CoreTuiController.GenerationState.IDLE)),
          UiAction.handled(false));
    }
    return new ReduceResult(
        preparedState.withSubmissionState(
            "Submit requested with " + context.selectedExtensionCount() + " extension(s)",
            "",
            "",
            true,
            false,
            false,
            false),
        List.of(
            new UiEffect.PrepareForGeneration(),
            new UiEffect.TransitionGenerationState(CoreTuiController.GenerationState.VALIDATING),
            new UiEffect.StartGeneration()),
        UiAction.handled(false));
  }

  private static ReduceResult reduceSubmitRecovery(
      UiState state, UiIntent.SubmitRecoveryContext context) {
    if (state.submitBlockedByTargetConflict() && context.targetConflictErrorMessage().isBlank()) {
      return new ReduceResult(
          state.withSubmissionState(
              "Target folder conflict resolved",
              "",
              state.verboseErrorDetails(),
              state.submitRequested(),
              false,
              false,
              false),
          List.of(),
          UiAction.handled(false));
    }
    if (state.submitBlockedByTargetConflict()) {
      return new ReduceResult(
          state.withSubmissionState(
              state.statusMessage(),
              context.targetConflictErrorMessage(),
              state.verboseErrorDetails(),
              state.submitRequested(),
              state.submitBlockedByValidation(),
              true,
              false),
          List.of(),
          UiAction.handled(false));
    }
    if (state.submitBlockedByValidation() && state.validation().isValid()) {
      return new ReduceResult(
          state.withSubmissionState(
              "Validation restored",
              "",
              state.verboseErrorDetails(),
              state.submitRequested(),
              false,
              false,
              false),
          List.of(),
          UiAction.handled(false));
    }
    if (state.submitBlockedByValidation()) {
      return new ReduceResult(
          state.withSubmissionState(
              state.statusMessage(),
              UiIntent.firstValidationError(state.validation()),
              state.verboseErrorDetails(),
              state.submitRequested(),
              true,
              false,
              false),
          List.of(),
          UiAction.handled(false));
    }
    return new ReduceResult(state, List.of(), UiAction.ignored());
  }

  private static ReduceResult reduceFocusNavigation(
      UiState state, KeyEvent keyEvent, FocusTarget focusTarget) {
    if (keyEvent.isFocusPrevious()) {
      return moveFocus(state, focusTarget, -1);
    }
    if (keyEvent.isFocusNext()) {
      return moveFocus(state, focusTarget, 1);
    }
    if (focusTarget == FocusTarget.SUBMIT) {
      if (UiKeyMatchers.isVimDownKey(keyEvent)) {
        return moveFocus(state, focusTarget, 1);
      }
      if (UiKeyMatchers.isVimUpKey(keyEvent)) {
        return moveFocus(state, focusTarget, -1);
      }
    }
    return new ReduceResult(state, List.of(), UiAction.ignored());
  }

  private static ReduceResult moveFocus(UiState state, FocusTarget focusTarget, int offset) {
    FocusTarget nextFocusTarget = UiFocusTargets.move(focusTarget, offset);
    return new ReduceResult(
        state.withFocusAndValidationFeedback(
            nextFocusTarget, "Focus moved to " + UiFocusTargets.nameOf(nextFocusTarget)),
        List.of(),
        UiAction.handled(false));
  }

  private static ReduceResult reduceMetadataInput(
      UiState state, KeyEvent keyEvent, FocusTarget focusTarget) {
    if (!MetadataSelectorManager.isSelectorFocus(focusTarget)) {
      return new ReduceResult(state, List.of(), UiAction.ignored());
    }
    if (!hasSelectorOptions(state, focusTarget)) {
      return new ReduceResult(state, List.of(), UiAction.ignored());
    }
    if (keyEvent.isLeft()
        || UiKeyMatchers.isVimLeftKey(keyEvent)
        || keyEvent.isUp()
        || UiKeyMatchers.isVimUpKey(keyEvent)
        || keyEvent.isRight()
        || UiKeyMatchers.isVimRightKey(keyEvent)
        || keyEvent.isDown()
        || UiKeyMatchers.isVimDownKey(keyEvent)
        || keyEvent.isHome()
        || keyEvent.isEnd()) {
      return new ReduceResult(
          state,
          List.of(new UiEffect.ApplyMetadataSelectorKey(focusTarget, keyEvent)),
          UiAction.handled(false));
    }
    return new ReduceResult(state, List.of(), UiAction.ignored());
  }

  private static ReduceResult reduceTextInput(
      UiState state, KeyEvent keyEvent, FocusTarget focusTarget) {
    if (!UiFocusPredicates.isTextInputFocus(focusTarget)
        || !UiTextInputKeys.isSupportedEditKey(keyEvent)) {
      return new ReduceResult(state, List.of(), UiAction.ignored());
    }
    return new ReduceResult(
        state,
        List.of(new UiEffect.ApplyTextInputKey(focusTarget, keyEvent)),
        UiAction.handled(false));
  }

  private static ReduceResult reducePostGeneration(
      UiState state, UiIntent.PostGenerationCommand command) {
    UiState.PostGenerationView postGeneration = state.postGeneration();
    return switch (command) {
      case UiIntent.PostGenerationCommand.Noop _ ->
          new ReduceResult(state, List.of(), UiAction.handled(false));
      case UiIntent.PostGenerationCommand.MoveActionSelection moveCommand ->
          new ReduceResult(
              state.withPostGeneration(
                  withActionSelection(
                      postGeneration, moveCommand.delta(), postGeneration.actions().size())),
              List.of(),
              UiAction.handled(false));
      case UiIntent.PostGenerationCommand.MoveGithubVisibilitySelection moveCommand ->
          new ReduceResult(
              state.withPostGeneration(
                  withGithubVisibilitySelection(
                      postGeneration,
                      moveCommand.delta(),
                      UiTextConstants.GITHUB_VISIBILITY_LABELS.size())),
              List.of(),
              UiAction.handled(false));
      case UiIntent.PostGenerationCommand.SelectActionIndex selectCommand ->
          executePostGenerationSelection(
              state, withExactActionSelection(postGeneration, selectCommand.index()));
      case UiIntent.PostGenerationCommand.SelectGithubVisibilityIndex selectCommand ->
          confirmGithubVisibilitySelection(
              state,
              new UiState.PostGenerationView(
                  postGeneration.visible(),
                  postGeneration.githubVisibilityVisible(),
                  postGeneration.actionSelection(),
                  selectCommand.index(),
                  postGeneration.actions(),
                  postGeneration.lastGeneratedProjectPath(),
                  postGeneration.lastGeneratedNextCommand(),
                  postGeneration.exitPlan()));
      case UiIntent.PostGenerationCommand.ConfirmSelection _ ->
          postGeneration.githubVisibilityVisible()
              ? confirmGithubVisibilitySelection(state, postGeneration)
              : executePostGenerationSelection(state, postGeneration);
      case UiIntent.PostGenerationCommand.CancelGithubVisibility _ ->
          new ReduceResult(
              state.withPostGeneration(
                  new UiState.PostGenerationView(
                      postGeneration.visible(),
                      false,
                      postGeneration.actionSelection(),
                      0,
                      postGeneration.actions(),
                      postGeneration.lastGeneratedProjectPath(),
                      postGeneration.lastGeneratedNextCommand(),
                      postGeneration.exitPlan())),
              List.of(),
              UiAction.handled(false));
      case UiIntent.PostGenerationCommand.Quit _ ->
          closePostGenerationWithExitPlan(
              state,
              postGeneration,
              new PostGenerationExitPlan(
                  PostGenerationExitAction.QUIT,
                  postGeneration.lastGeneratedProjectPath(),
                  postGeneration.lastGeneratedNextCommand(),
                  GitHubVisibility.PRIVATE,
                  null));
    };
  }

  private static ReduceResult reduceToggleErrorDetails(UiState state, boolean activeErrorPresent) {
    if (!activeErrorPresent) {
      return new ReduceResult(
          state.withShowErrorDetails(false, "No error details available"),
          List.of(),
          UiAction.handled(false));
    }
    boolean nextShowErrorDetails = !state.showErrorDetails();
    return new ReduceResult(
        state.withShowErrorDetails(
            nextShowErrorDetails,
            nextShowErrorDetails ? "Expanded error details" : "Collapsed error details"),
        List.of(),
        UiAction.handled(false));
  }

  private static boolean hasSelectorOptions(UiState state, FocusTarget focusTarget) {
    MetadataPanelSnapshot.SelectorInfo selectorInfo =
        switch (focusTarget) {
          case PLATFORM_STREAM -> state.metadataPanel().platformStreamInfo();
          case BUILD_TOOL -> state.metadataPanel().buildToolInfo();
          case JAVA_VERSION -> state.metadataPanel().javaVersionInfo();
          default -> MetadataPanelSnapshot.SelectorInfo.EMPTY;
        };
    return selectorInfo.totalOptions() > 0;
  }

  private static UiState.PostGenerationView withActionSelection(
      UiState.PostGenerationView state, int delta, int size) {
    int nextSelection =
        size > 0 ? Math.floorMod(state.actionSelection() + delta, size) : state.actionSelection();
    return new UiState.PostGenerationView(
        state.visible(),
        state.githubVisibilityVisible(),
        nextSelection,
        state.githubVisibilitySelection(),
        state.actions(),
        state.lastGeneratedProjectPath(),
        state.lastGeneratedNextCommand(),
        state.exitPlan());
  }

  private static UiState.PostGenerationView withGithubVisibilitySelection(
      UiState.PostGenerationView state, int delta, int size) {
    int nextSelection =
        size > 0
            ? Math.floorMod(state.githubVisibilitySelection() + delta, size)
            : state.githubVisibilitySelection();
    return new UiState.PostGenerationView(
        state.visible(),
        state.githubVisibilityVisible(),
        state.actionSelection(),
        nextSelection,
        state.actions(),
        state.lastGeneratedProjectPath(),
        state.lastGeneratedNextCommand(),
        state.exitPlan());
  }

  private static UiState.PostGenerationView withExactActionSelection(
      UiState.PostGenerationView state, int index) {
    return new UiState.PostGenerationView(
        state.visible(),
        state.githubVisibilityVisible(),
        index,
        state.githubVisibilitySelection(),
        state.actions(),
        state.lastGeneratedProjectPath(),
        state.lastGeneratedNextCommand(),
        state.exitPlan());
  }

  private static ReduceResult executePostGenerationSelection(
      UiState state, UiState.PostGenerationView postGeneration) {
    UiTextConstants.PostGenerationAction selected =
        (postGeneration.actionSelection() >= 0
                && postGeneration.actionSelection() < postGeneration.actions().size())
            ? postGeneration.actions().get(postGeneration.actionSelection())
            : null;
    PostGenerationExitAction action =
        selected != null ? selected.action() : PostGenerationExitAction.QUIT;
    if (action == PostGenerationExitAction.EXPORT_RECIPE_LOCK) {
      return new ReduceResult(
          state.withPostGeneration(postGeneration),
          List.of(new UiEffect.ExportRecipeAndLock()),
          UiAction.handled(false));
    }
    if (action == PostGenerationExitAction.PUBLISH_GITHUB) {
      return new ReduceResult(
          state.withPostGeneration(
              new UiState.PostGenerationView(
                  true,
                  true,
                  postGeneration.actionSelection(),
                  0,
                  postGeneration.actions(),
                  postGeneration.lastGeneratedProjectPath(),
                  postGeneration.lastGeneratedNextCommand(),
                  null)),
          List.of(),
          UiAction.handled(false));
    }
    if (action == PostGenerationExitAction.GENERATE_AGAIN) {
      return new ReduceResult(
          state
              .withSubmissionState(
                  "Ready for next generation", "", "", state.submitRequested(), false, false, false)
              .withPostGeneration(resetPostGenerationState(postGeneration)),
          List.of(new UiEffect.PrepareForGeneration()),
          UiAction.handled(false));
    }
    return closePostGenerationWithExitPlan(
        state,
        postGeneration,
        new PostGenerationExitPlan(
            action,
            postGeneration.lastGeneratedProjectPath(),
            postGeneration.lastGeneratedNextCommand(),
            GitHubVisibility.PRIVATE,
            selected != null ? selected.ideCommand() : null));
  }

  private static ReduceResult confirmGithubVisibilitySelection(
      UiState state, UiState.PostGenerationView postGeneration) {
    return closePostGenerationWithExitPlan(
        state,
        postGeneration,
        new PostGenerationExitPlan(
            PostGenerationExitAction.PUBLISH_GITHUB,
            postGeneration.lastGeneratedProjectPath(),
            postGeneration.lastGeneratedNextCommand(),
            postGeneration.selectedGithubVisibility(),
            null));
  }

  private static ReduceResult closePostGenerationWithExitPlan(
      UiState state, UiState.PostGenerationView postGeneration, PostGenerationExitPlan exitPlan) {
    return new ReduceResult(
        state.withPostGeneration(
            new UiState.PostGenerationView(
                false,
                false,
                0,
                0,
                postGeneration.actions(),
                postGeneration.lastGeneratedProjectPath(),
                postGeneration.lastGeneratedNextCommand(),
                exitPlan)),
        List.of(new UiEffect.CancelPendingAsync()),
        UiAction.handled(true));
  }

  private static UiState.PostGenerationView successPostGenerationState(
      UiState.PostGenerationView currentState,
      java.nio.file.Path generatedPath,
      String nextCommand) {
    return new UiState.PostGenerationView(
        true, false, 0, 0, currentState.actions(), generatedPath, nextCommand, null);
  }

  private static UiState.PostGenerationView hiddenPostGenerationState(
      UiState.PostGenerationView currentState) {
    return new UiState.PostGenerationView(
        false,
        false,
        currentState.actionSelection(),
        0,
        currentState.actions(),
        currentState.lastGeneratedProjectPath(),
        currentState.lastGeneratedNextCommand(),
        null);
  }

  private static UiState.PostGenerationView resetPostGenerationState(
      UiState.PostGenerationView currentState) {
    return new UiState.PostGenerationView(
        false, false, 0, 0, currentState.actions(), null, "", null);
  }
}
