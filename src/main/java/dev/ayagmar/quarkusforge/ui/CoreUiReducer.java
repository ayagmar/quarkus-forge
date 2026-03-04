package dev.ayagmar.quarkusforge.ui;

import dev.tamboui.tui.event.KeyEvent;
import java.util.List;

/** Default reducer implementation for migrated UI state-machine slices. */
final class CoreUiReducer implements UiReducer {

  @Override
  public ReduceResult reduce(UiState state, UiIntent intent) {
    if (intent instanceof UiIntent.PostGenerationIntent postGenerationIntent) {
      return reducePostGeneration(state, postGenerationIntent.transition());
    }
    if (intent instanceof UiIntent.SubmitReadyIntent) {
      return new ReduceResult(
          state, List.of(new UiEffect.StartGeneration()), UiAction.handled(false));
    }
    if (intent instanceof UiIntent.CancelGenerationIntent) {
      return new ReduceResult(
          state, List.of(new UiEffect.RequestGenerationCancellation()), UiAction.handled(false));
    }
    if (intent instanceof UiIntent.GenerationProgressIntent progressIntent) {
      GenerationProgressUpdate progressUpdate = progressIntent.progressUpdate();
      String progressMessage =
          progressUpdate.message().isBlank() ? "working..." : progressUpdate.message();
      return new ReduceResult(
          state.withFeedback("Generation in progress: " + progressMessage, "", ""),
          List.of(),
          UiAction.handled(false));
    }
    if (intent instanceof UiIntent.GenerationSuccessIntent successIntent) {
      return new ReduceResult(
          state.withFeedback("Generation succeeded: " + successIntent.generatedPath(), "", ""),
          List.of(
              new UiEffect.ShowPostGenerationSuccess(
                  successIntent.generatedPath(), successIntent.nextCommand()),
              new UiEffect.RequestAsyncRepaint()),
          UiAction.handled(false));
    }
    if (intent instanceof UiIntent.GenerationCancelledIntent) {
      return new ReduceResult(
          state.withFeedback(
              "Generation cancelled. Update inputs and press Enter to retry.", "", ""),
          List.of(new UiEffect.HidePostGenerationMenu(), new UiEffect.RequestAsyncRepaint()),
          UiAction.handled(false));
    }
    if (intent instanceof UiIntent.GenerationFailedIntent failedIntent) {
      return new ReduceResult(
          state.withFeedback(
              "Generation failed.",
              failedIntent.userErrorMessage(),
              failedIntent.verboseErrorDetails()),
          List.of(new UiEffect.HidePostGenerationMenu(), new UiEffect.RequestAsyncRepaint()),
          UiAction.handled(false));
    }
    if (intent instanceof UiIntent.GenerationCancellationRequestedIntent) {
      return new ReduceResult(
          state.withStatusAndError("Cancellation requested. Waiting for cleanup...", ""),
          List.of(),
          UiAction.handled(false));
    }
    if (intent instanceof UiIntent.FocusNavigationIntent navigationIntent) {
      return reduceFocusNavigation(
          state, navigationIntent.keyEvent(), navigationIntent.focusTarget());
    }
    if (intent instanceof UiIntent.MetadataInputIntent metadataIntent) {
      return reduceMetadataInput(state, metadataIntent.keyEvent(), metadataIntent.focusTarget());
    }
    if (intent instanceof UiIntent.TextInputIntent textInputIntent) {
      return reduceTextInput(state, textInputIntent.keyEvent(), textInputIntent.focusTarget());
    }
    return new ReduceResult(state, List.of(), UiAction.ignored());
  }

  private static ReduceResult reduceFocusNavigation(
      UiState state, KeyEvent keyEvent, FocusTarget focusTarget) {
    if (keyEvent.isFocusPrevious()) {
      return new ReduceResult(state, List.of(new UiEffect.MoveFocus(-1)), UiAction.handled(false));
    }
    if (keyEvent.isFocusNext()) {
      return new ReduceResult(state, List.of(new UiEffect.MoveFocus(1)), UiAction.handled(false));
    }
    if (focusTarget == FocusTarget.SUBMIT) {
      if (UiKeyMatchers.isVimDownKey(keyEvent)) {
        return new ReduceResult(state, List.of(new UiEffect.MoveFocus(1)), UiAction.handled(false));
      }
      if (UiKeyMatchers.isVimUpKey(keyEvent)) {
        return new ReduceResult(
            state, List.of(new UiEffect.MoveFocus(-1)), UiAction.handled(false));
      }
    }
    return new ReduceResult(state, List.of(), UiAction.ignored());
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
      UiState state, UiIntent.PostGenerationTransition transition) {
    return switch (transition) {
      case HANDLED -> new ReduceResult(state, List.of(), UiAction.handled(false));
      case QUIT ->
          new ReduceResult(
              state, List.of(new UiEffect.CancelPendingAsync()), UiAction.handled(true));
      case EXPORT_RECIPE ->
          new ReduceResult(
              state, List.of(new UiEffect.ExportRecipeAndLock()), UiAction.handled(false));
      case GENERATE_AGAIN ->
          new ReduceResult(
              state.withStatusAndError("Ready for next generation", ""),
              List.of(new UiEffect.ResetGenerationAfterOutcome()),
              UiAction.handled(false));
    };
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
}
