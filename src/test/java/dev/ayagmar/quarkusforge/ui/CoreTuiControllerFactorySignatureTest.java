package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThatCode;

import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import java.time.Duration;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

class CoreTuiControllerFactorySignatureTest {
  @Test
  void factoryOverloadsRemainStable() {
    assertThatCode(() -> CoreTuiController.class.getDeclaredMethod("from", ForgeUiState.class))
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                CoreTuiController.class.getDeclaredMethod(
                    "from", ForgeUiState.class, UiScheduler.class, Duration.class))
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                CoreTuiController.class.getDeclaredMethod(
                    "from",
                    ForgeUiState.class,
                    UiScheduler.class,
                    Duration.class,
                    CoreTuiController.ProjectGenerationRunner.class))
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                CoreTuiController.class.getDeclaredMethod(
                    "from",
                    ForgeUiState.class,
                    UiScheduler.class,
                    Duration.class,
                    CoreTuiController.ProjectGenerationRunner.class,
                    ExtensionFavoritesStore.class,
                    Executor.class))
        .doesNotThrowAnyException();
  }
}
