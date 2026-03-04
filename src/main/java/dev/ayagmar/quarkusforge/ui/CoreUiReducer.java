package dev.ayagmar.quarkusforge.ui;

import dev.tamboui.tui.event.KeyEvent;
import java.util.List;

/** Default reducer implementation for migrated UI state-machine slices. */
final class CoreUiReducer implements UiReducer {

  @Override
  public ReduceResult reduce(UiState state, UiIntent intent) {
    return switch (intent) {
      case UiIntent.PostGenerationIntent postGenerationIntent ->
          reducePostGeneration(state, postGenerationIntent.transition());
      case UiIntent.SubmitReadyIntent _ ->
          new ReduceResult(state, List.of(new UiEffect.StartGeneration()), UiAction.handled(false));
      case UiIntent.CancelGenerationIntent _ ->
          new ReduceResult(
              state,
              List.of(new UiEffect.RequestGenerationCancellation()),
              UiAction.handled(false));
      case UiIntent.GenerationProgressIntent progressIntent -> {
        GenerationProgressUpdate progressUpdate = progressIntent.progressUpdate();
        String rawProgressMessage =
            progressUpdate.message() == null ? "" : progressUpdate.message();
        String progressMessage = rawProgressMessage.isBlank() ? "working..." : rawProgressMessage;
        yield new ReduceResult(
            state.withFeedback("Generation in progress: " + progressMessage, "", ""),
            List.of(),
            UiAction.handled(false));
      }
      case UiIntent.GenerationSuccessIntent successIntent ->
          new ReduceResult(
              state.withFeedback("Generation succeeded: " + successIntent.generatedPath(), "", ""),
              List.of(
                  new UiEffect.ShowPostGenerationSuccess(
                      successIntent.generatedPath(), successIntent.nextCommand()),
                  new UiEffect.RequestAsyncRepaint()),
              UiAction.handled(false));
      case UiIntent.GenerationCancelledIntent _ ->
          new ReduceResult(
              state.withFeedback(
                  "Generation cancelled. Update inputs and press Enter to retry.", "", ""),
              List.of(new UiEffect.HidePostGenerationMenu(), new UiEffect.RequestAsyncRepaint()),
              UiAction.handled(false));
      case UiIntent.GenerationFailedIntent failedIntent ->
          new ReduceResult(
              state.withFeedback(
                  "Generation failed.",
                  failedIntent.userErrorMessage(),
                  failedIntent.verboseErrorDetails()),
              List.of(new UiEffect.HidePostGenerationMenu(), new UiEffect.RequestAsyncRepaint()),
              UiAction.handled(false));
      case UiIntent.GenerationCancellationRequestedIntent _ ->
          new ReduceResult(
              state.withStatusAndError("Cancellation requested. Waiting for cleanup...", ""),
              List.of(),
              UiAction.handled(false));
      case UiIntent.FocusNavigationIntent navigationIntent ->
          reduceFocusNavigation(state, navigationIntent.keyEvent(), navigationIntent.focusTarget());
      case UiIntent.MetadataInputIntent metadataIntent ->
          reduceMetadataInput(state, metadataIntent.keyEvent(), metadataIntent.focusTarget());
      case UiIntent.TextInputIntent textInputIntent ->
          reduceTextInput(state, textInputIntent.keyEvent(), textInputIntent.focusTarget());
      default -> new ReduceResult(state, List.of(), UiAction.ignored());
    };
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
