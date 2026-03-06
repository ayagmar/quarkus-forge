package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.api.CatalogSource;
import dev.ayagmar.quarkusforge.api.ExtensionDto;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class CatalogLoadCoordinatorTest {

  @Test
  void syncLoaderFailureProducesFallbackFailureEvent() {
    CatalogLoadCoordinator coordinator = new CatalogLoadCoordinator();
    TestCallbacks callbacks = new TestCallbacks();

    coordinator.startLoad(
        () -> {
          throw new IllegalStateException("boom");
        },
        callbacks);

    assertThat(callbacks.failures).hasSize(1);
    assertThat(callbacks.failures.getFirst().errorMessage()).contains("boom");
    assertThat(callbacks.currentState).isInstanceOf(CatalogLoadState.Failed.class);
  }

  @Test
  void failedReloadKeepsPreviousLiveCatalog() {
    CatalogLoadCoordinator coordinator = new CatalogLoadCoordinator();
    TestCallbacks callbacks =
        new TestCallbacks(CatalogLoadState.loaded(CatalogSource.LIVE.label(), false));

    coordinator.startLoad(
        () -> CompletableFuture.failedFuture(new IllegalStateException("boom")), callbacks);

    assertThat(callbacks.failures).hasSize(1);
    assertThat(callbacks.failures.getFirst().statusMessage())
        .isEqualTo("Catalog reload failed; keeping current catalog");
    assertThat(callbacks.currentState).isEqualTo(CatalogLoadState.loaded("live", false));
  }

  @Test
  void staleCompletionFromEarlierLoadIsIgnored() {
    CatalogLoadCoordinator coordinator = new CatalogLoadCoordinator();
    TestCallbacks callbacks = new TestCallbacks();
    CompletableFuture<ExtensionCatalogLoadResult> firstLoad = new CompletableFuture<>();
    CompletableFuture<ExtensionCatalogLoadResult> secondLoad =
        CompletableFuture.completedFuture(successResult("second"));

    coordinator.startLoad(() -> firstLoad, callbacks);
    coordinator.startLoad(() -> secondLoad, callbacks);
    firstLoad.complete(successResult("first"));

    assertThat(callbacks.successes).hasSize(1);
    assertThat(callbacks.successes.getFirst().items())
        .extracting(ExtensionCatalogItem::id)
        .containsExactly("second");
  }

  @Test
  void cancelRestoresPreviousCatalogState() {
    CatalogLoadCoordinator coordinator = new CatalogLoadCoordinator();
    TestCallbacks callbacks =
        new TestCallbacks(CatalogLoadState.loaded(CatalogSource.LIVE.label(), false));
    CompletableFuture<ExtensionCatalogLoadResult> pendingLoad = new CompletableFuture<>();

    coordinator.startLoad(() -> pendingLoad, callbacks);
    coordinator.cancel(callbacks);

    assertThat(callbacks.currentState).isEqualTo(CatalogLoadState.loaded("live", false));
  }

  @Test
  void startingNewLoadCancelsPreviousFuture() {
    CatalogLoadCoordinator coordinator = new CatalogLoadCoordinator();
    TestCallbacks callbacks = new TestCallbacks();
    CompletableFuture<ExtensionCatalogLoadResult> firstLoad = new CompletableFuture<>();
    CompletableFuture<ExtensionCatalogLoadResult> secondLoad =
        CompletableFuture.completedFuture(successResult("second"));

    coordinator.startLoad(() -> firstLoad, callbacks);
    coordinator.startLoad(() -> secondLoad, callbacks);

    assertThat(firstLoad).isCancelled();
    assertThat(callbacks.failures).isEmpty();
  }

  @Test
  void cancelledFutureDoesNotEmitFailureCallback() {
    CatalogLoadCoordinator coordinator = new CatalogLoadCoordinator();
    TestCallbacks callbacks = new TestCallbacks();
    CompletableFuture<ExtensionCatalogLoadResult> cancelled =
        CompletableFuture.failedFuture(new CancellationException("cancelled"));

    coordinator.startLoad(() -> cancelled, callbacks);

    assertThat(callbacks.failures).isEmpty();
    assertThat(callbacks.successes).isEmpty();
  }

  @Test
  void requestReloadWithoutPriorLoaderMarksReloadUnavailable() {
    CatalogLoadCoordinator coordinator = new CatalogLoadCoordinator();
    TestCallbacks callbacks = new TestCallbacks();

    coordinator.requestReload(callbacks);

    assertThat(callbacks.reloadUnavailable).isTrue();
  }

  @Test
  void nullLoadFutureProducesFallbackFailureEvent() {
    CatalogLoadCoordinator coordinator = new CatalogLoadCoordinator();
    TestCallbacks callbacks = new TestCallbacks();

    coordinator.startLoad(() -> null, callbacks);

    assertThat(callbacks.failures).hasSize(1);
    assertThat(callbacks.failures.getFirst().errorMessage())
        .contains("loader returned null future");
    assertThat(callbacks.currentState).isInstanceOf(CatalogLoadState.Failed.class);
  }

  @Test
  void nullLoadResultProducesFailureEvent() {
    CatalogLoadCoordinator coordinator = new CatalogLoadCoordinator();
    TestCallbacks callbacks = new TestCallbacks();

    coordinator.startLoad(() -> CompletableFuture.completedFuture(null), callbacks);

    assertThat(callbacks.failures).hasSize(1);
    assertThat(callbacks.failures.getFirst().errorMessage()).contains("empty load result");
  }

  @Test
  void firstLoadFailureUsesFallbackCatalogState() {
    CatalogLoadCoordinator coordinator = new CatalogLoadCoordinator();
    TestCallbacks callbacks = new TestCallbacks();

    coordinator.startLoad(
        () ->
            CompletableFuture.failedFuture(
                new IllegalStateException("no valid cache snapshot found")),
        callbacks);

    assertThat(callbacks.failures).hasSize(1);
    assertThat(callbacks.failures.getFirst().errorMessage())
        .isEqualTo("Live catalog/cache unavailable. Using bundled snapshot (Ctrl+R to retry).");
    assertThat(callbacks.failures.getFirst().statusMessage())
        .isEqualTo("Using fallback extension catalog");
    assertThat(callbacks.currentState).isInstanceOf(CatalogLoadState.Failed.class);
  }

  private static ExtensionCatalogLoadResult successResult(String id) {
    return new ExtensionCatalogLoadResult(
        List.of(new ExtensionDto(id, id, "web")), CatalogSource.LIVE, false, "", null);
  }

  private static final class TestCallbacks implements CatalogLoadIntentPort {
    private CatalogLoadState currentState;
    private final List<CatalogLoadSuccess> successes = new ArrayList<>();
    private final List<CatalogLoadFailure> failures = new ArrayList<>();
    private boolean reloadUnavailable;

    private TestCallbacks() {
      this(CatalogLoadState.initial());
    }

    private TestCallbacks(CatalogLoadState currentState) {
      this.currentState = currentState;
    }

    @Override
    public CatalogLoadState currentCatalogLoadState() {
      return currentState;
    }

    @Override
    public void scheduleOnRenderThread(Runnable task) {
      task.run();
    }

    @Override
    public void dispatchIntent(UiIntent intent) {
      switch (intent) {
        case UiIntent.CatalogLoadStartedIntent startedIntent ->
            currentState = startedIntent.nextState();
        case UiIntent.CatalogLoadCancelledIntent cancelledIntent ->
            currentState = cancelledIntent.nextState();
        case UiIntent.CatalogReloadUnavailableIntent _ -> reloadUnavailable = true;
        case UiIntent.CatalogLoadSucceededIntent succeededIntent -> {
          currentState = succeededIntent.success().nextState();
          successes.add(succeededIntent.success());
        }
        case UiIntent.CatalogLoadFailedIntent failedIntent -> {
          currentState = failedIntent.failure().nextState();
          failures.add(failedIntent.failure());
        }
        default -> throw new AssertionError("Unexpected intent: " + intent);
      }
    }
  }
}
