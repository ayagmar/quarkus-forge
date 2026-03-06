package dev.ayagmar.quarkusforge.ui;

/** Normalized user/system events consumed by the reducer. */
sealed interface UiIntent {
  record PostGenerationIntent(PostGenerationCommand command) implements UiIntent {}

  record SubmitRequestedIntent(SubmitEvaluation evaluation) implements UiIntent {}

  record SubmitEditRecoveryIntent(SubmitEditRecovery recovery) implements UiIntent {}

  record CancelGenerationIntent() implements UiIntent {}

  record GenerationProgressIntent(String statusMessage) implements UiIntent {}

  record GenerationSuccessIntent(java.nio.file.Path generatedPath, String nextCommand)
      implements UiIntent {}

  record GenerationCancelledIntent() implements UiIntent {}

  record GenerationFailedIntent(String userErrorMessage, String verboseErrorDetails)
      implements UiIntent {}

  record GenerationCancellationRequestedIntent() implements UiIntent {}

  record FocusNavigationIntent(dev.tamboui.tui.event.KeyEvent keyEvent, FocusTarget focusTarget)
      implements UiIntent {}

  record MetadataInputIntent(dev.tamboui.tui.event.KeyEvent keyEvent, FocusTarget focusTarget)
      implements UiIntent {}

  record TextInputIntent(dev.tamboui.tui.event.KeyEvent keyEvent, FocusTarget focusTarget)
      implements UiIntent {}

  record ToggleErrorDetailsIntent(boolean activeErrorPresent) implements UiIntent {}

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

  record SubmitEvaluation(
      boolean generationConfigured,
      int selectedExtensionCount,
      FocusTarget firstInvalidTarget,
      int validationIssueCount,
      String firstValidationError,
      String targetConflictErrorMessage) {
    boolean hasValidationError() {
      return !firstValidationError.isBlank();
    }

    boolean hasTargetConflict() {
      return !targetConflictErrorMessage.isBlank();
    }
  }

  record SubmitEditRecovery(
      boolean submitBlockedByValidation,
      boolean validationValid,
      String firstValidationError,
      boolean submitBlockedByTargetConflict,
      String targetConflictErrorMessage) {
    boolean targetConflictResolved() {
      return submitBlockedByTargetConflict && targetConflictErrorMessage.isBlank();
    }

    boolean validationRecovered() {
      return submitBlockedByValidation && validationValid;
    }
  }
}
