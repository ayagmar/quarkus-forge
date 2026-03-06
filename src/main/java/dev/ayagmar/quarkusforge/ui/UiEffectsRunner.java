package dev.ayagmar.quarkusforge.ui;

import java.util.List;

/** Executes reducer-emitted effects by invoking controller-owned imperative operations. */
final class UiEffectsRunner {

  void run(List<UiEffect> effects, UiEffectsPort effectsPort) {
    for (UiEffect effect : effects) {
      run(effect, effectsPort);
    }
  }

  private static void run(UiEffect effect, UiEffectsPort effectsPort) {
    switch (effect) {
      case UiEffect.StartCatalogLoad catalogLoadEffect ->
          effectsPort.startCatalogLoad(catalogLoadEffect.loader());
      case UiEffect.RequestCatalogReload _ -> effectsPort.requestCatalogReload();
      case UiEffect.PrepareForGeneration _ -> effectsPort.prepareForGeneration();
      case UiEffect.CancelPendingAsync _ -> effectsPort.cancelPendingAsync();
      case UiEffect.ExportRecipeAndLock _ -> effectsPort.exportRecipeAndLock();
      case UiEffect.ApplyCatalogLoadSuccess successEffect ->
          effectsPort.applyCatalogLoadSuccess(successEffect.success());
      case UiEffect.StartGeneration _ -> effectsPort.startGeneration();
      case UiEffect.TransitionGenerationState transitionEffect ->
          effectsPort.transitionGenerationState(transitionEffect.targetState());
      case UiEffect.RequestGenerationCancellation _ -> effectsPort.requestGenerationCancellation();
      case UiEffect.RequestAsyncRepaint _ -> effectsPort.requestAsyncRepaint();
      case UiEffect.ApplyMetadataSelectorKey selectorEffect ->
          effectsPort.applyMetadataSelectorKey(
              selectorEffect.focusTarget(), selectorEffect.keyEvent());
      case UiEffect.ApplyTextInputKey textInputEffect ->
          effectsPort.applyTextInputKey(textInputEffect.focusTarget(), textInputEffect.keyEvent());
    }
  }
}
