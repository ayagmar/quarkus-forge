package dev.ayagmar.quarkusforge.ui;

import java.util.List;

final class UiEffectsRunner {

  void run(List<UiEffect> effects, CoreTuiController controller) {
    for (UiEffect effect : effects) {
      run(effect, controller);
    }
  }

  private static void run(UiEffect effect, CoreTuiController controller) {
    switch (effect) {
      case UiEffect.CancelPendingAsync _ -> controller.cancelPendingAsyncForReducer();
      case UiEffect.ExportRecipeAndLock _ -> controller.exportRecipeAndLockForReducer();
      case UiEffect.ResetGenerationAfterOutcome _ -> controller.resetGenerationForReducer();
    }
  }
}
