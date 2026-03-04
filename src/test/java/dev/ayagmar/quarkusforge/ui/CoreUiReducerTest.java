package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CoreUiReducerTest {

  private final UiReducer reducer = new CoreUiReducer();

  @Test
  void postGenerationQuitProducesQuitActionAndCancelEffect() {
    ReduceResult result =
        reducer.reduce(
            UiControllerTestHarness.controller().uiState(),
            new UiIntent.PostGenerationIntent(UiIntent.PostGenerationTransition.QUIT));

    assertThat(result.action()).isEqualTo(UiAction.handled(true));
    assertThat(result.effects()).containsExactly(new UiEffect.CancelPendingAsync());
  }

  @Test
  void postGenerationExportRecipeProducesExportEffect() {
    ReduceResult result =
        reducer.reduce(
            UiControllerTestHarness.controller().uiState(),
            new UiIntent.PostGenerationIntent(UiIntent.PostGenerationTransition.EXPORT_RECIPE));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects()).containsExactly(new UiEffect.ExportRecipeAndLock());
  }

  @Test
  void postGenerationGenerateAgainResetsStatusAndErrorViaReducerState() {
    UiState baseState =
        UiControllerTestHarness.controller()
            .uiState()
            .withStatusAndError("Some previous status", "Some previous error");

    ReduceResult result =
        reducer.reduce(
            baseState,
            new UiIntent.PostGenerationIntent(UiIntent.PostGenerationTransition.GENERATE_AGAIN));

    assertThat(result.action()).isEqualTo(UiAction.handled(false));
    assertThat(result.effects()).containsExactly(new UiEffect.ResetGenerationAfterOutcome());
    assertThat(result.nextState().statusMessage()).isEqualTo("Ready for next generation");
    assertThat(result.nextState().errorMessage()).isEmpty();
  }
}
