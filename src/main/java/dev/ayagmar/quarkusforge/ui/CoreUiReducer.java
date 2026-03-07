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
                      state
                          .postGeneration()
                          .afterSuccess(
                              successIntent.generatedPath(), successIntent.nextCommand())),
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
                  .withPostGeneration(state.postGeneration().hidden()),
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
                  .withPostGeneration(state.postGeneration().hidden()),
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
      case UiIntent.CommandPaletteIntent commandPaletteIntent ->
          reduceCommandPalette(state, commandPaletteIntent.command());
      case UiIntent.HelpOverlayIntent helpOverlayIntent ->
          reduceHelpOverlay(state, helpOverlayIntent.command());
      case UiIntent.SharedActionIntent sharedActionIntent ->
          reduceSharedAction(state, sharedActionIntent.action());
      case UiIntent.ExtensionCancelIntent _ -> reduceExtensionCancel(state);
      case UiIntent.ExtensionCommandIntent extensionIntent ->
          reduceExtensionCommand(state, extensionIntent.command());
      case UiIntent.ExtensionStatusIntent extensionStatusIntent ->
          new ReduceResult(
              state.withStatusMessage(extensionStatusIntent.statusMessage()),
              List.of(),
              UiAction.handled(false));
      case UiIntent.StatusMessageIntent statusMessageIntent ->
          new ReduceResult(
              state.withStatusMessage(statusMessageIntent.statusMessage()),
              List.of(),
              UiAction.handled(false));
      case UiIntent.FocusStatusIntent focusStatusIntent ->
          new ReduceResult(
              state.withFocusAndStatus(
                  focusStatusIntent.focusTarget(), focusStatusIntent.statusMessage()),
              List.of(),
              UiAction.handled(false));
      case UiIntent.FormStateUpdatedIntent formStateUpdatedIntent ->
          new ReduceResult(
              state.withRequestAndValidation(
                  formStateUpdatedIntent.request(), formStateUpdatedIntent.validation()),
              List.of(),
              UiAction.handled(false));
      case UiIntent.ExtensionStateUpdatedIntent extensionStateUpdatedIntent ->
          new ReduceResult(
              state.withExtensions(extensionStateUpdatedIntent.extensions()),
              List.of(),
              UiAction.handled(false));
      case UiIntent.ExtensionNavigationIntent navigationIntent ->
          reduceExtensionNavigation(state, navigationIntent.keyEvent());
      case UiIntent.FocusNavigationIntent navigationIntent ->
          reduceFocusNavigation(state, navigationIntent.keyEvent());
      case UiIntent.MetadataInputIntent metadataIntent ->
          reduceMetadataInput(state, metadataIntent.keyEvent());
      case UiIntent.TextInputIntent textInputIntent ->
          reduceTextInput(state, textInputIntent.keyEvent());
      case UiIntent.ToggleErrorDetailsIntent _ ->
          reduceToggleErrorDetails(state, hasActiveError(state));
      default -> new ReduceResult(state, List.of(), UiAction.ignored());
    };
  }

  private static ReduceResult reduceSubmitRequest(
      UiState state, UiIntent.SubmitRequestContext context) {
    UiState preparedState = state.withPostGeneration(state.postGeneration().reset());
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

  private static ReduceResult reduceCommandPalette(
      UiState state, UiIntent.CommandPaletteCommand command) {
    return switch (command) {
      case UiIntent.CommandPaletteCommand.ToggleVisibility _ -> {
        if (state.overlays().commandPaletteVisible()) {
          yield new ReduceResult(
              closeCommandPalette(state, "Command palette closed"),
              List.of(),
              UiAction.handled(false));
        }
        if (state.overlays().generationVisible()) {
          yield new ReduceResult(
              state.withStatusAndError(
                  "Generation in progress. Press Esc to cancel.", state.errorMessage()),
              List.of(),
              UiAction.handled(false));
        }
        if (state.overlays().postGenerationVisible()) {
          yield new ReduceResult(
              state.withStatusAndError("Post-generation actions are open.", state.errorMessage()),
              List.of(),
              UiAction.handled(false));
        }
        yield new ReduceResult(
            state.withOverlayState(true, false, 0, "Command palette opened"),
            List.of(),
            UiAction.handled(false));
      }
      case UiIntent.CommandPaletteCommand.Dismiss _ -> {
        if (!state.overlays().commandPaletteVisible()) {
          yield new ReduceResult(state, List.of(), UiAction.ignored());
        }
        yield new ReduceResult(
            closeCommandPalette(state, "Command palette closed"),
            List.of(),
            UiAction.handled(false));
      }
      case UiIntent.CommandPaletteCommand.MoveSelection moveSelection -> {
        if (!state.overlays().commandPaletteVisible()) {
          yield new ReduceResult(state, List.of(), UiAction.ignored());
        }
        int size = UiTextConstants.COMMAND_PALETTE_ENTRIES.size();
        int nextSelection =
            size == 0
                ? state.commandPaletteSelection()
                : Math.floorMod(state.commandPaletteSelection() + moveSelection.delta(), size);
        yield new ReduceResult(
            state.withOverlayState(
                true, state.overlays().helpOverlayVisible(), nextSelection, state.statusMessage()),
            List.of(),
            UiAction.handled(false));
      }
      case UiIntent.CommandPaletteCommand.JumpHome _ -> {
        if (!state.overlays().commandPaletteVisible()) {
          yield new ReduceResult(state, List.of(), UiAction.ignored());
        }
        yield new ReduceResult(
            state.withOverlayState(
                true, state.overlays().helpOverlayVisible(), 0, state.statusMessage()),
            List.of(),
            UiAction.handled(false));
      }
      case UiIntent.CommandPaletteCommand.JumpEnd _ -> {
        if (!state.overlays().commandPaletteVisible()) {
          yield new ReduceResult(state, List.of(), UiAction.ignored());
        }
        int lastSelection = Math.max(UiTextConstants.COMMAND_PALETTE_ENTRIES.size() - 1, 0);
        yield new ReduceResult(
            state.withOverlayState(
                true, state.overlays().helpOverlayVisible(), lastSelection, state.statusMessage()),
            List.of(),
            UiAction.handled(false));
      }
      case UiIntent.CommandPaletteCommand.SelectIndex selectIndex -> {
        if (!state.overlays().commandPaletteVisible()) {
          yield new ReduceResult(state, List.of(), UiAction.ignored());
        }
        int nextSelection =
            selectIndex.index() >= 0
                    && selectIndex.index() < UiTextConstants.COMMAND_PALETTE_ENTRIES.size()
                ? selectIndex.index()
                : state.commandPaletteSelection();
        yield new ReduceResult(
            state.withOverlayState(
                true, state.overlays().helpOverlayVisible(), nextSelection, state.statusMessage()),
            List.of(),
            UiAction.handled(false));
      }
      case UiIntent.CommandPaletteCommand.ConfirmSelection _ -> {
        if (!state.overlays().commandPaletteVisible()) {
          yield new ReduceResult(state, List.of(), UiAction.ignored());
        }
        CommandPaletteAction selectedAction = selectedCommandPaletteAction(state);
        UiState closedState = closeCommandPalette(state, state.statusMessage());
        yield selectedAction == null
            ? new ReduceResult(closedState, List.of(), UiAction.handled(false))
            : reduceSharedAction(closedState, selectedAction);
      }
    };
  }

  private static ReduceResult reduceSharedAction(UiState state, CommandPaletteAction action) {
    return switch (action) {
      case FOCUS_EXTENSION_SEARCH ->
          new ReduceResult(
              state.withFocusAndValidationFeedback(
                  FocusTarget.EXTENSION_SEARCH, "Focus moved to extensionSearch"),
              List.of(new UiEffect.MoveTextInputCursorToEnd(FocusTarget.EXTENSION_SEARCH)),
              UiAction.handled(false));
      case FOCUS_EXTENSION_LIST ->
          new ReduceResult(
              state.withFocusAndValidationFeedback(
                  FocusTarget.EXTENSION_LIST, "Focus moved to extensionList"),
              List.of(),
              UiAction.handled(false));
      case TOGGLE_FAVORITES_FILTER ->
          reduceExtensionCommand(state, UiIntent.ExtensionCommand.TOGGLE_FAVORITES_FILTER);
      case TOGGLE_SELECTED_FILTER ->
          reduceExtensionCommand(state, UiIntent.ExtensionCommand.TOGGLE_SELECTED_FILTER);
      case CYCLE_PRESET_FILTER ->
          reduceExtensionCommand(state, UiIntent.ExtensionCommand.CYCLE_PRESET_FILTER);
      case CYCLE_CATEGORY_FILTER ->
          reduceExtensionCommandWithOptionalListFocus(
              state, UiIntent.ExtensionCommand.CYCLE_CATEGORY_FILTER);
      case TOGGLE_CATEGORY ->
          reduceExtensionCommandWithOptionalListFocus(
              state, UiIntent.ExtensionCommand.TOGGLE_CATEGORY_AT_SELECTION);
      case OPEN_ALL_CATEGORIES ->
          reduceExtensionCommandWithOptionalListFocus(
              state, UiIntent.ExtensionCommand.OPEN_ALL_CATEGORIES);
      case JUMP_TO_FAVORITE ->
          reduceExtensionCommandWithOptionalListFocus(
              state, UiIntent.ExtensionCommand.JUMP_TO_FAVORITE);
      case RELOAD_CATALOG ->
          new ReduceResult(
              state, List.of(new UiEffect.RequestCatalogReload()), UiAction.handled(false));
      case TOGGLE_ERROR_DETAILS -> reduceToggleErrorDetails(state, hasActiveError(state));
    };
  }

  private static ReduceResult reduceExtensionCancel(UiState state) {
    if (state.overlays().generationVisible()) {
      return new ReduceResult(state, List.of(), UiAction.ignored());
    }
    boolean extensionFocused =
        state.focusTarget() == FocusTarget.EXTENSION_SEARCH
            || state.focusTarget() == FocusTarget.EXTENSION_LIST;
    if (!extensionFocused) {
      return new ReduceResult(state, List.of(), UiAction.ignored());
    }
    if (!state.extensions().searchQuery().isBlank()) {
      return reduceExtensionCommand(state, UiIntent.ExtensionCommand.CLEAR_SEARCH);
    }
    if (state.extensions().favoritesOnlyEnabled()) {
      return reduceExtensionCommand(state, UiIntent.ExtensionCommand.DISABLE_FAVORITES_FILTER);
    }
    if (state.extensions().selectedOnlyEnabled()) {
      return reduceExtensionCommand(state, UiIntent.ExtensionCommand.DISABLE_SELECTED_FILTER);
    }
    if (!state.extensions().activePresetFilterName().isBlank()) {
      return reduceExtensionCommand(state, UiIntent.ExtensionCommand.CLEAR_PRESET_FILTER);
    }
    if (!state.extensions().activeCategoryFilterTitle().isBlank()) {
      return reduceExtensionCommand(state, UiIntent.ExtensionCommand.CLEAR_CATEGORY_FILTER);
    }
    if (state.focusTarget() == FocusTarget.EXTENSION_SEARCH) {
      return reduceExtensionPanelFocus(state, FocusTarget.EXTENSION_LIST);
    }
    return new ReduceResult(state, List.of(), UiAction.ignored());
  }

  private static ReduceResult reduceExtensionCommand(
      UiState state, UiIntent.ExtensionCommand command) {
    return new ReduceResult(
        state, List.of(new UiEffect.ExecuteExtensionCommand(command)), UiAction.handled(false));
  }

  private static ReduceResult reduceExtensionCommandWithOptionalListFocus(
      UiState state, UiIntent.ExtensionCommand command) {
    UiState focusedState = focusExtensionListForSharedAction(state);
    return new ReduceResult(
        focusedState,
        List.of(new UiEffect.ExecuteExtensionCommand(command)),
        UiAction.handled(false));
  }

  private static ReduceResult reduceExtensionNavigation(UiState state, KeyEvent keyEvent) {
    FocusTarget focusTarget = state.focusTarget();
    if (focusTarget != FocusTarget.EXTENSION_LIST || !isExtensionNavigationKey(keyEvent)) {
      return new ReduceResult(state, List.of(), UiAction.ignored());
    }
    return new ReduceResult(
        state,
        List.of(new UiEffect.ApplyExtensionNavigationKey(keyEvent)),
        UiAction.handled(false));
  }

  private static ReduceResult reduceHelpOverlay(
      UiState state, UiIntent.HelpOverlayCommand command) {
    return switch (command) {
      case UiIntent.HelpOverlayCommand.ToggleVisibility _ -> {
        if (state.overlays().helpOverlayVisible()) {
          yield new ReduceResult(
              closeHelpOverlay(state, "Help closed"), List.of(), UiAction.handled(false));
        }
        if (state.overlays().generationVisible()) {
          yield new ReduceResult(
              state.withStatusAndError(
                  "Generation in progress. Press Esc to cancel.", state.errorMessage()),
              List.of(),
              UiAction.handled(false));
        }
        if (state.overlays().postGenerationVisible()) {
          yield new ReduceResult(
              state.withStatusAndError("Post-generation actions are open.", state.errorMessage()),
              List.of(),
              UiAction.handled(false));
        }
        yield new ReduceResult(
            state.withOverlayState(false, true, state.commandPaletteSelection(), "Help opened"),
            List.of(),
            UiAction.handled(false));
      }
      case UiIntent.HelpOverlayCommand.Dismiss _ -> {
        if (!state.overlays().helpOverlayVisible()) {
          yield new ReduceResult(state, List.of(), UiAction.ignored());
        }
        yield new ReduceResult(
            closeHelpOverlay(state, "Help closed"), List.of(), UiAction.handled(false));
      }
    };
  }

  private static ReduceResult reduceFocusNavigation(UiState state, KeyEvent keyEvent) {
    FocusTarget focusTarget = state.focusTarget();
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

  private static ReduceResult reduceExtensionPanelFocus(UiState state, FocusTarget focusTarget) {
    if (focusTarget != FocusTarget.EXTENSION_SEARCH && focusTarget != FocusTarget.EXTENSION_LIST) {
      return new ReduceResult(state, List.of(), UiAction.ignored());
    }
    return new ReduceResult(
        state.withFocusAndValidationFeedback(
            focusTarget, "Focus moved to " + UiFocusTargets.nameOf(focusTarget)),
        List.of(),
        UiAction.handled(false));
  }

  private static ReduceResult moveFocus(UiState state, FocusTarget focusTarget, int offset) {
    FocusTarget nextFocusTarget = UiFocusTargets.move(focusTarget, offset);
    return new ReduceResult(
        state.withFocusAndValidationFeedback(
            nextFocusTarget, "Focus moved to " + UiFocusTargets.nameOf(nextFocusTarget)),
        List.of(),
        UiAction.handled(false));
  }

  private static UiState closeCommandPalette(UiState state, String nextStatusMessage) {
    return state.withOverlayState(
        false,
        state.overlays().helpOverlayVisible(),
        state.commandPaletteSelection(),
        nextStatusMessage);
  }

  private static CommandPaletteAction selectedCommandPaletteAction(UiState state) {
    if (UiTextConstants.COMMAND_PALETTE_ENTRIES.isEmpty()) {
      return null;
    }
    int selection = state.commandPaletteSelection();
    if (selection < 0 || selection >= UiTextConstants.COMMAND_PALETTE_ENTRIES.size()) {
      return null;
    }
    return UiTextConstants.COMMAND_PALETTE_ENTRIES.get(selection).action();
  }

  private static UiState closeHelpOverlay(UiState state, String nextStatusMessage) {
    return state.withOverlayState(
        state.overlays().commandPaletteVisible(),
        false,
        state.commandPaletteSelection(),
        nextStatusMessage);
  }

  private static UiState focusExtensionListForSharedAction(UiState state) {
    if (state.focusTarget() == FocusTarget.EXTENSION_LIST) {
      return state;
    }
    return state.withFocusAndValidationFeedback(
        FocusTarget.EXTENSION_LIST, "Focus moved to extensionList");
  }

  private static boolean isExtensionNavigationKey(KeyEvent keyEvent) {
    return keyEvent.isUp()
        || UiKeyMatchers.isVimUpKey(keyEvent)
        || keyEvent.isDown()
        || UiKeyMatchers.isVimDownKey(keyEvent)
        || keyEvent.isHome()
        || UiKeyMatchers.isVimHomeKey(keyEvent)
        || keyEvent.isEnd()
        || UiKeyMatchers.isVimEndKey(keyEvent);
  }

  private static ReduceResult reduceMetadataInput(UiState state, KeyEvent keyEvent) {
    FocusTarget focusTarget = state.focusTarget();
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

  private static ReduceResult reduceTextInput(UiState state, KeyEvent keyEvent) {
    FocusTarget focusTarget = state.focusTarget();
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
    if (command instanceof UiIntent.PostGenerationCommand.Noop) {
      return new ReduceResult(state, List.of(), UiAction.handled(false));
    }
    if (!postGeneration.visible()) {
      return new ReduceResult(state, List.of(), UiAction.handled(false));
    }
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
          selectCommand.index() >= 0 && selectCommand.index() < postGeneration.actions().size()
              ? executePostGenerationSelection(
                  state, withExactActionSelection(postGeneration, selectCommand.index()))
              : new ReduceResult(state, List.of(), UiAction.handled(false));
      case UiIntent.PostGenerationCommand.SelectGithubVisibilityIndex selectCommand ->
          selectCommand.index() >= 0
                  && selectCommand.index() < UiTextConstants.GITHUB_VISIBILITY_LABELS.size()
              ? confirmGithubVisibilitySelection(
                  state, postGeneration.withGithubVisibilitySelection(selectCommand.index()))
              : new ReduceResult(state, List.of(), UiAction.handled(false));
      case UiIntent.PostGenerationCommand.ConfirmSelection _ ->
          postGeneration.githubVisibilityVisible()
              ? confirmGithubVisibilitySelection(state, postGeneration)
              : executePostGenerationSelection(state, postGeneration);
      case UiIntent.PostGenerationCommand.CancelGithubVisibility _ ->
          new ReduceResult(
              state.withPostGeneration(postGeneration.hideGithubVisibilityMenu()),
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

  private static boolean hasActiveError(UiState state) {
    return !state.errorMessage().isBlank() || !state.catalogLoad().state().errorMessage().isBlank();
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
    return state.withActionSelection(nextSelection);
  }

  private static UiState.PostGenerationView withGithubVisibilitySelection(
      UiState.PostGenerationView state, int delta, int size) {
    int nextSelection =
        size > 0
            ? Math.floorMod(state.githubVisibilitySelection() + delta, size)
            : state.githubVisibilitySelection();
    return state.withGithubVisibilitySelection(nextSelection);
  }

  private static UiState.PostGenerationView withExactActionSelection(
      UiState.PostGenerationView state, int index) {
    return state.withActionSelection(index);
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
          state.withPostGeneration(postGeneration.showGithubVisibilityMenu()),
          List.of(),
          UiAction.handled(false));
    }
    if (action == PostGenerationExitAction.GENERATE_AGAIN) {
      return new ReduceResult(
          state
              .withSubmissionState(
                  "Ready for next generation", "", "", state.submitRequested(), false, false, false)
              .withPostGeneration(postGeneration.reset()),
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
        state.withPostGeneration(postGeneration.closeWithExitPlan(exitPlan)),
        List.of(new UiEffect.CancelPendingAsync()),
        UiAction.handled(true));
  }
}
