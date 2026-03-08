package dev.ayagmar.quarkusforge.ui;

import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ValidationReport;

/** Normalized user/system events consumed by the reducer. */
sealed interface UiIntent {
  record PostGenerationIntent(PostGenerationCommand command) implements UiIntent {}

  record CatalogLoadRequestedIntent(ExtensionCatalogLoader loader) implements UiIntent {}

  record CatalogReloadRequestedIntent() implements UiIntent {}

  record CatalogLoadStartedIntent(CatalogLoadState nextState, boolean startupOverlayVisible)
      implements UiIntent {}

  record CatalogLoadCancelledIntent(CatalogLoadState nextState, boolean startupOverlayVisible)
      implements UiIntent {}

  record CatalogReloadUnavailableIntent() implements UiIntent {}

  record CatalogLoadSucceededIntent(CatalogLoadSuccess success, boolean startupOverlayVisible)
      implements UiIntent {}

  record CatalogLoadFailedIntent(CatalogLoadFailure failure, boolean startupOverlayVisible)
      implements UiIntent {}

  record StartupOverlayVisibilityIntent(boolean visible) implements UiIntent {}

  record SubmitRequestedIntent(SubmitRequestContext context) implements UiIntent {}

  record SubmitEditRecoveryIntent(SubmitRecoveryContext context) implements UiIntent {}

  record CancelGenerationIntent() implements UiIntent {}

  record GenerationProgressIntent() implements UiIntent {}

  record GenerationSuccessIntent(java.nio.file.Path generatedPath, String nextCommand)
      implements UiIntent {}

  record GenerationCancelledIntent() implements UiIntent {}

  record GenerationFailedIntent(String userErrorMessage, String verboseErrorDetails)
      implements UiIntent {}

  record GenerationCancellationRequestedIntent() implements UiIntent {}

  record GenerationOverlayVisibilityIntent(boolean visible) implements UiIntent {}

  record CommandPaletteIntent(CommandPaletteCommand command) implements UiIntent {}

  record HelpOverlayIntent(HelpOverlayCommand command) implements UiIntent {}

  record SharedActionIntent(CommandPaletteAction action) implements UiIntent {}

  record ExtensionCancelIntent() implements UiIntent {}

  record ExtensionCommandIntent(ExtensionCommand command) implements UiIntent {}

  record ExtensionStatusIntent(String statusMessage) implements UiIntent {
    public ExtensionStatusIntent {
      statusMessage = statusMessage == null ? "" : statusMessage;
    }
  }

  record StatusMessageIntent(String statusMessage) implements UiIntent {
    public StatusMessageIntent {
      statusMessage = statusMessage == null ? "" : statusMessage;
    }
  }

  record FocusStatusIntent(FocusTarget focusTarget, String statusMessage) implements UiIntent {
    public FocusStatusIntent {
      statusMessage = statusMessage == null ? "" : statusMessage;
    }
  }

  record FormStateUpdatedIntent(ProjectRequest request, ValidationReport validation)
      implements UiIntent {}

  record ExtensionStateUpdatedIntent(UiState.ExtensionView extensions) implements UiIntent {}

  record ExtensionInteractionIntent(dev.tamboui.tui.event.KeyEvent keyEvent) implements UiIntent {}

  record FocusNavigationIntent(dev.tamboui.tui.event.KeyEvent keyEvent, FocusTarget focusTarget)
      implements UiIntent {}

  record MetadataInputIntent(
      dev.tamboui.tui.event.KeyEvent keyEvent, FocusTarget focusTarget, boolean optionsAvailable)
      implements UiIntent {}

  record TextInputIntent(dev.tamboui.tui.event.KeyEvent keyEvent, FocusTarget focusTarget)
      implements UiIntent {}

  record ToggleErrorDetailsIntent() implements UiIntent {}

  enum ExtensionCommand {
    CLEAR_SEARCH,
    DISABLE_FAVORITES_FILTER,
    DISABLE_SELECTED_FILTER,
    CLEAR_PRESET_FILTER,
    CLEAR_CATEGORY_FILTER,
    TOGGLE_FAVORITE_AT_SELECTION,
    CLEAR_SELECTED_EXTENSIONS,
    TOGGLE_CATEGORY_AT_SELECTION,
    OPEN_ALL_CATEGORIES,
    JUMP_TO_NEXT_CATEGORY,
    JUMP_TO_PREVIOUS_CATEGORY,
    HIERARCHY_LEFT,
    HIERARCHY_RIGHT,
    TOGGLE_SELECTION_AT_CURSOR,
    TOGGLE_FAVORITES_FILTER,
    TOGGLE_SELECTED_FILTER,
    CYCLE_PRESET_FILTER,
    JUMP_TO_FAVORITE,
    CYCLE_CATEGORY_FILTER
  }

  sealed interface CommandPaletteCommand {
    record ToggleVisibility() implements CommandPaletteCommand {}

    record Dismiss() implements CommandPaletteCommand {}

    record MoveSelection(int delta) implements CommandPaletteCommand {}

    record JumpHome() implements CommandPaletteCommand {}

    record JumpEnd() implements CommandPaletteCommand {}

    record SelectIndex(int index) implements CommandPaletteCommand {}

    record ConfirmSelection() implements CommandPaletteCommand {}
  }

  sealed interface HelpOverlayCommand {
    record ToggleVisibility() implements HelpOverlayCommand {}

    record Dismiss() implements HelpOverlayCommand {}
  }

  sealed interface PostGenerationCommand {
    record Noop() implements PostGenerationCommand {}

    record MoveActionSelection(int delta) implements PostGenerationCommand {}

    record MoveGithubVisibilitySelection(int delta) implements PostGenerationCommand {}

    record SelectActionIndex(int index) implements PostGenerationCommand {}

    record SelectGithubVisibilityIndex(int index) implements PostGenerationCommand {}

    record ConfirmSelection() implements PostGenerationCommand {}

    record CancelGithubVisibility() implements PostGenerationCommand {}

    record Quit() implements PostGenerationCommand {}
  }

  record SubmitRequestContext(
      boolean generationConfigured, int selectedExtensionCount, String targetConflictErrorMessage) {
    public SubmitRequestContext {
      targetConflictErrorMessage =
          targetConflictErrorMessage == null ? "" : targetConflictErrorMessage;
    }

    static SubmitRequestContext from(
        boolean generationConfigured,
        int selectedExtensionCount,
        String targetConflictErrorMessage) {
      return new SubmitRequestContext(
          generationConfigured, selectedExtensionCount, targetConflictErrorMessage);
    }

    boolean hasTargetConflict() {
      return !targetConflictErrorMessage.isBlank();
    }
  }

  record SubmitRecoveryContext(String targetConflictErrorMessage) {
    public SubmitRecoveryContext {
      targetConflictErrorMessage =
          targetConflictErrorMessage == null ? "" : targetConflictErrorMessage;
    }
  }

  static String firstValidationError(ValidationReport report) {
    return report.errors().isEmpty()
        ? ""
        : report.errors().getFirst().field() + ": " + report.errors().getFirst().message();
  }
}
