package dev.ayagmar.quarkusforge.ui;

import java.util.ArrayList;
import java.util.List;

/** Executes reducer-emitted effects by invoking controller-owned imperative operations. */
final class UiEffectsRunner {

  List<UiIntent> run(List<UiEffect> effects, UiEffectsPort effectsPort) {
    List<UiIntent> followUpIntents = new ArrayList<>();
    for (UiEffect effect : effects) {
      followUpIntents.addAll(run(effect, effectsPort));
    }
    return List.copyOf(followUpIntents);
  }

  private static List<UiIntent> run(UiEffect effect, UiEffectsPort effectsPort) {
    return switch (effect) {
      case UiEffect.StartCatalogLoad catalogLoadEffect ->
          runAndReturnNone(() -> effectsPort.startCatalogLoad(catalogLoadEffect.loader()));
      case UiEffect.RequestCatalogReload _ -> runAndReturnNone(effectsPort::requestCatalogReload);
      case UiEffect.PrepareForGeneration _ -> runAndReturnNone(effectsPort::prepareForGeneration);
      case UiEffect.CancelPendingAsync _ -> runAndReturnNone(effectsPort::cancelPendingAsync);
      case UiEffect.ExportRecipeAndLock _ ->
          List.of(new UiIntent.StatusMessageIntent(effectsPort.exportRecipeAndLock()));
      case UiEffect.ExecuteExtensionCommand extensionEffect ->
          effectsPort.executeExtensionCommand(extensionEffect.command());
      case UiEffect.ApplyExtensionNavigationKey navigationEffect ->
          effectsPort.applyExtensionNavigationKey(navigationEffect.keyEvent());
      case UiEffect.ApplyCatalogLoadSuccess successEffect ->
          effectsPort.applyCatalogLoadSuccess(successEffect.success());
      case UiEffect.StartGeneration _ -> runAndReturnNone(effectsPort::startGeneration);
      case UiEffect.TransitionGenerationState transitionEffect ->
          runAndReturnNone(
              () -> effectsPort.transitionGenerationState(transitionEffect.targetState()));
      case UiEffect.RequestGenerationCancellation _ ->
          runAndReturnNone(effectsPort::requestGenerationCancellation);
      case UiEffect.RequestAsyncRepaint _ -> runAndReturnNone(effectsPort::requestAsyncRepaint);
      case UiEffect.MoveTextInputCursorToEnd moveCursorEffect ->
          runAndReturnNone(
              () -> effectsPort.moveTextInputCursorToEnd(moveCursorEffect.focusTarget()));
      case UiEffect.ApplyMetadataSelectorKey selectorEffect ->
          effectsPort.applyMetadataSelectorKey(
              selectorEffect.focusTarget(), selectorEffect.keyEvent());
      case UiEffect.ApplyTextInputKey textInputEffect ->
          effectsPort.applyTextInputKey(textInputEffect.focusTarget(), textInputEffect.keyEvent());
    };
  }

  private static List<UiIntent> runAndReturnNone(Runnable runnable) {
    runnable.run();
    return List.of();
  }
}
