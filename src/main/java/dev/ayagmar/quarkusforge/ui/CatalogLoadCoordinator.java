package dev.ayagmar.quarkusforge.ui;

import dev.ayagmar.quarkusforge.api.ErrorMessageMapper;
import dev.ayagmar.quarkusforge.api.ThrowableUnwrapper;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

final class CatalogLoadCoordinator {
  private final StartupOverlayTracker startupOverlay = new StartupOverlayTracker();
  private volatile long loadToken;
  private ExtensionCatalogLoader loader;
  private CompletableFuture<?> currentLoadFuture;

  void startLoad(ExtensionCatalogLoader loader, CatalogLoadFlowCallbacks callbacks) {
    Objects.requireNonNull(loader);
    this.loader = loader;
    cancelCurrentLoad();

    long token = ++loadToken;
    callbacks.onCatalogLoadStarted(
        CatalogLoadState.loadingFrom(callbacks.currentCatalogLoadState()),
        "Loading extension catalog...");
    startupOverlay.activateIfFirstLoad(token);

    CompletableFuture<ExtensionCatalogLoadResult> loadFuture;
    try {
      loadFuture = loader.load();
    } catch (RuntimeException runtimeException) {
      loadFuture = CompletableFuture.failedFuture(runtimeException);
    }
    if (loadFuture == null) {
      loadFuture =
          CompletableFuture.failedFuture(new IllegalStateException("loader returned null future"));
    }
    CompletableFuture<ExtensionCatalogLoadResult> observedLoadFuture = loadFuture;
    currentLoadFuture = observedLoadFuture;

    observedLoadFuture.whenComplete(
        (result, throwable) ->
            callbacks.scheduleOnRenderThread(
                () -> onCompleted(token, observedLoadFuture, result, throwable, callbacks)));
  }

  void requestReload(CatalogLoadFlowCallbacks callbacks) {
    if (loader == null) {
      callbacks.onCatalogReloadUnavailable();
      return;
    }
    startLoad(loader, callbacks);
  }

  void cancel(CatalogLoadFlowCallbacks callbacks) {
    cancelCurrentLoad();
    loadToken++;
    CatalogLoadState currentState = callbacks.currentCatalogLoadState();
    if (currentState instanceof CatalogLoadState.Loading loading) {
      callbacks.onCatalogLoadCancelled(
          loading.previous() == null ? CatalogLoadState.initial() : loading.previous());
    }
  }

  void setStartupOverlayMinDuration(Duration minimumDuration) {
    startupOverlay.setMinDuration(minimumDuration);
  }

  boolean isStartupOverlayVisible(CatalogLoadState loadState) {
    return startupOverlay.isVisible(loadState.isLoading());
  }

  boolean tickStartupOverlay(CatalogLoadState loadState) {
    return startupOverlay.tick(loadState.isLoading());
  }

  private void onCompleted(
      long token,
      CompletableFuture<?> loadFuture,
      ExtensionCatalogLoadResult result,
      Throwable throwable,
      CatalogLoadFlowCallbacks callbacks) {
    if (currentLoadFuture == loadFuture) {
      currentLoadFuture = null;
    }
    if (token != loadToken) {
      return;
    }
    if (throwable instanceof CancellationException) {
      return;
    }
    if (throwable != null) {
      callbacks.onCatalogLoadFailed(failure(callbacks.currentCatalogLoadState(), throwable));
      return;
    }
    if (result == null) {
      callbacks.onCatalogLoadFailed(
          failure(callbacks.currentCatalogLoadState(), "Catalog load failed: empty load result"));
      return;
    }

    List<ExtensionCatalogItem> items =
        result.extensions().stream()
            .map(
                extension ->
                    new ExtensionCatalogItem(
                        extension.id(),
                        extension.name(),
                        extension.shortName(),
                        extension.category(),
                        extension.order(),
                        extension.description()))
            .toList();
    if (items.isEmpty()) {
      callbacks.onCatalogLoadFailed(
          failure(callbacks.currentCatalogLoadState(), "Catalog load returned no extensions"));
      return;
    }

    String statusMessage =
        !result.detailMessage().isBlank()
            ? result.detailMessage()
            : CoreTuiController.catalogLoadedStatusMessage(result.source(), result.stale());
    callbacks.onCatalogLoadSucceeded(
        new CatalogLoadSuccess(
            items,
            result.metadata(),
            result.presetExtensionsByName(),
            CatalogLoadState.loaded(result.source().label(), result.stale()),
            statusMessage));
  }

  private static CatalogLoadFailure failure(CatalogLoadState currentState, Throwable throwable) {
    return failure(
        currentState,
        catalogLoadFailureMessage(ThrowableUnwrapper.unwrapCompletionCause(throwable)));
  }

  private static CatalogLoadFailure failure(CatalogLoadState currentState, String errorMessage) {
    CatalogLoadState previousState =
        currentState instanceof CatalogLoadState.Loading loading ? loading.previous() : null;
    if (previousState instanceof CatalogLoadState.Loaded loaded && loaded.isLiveLoad()) {
      return new CatalogLoadFailure(
          previousState, errorMessage, "Catalog reload failed; keeping current catalog");
    }
    return new CatalogLoadFailure(
        CatalogLoadState.failed(errorMessage), errorMessage, "Using fallback extension catalog");
  }

  private static String catalogLoadFailureMessage(Throwable throwable) {
    String message = ErrorMessageMapper.userFriendlyError(throwable);
    if (message.contains("no valid cache snapshot found")) {
      return "Live catalog/cache unavailable. Using bundled snapshot (Ctrl+R to retry).";
    }
    return "Catalog load failed: " + message;
  }

  private void cancelCurrentLoad() {
    CompletableFuture<?> inFlight = currentLoadFuture;
    if (inFlight != null) {
      inFlight.cancel(true);
      if (currentLoadFuture == inFlight) {
        currentLoadFuture = null;
      }
    }
  }
}
