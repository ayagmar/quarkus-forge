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
            GenerationStateMachine.isValidTransition(
                GenerationState.IDLE, GenerationState.VALIDATING))
        .isTrue();
    assertThat(
            GenerationStateMachine.isValidTransition(
                GenerationState.VALIDATING, GenerationState.LOADING))
        .isTrue();
    assertThat(
            GenerationStateMachine.isValidTransition(
                GenerationState.VALIDATING, GenerationState.ERROR))
        .isTrue();
    assertThat(
            GenerationStateMachine.isValidTransition(
                GenerationState.LOADING, GenerationState.SUCCESS))
        .isTrue();
    assertThat(
            GenerationStateMachine.isValidTransition(
                GenerationState.LOADING, GenerationState.CANCELLED))
        .isTrue();
    assertThat(
            GenerationStateMachine.isValidTransition(GenerationState.SUCCESS, GenerationState.IDLE))
        .isTrue();
  }

  @Test
  void invalidTransitionsAreRejected() {
    assertThat(
            GenerationStateMachine.isValidTransition(GenerationState.IDLE, GenerationState.LOADING))
        .isFalse();
    assertThat(
            GenerationStateMachine.isValidTransition(
                GenerationState.LOADING, GenerationState.VALIDATING))
        .isFalse();
    assertThat(
            GenerationStateMachine.isValidTransition(
                GenerationState.ERROR, GenerationState.LOADING))
        .isFalse();
    assertThat(
            GenerationStateMachine.isValidTransition(
                GenerationState.CANCELLED, GenerationState.SUCCESS))
        .isFalse();
    assertThat(GenerationStateMachine.isValidTransition(GenerationState.IDLE, GenerationState.IDLE))
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
    assertThat(controller.generationState()).isEqualTo(GenerationState.LOADING);
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
    UiAction cancelAction = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));

    assertThat(cancelAction.shouldQuit()).isFalse();
    assertThat(cancelAction.handled()).isTrue();
    assertThat(controller.generationState())
        .isIn(GenerationState.LOADING, GenerationState.CANCELLED);
    assertThat(controller.statusMessage())
        .containsAnyOf("Cancellation requested", "Generation cancelled");
    if (controller.generationState() == GenerationState.LOADING) {
      generationRunner.fail(new CancellationException("cancelled"));
    }

    assertThat(controller.generationState()).isEqualTo(GenerationState.CANCELLED);
    assertThat(controller.statusMessage()).contains("Generation cancelled");
  }

  @Test
  void staleCatalogCompletionIsIgnoredWhenNewerLoadAlreadyApplied() {
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), UiScheduler.immediate(), Duration.ZERO);
    CompletableFuture<ExtensionCatalogLoadResult> firstLoad = new CompletableFuture<>();
    CompletableFuture<ExtensionCatalogLoadResult> secondLoad = new CompletableFuture<>();

    controller.loadExtensionCatalogAsync(() -> firstLoad);
    controller.loadExtensionCatalogAsync(() -> secondLoad);

    secondLoad.complete(
        ExtensionCatalogLoadResult.live(
            List.of(
                new ExtensionDto(
                    "io.quarkus:quarkus-jdbc-postgresql", "JDBC PostgreSQL", "jdbc-postgresql"))));
    assertThat(controller.firstFilteredExtensionId()).contains("jdbc-postgresql");

    firstLoad.complete(
        ExtensionCatalogLoadResult.live(
            List.of(new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest"))));
    assertThat(controller.firstFilteredExtensionId()).contains("jdbc-postgresql");
  }

  private static final class ControlledGenerationRunner implements ProjectGenerationRunner {
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
        Consumer<GenerationProgressUpdate> progressListener) {
      callCount++;
      progressListener.accept(
          GenerationProgressUpdate.requestingArchive(
              "requesting project archive from Quarkus API..."));
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
