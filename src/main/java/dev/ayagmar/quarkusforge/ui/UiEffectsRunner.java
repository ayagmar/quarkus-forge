package dev.ayagmar.quarkusforge.ui;

import java.util.List;

/** Executes reducer-emitted effects by invoking controller-owned imperative operations. */
final class UiEffectsRunner {

  void run(List<UiEffect> effects, CoreTuiController controller) {
    for (UiEffect effect : effects) {
      run(effect, controller);
    }
  }

  private static void run(UiEffect effect, CoreTuiController controller) {
    switch (effect) {
      case UiEffect.PrepareForGeneration _ -> controller.prepareForGenerationForReducer();
      case UiEffect.CancelPendingAsync _ -> controller.cancelPendingAsyncForReducer();
      case UiEffect.ExportRecipeAndLock _ -> controller.exportRecipeAndLockForReducer();
      case UiEffect.StartGeneration _ -> controller.startGenerationForReducer();
      case UiEffect.RequestGenerationCancellation _ ->
          controller.requestGenerationCancellationForReducer();
      case UiEffect.ShowPostGenerationSuccess showEffect ->
          controller.showPostGenerationSuccessForReducer(
              showEffect.generatedPath(), showEffect.nextCommand());
      case UiEffect.HidePostGenerationMenu _ -> controller.hidePostGenerationForReducer();
      case UiEffect.RequestAsyncRepaint _ -> controller.requestAsyncRepaintForReducer();
      case UiEffect.MoveFocus moveFocusEffect ->
          controller.moveFocusForReducer(moveFocusEffect.offset());
      case UiEffect.ApplyMetadataSelectorKey selectorEffect ->
          controller.applyMetadataSelectorKeyForReducer(
              selectorEffect.focusTarget(), selectorEffect.keyEvent());
      case UiEffect.ApplyTextInputKey textInputEffect ->
          controller.applyTextInputKeyForReducer(
              textInputEffect.focusTarget(), textInputEffect.keyEvent());
    }
  }
}
