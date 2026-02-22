package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.api.ExtensionDto;
import dev.ayagmar.quarkusforge.api.GenerationRequest;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class CoreTuiStateMachineTest {
  @Test
  void validTransitionsMatchContract() {
    assertThat(
            CoreTuiController.isValidTransition(
                CoreTuiController.GenerationState.IDLE,
                CoreTuiController.GenerationState.VALIDATING))
        .isTrue();
    assertThat(
            CoreTuiController.isValidTransition(
                CoreTuiController.GenerationState.VALIDATING,
                CoreTuiController.GenerationState.LOADING))
        .isTrue();
    assertThat(
            CoreTuiController.isValidTransition(
                CoreTuiController.GenerationState.VALIDATING,
                CoreTuiController.GenerationState.ERROR))
        .isTrue();
    assertThat(
            CoreTuiController.isValidTransition(
                CoreTuiController.GenerationState.LOADING,
                CoreTuiController.GenerationState.SUCCESS))
        .isTrue();
    assertThat(
            CoreTuiController.isValidTransition(
                CoreTuiController.GenerationState.LOADING,
                CoreTuiController.GenerationState.CANCELLED))
        .isTrue();
    assertThat(
            CoreTuiController.isValidTransition(
                CoreTuiController.GenerationState.SUCCESS, CoreTuiController.GenerationState.IDLE))
        .isTrue();
  }

  @Test
  void invalidTransitionsAreRejected() {
    assertThat(
            CoreTuiController.isValidTransition(
                CoreTuiController.GenerationState.IDLE, CoreTuiController.GenerationState.LOADING))
        .isFalse();
    assertThat(
            CoreTuiController.isValidTransition(
                CoreTuiController.GenerationState.LOADING,
                CoreTuiController.GenerationState.VALIDATING))
        .isFalse();
    assertThat(
            CoreTuiController.isValidTransition(
                CoreTuiController.GenerationState.ERROR, CoreTuiController.GenerationState.LOADING))
        .isFalse();
    assertThat(
            CoreTuiController.isValidTransition(
                CoreTuiController.GenerationState.CANCELLED,
                CoreTuiController.GenerationState.SUCCESS))
        .isFalse();
    assertThat(
            CoreTuiController.isValidTransition(
                CoreTuiController.GenerationState.IDLE, CoreTuiController.GenerationState.IDLE))
        .isFalse();
  }

  @Test
  void rapidEnterSpamStartsOneGenerationWhileLoading() {
    ControlledGenerationRunner generationRunner = new ControlledGenerationRunner();
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(),
            UiScheduler.immediate(),
            Duration.ZERO,
            generationRunner);

    for (int i = 0; i < 20; i++) {
      controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    }

    assertThat(generationRunner.callCount()).isEqualTo(1);
    assertThat(controller.generationState()).isEqualTo(CoreTuiController.GenerationState.LOADING);
    assertThat(controller.statusMessage()).contains("already in progress");
  }

  @Test
  void escapeDuringLoadingCancelsGenerationWithoutQuitting() {
    ControlledGenerationRunner generationRunner = new ControlledGenerationRunner();
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(),
            UiScheduler.immediate(),
            Duration.ZERO,
            generationRunner);

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    CoreTuiController.UiAction cancelAction = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));

    assertThat(cancelAction.shouldQuit()).isFalse();
    assertThat(cancelAction.handled()).isTrue();
    assertThat(controller.generationState())
        .isIn(
            CoreTuiController.GenerationState.LOADING, CoreTuiController.GenerationState.CANCELLED);
    assertThat(controller.statusMessage())
        .containsAnyOf("Cancellation requested", "Generation cancelled");
    if (controller.generationState() == CoreTuiController.GenerationState.LOADING) {
      generationRunner.fail(new CancellationException("cancelled"));
    }

    assertThat(controller.generationState()).isEqualTo(CoreTuiController.GenerationState.CANCELLED);
    assertThat(controller.statusMessage()).contains("Generation cancelled");
  }

  @Test
  void staleCatalogCompletionIsIgnoredWhenNewerLoadAlreadyApplied() {
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), UiScheduler.immediate(), Duration.ZERO);
    CompletableFuture<List<ExtensionDto>> firstLoad = new CompletableFuture<>();
    CompletableFuture<List<ExtensionDto>> secondLoad = new CompletableFuture<>();

    controller.loadExtensionCatalogAsync(() -> firstLoad);
    controller.loadExtensionCatalogAsync(() -> secondLoad);

    secondLoad.complete(
        List.of(
            new ExtensionDto(
                "io.quarkus:quarkus-jdbc-postgresql", "JDBC PostgreSQL", "jdbc-postgresql")));
    assertThat(controller.firstFilteredExtensionId()).contains("jdbc-postgresql");

    firstLoad.complete(List.of(new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest")));
    assertThat(controller.firstFilteredExtensionId()).contains("jdbc-postgresql");
  }

  private static final class ControlledGenerationRunner
      implements CoreTuiController.ProjectGenerationRunner {
    private int callCount;
    private CompletableFuture<Path> future;

    ControlledGenerationRunner() {
      callCount = 0;
      future = new CompletableFuture<>();
    }

    @Override
    public CompletableFuture<Path> generate(
        GenerationRequest generationRequest,
        Path outputDirectory,
        BooleanSupplier cancelled,
        Consumer<String> progressListener) {
      callCount++;
      progressListener.accept("downloading project archive...");
      return future;
    }

    int callCount() {
      return callCount;
    }

    void fail(Throwable throwable) {
      future.completeExceptionally(throwable);
    }
  }
}
