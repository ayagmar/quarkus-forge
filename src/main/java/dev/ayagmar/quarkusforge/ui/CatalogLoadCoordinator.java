package dev.ayagmar.quarkusforge.ui;

import dev.ayagmar.quarkusforge.api.ErrorMessageMapper;
import dev.ayagmar.quarkusforge.api.ThrowableUnwrapper;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

final class CatalogLoadCoordinator {
  record StartupOverlayTickResult(boolean visible, boolean repaintRequired) {}

  private final StartupOverlayTracker startupOverlay = new StartupOverlayTracker();
  private volatile long loadToken;
  private ExtensionCatalogLoader loader;
  private CompletableFuture<?> currentLoadFuture;

  void startLoad(ExtensionCatalogLoader loader, CatalogLoadIntentPort port) {
    Objects.requireNonNull(loader);
    this.loader = loader;
    cancelCurrentLoad();

    long token = ++loadToken;
    CatalogLoadState nextState = CatalogLoadState.loadingFrom(port.currentCatalogLoadState());
    startupOverlay.activateIfFirstLoad(token);
    port.dispatchIntent(
        new UiIntent.CatalogLoadStartedIntent(nextState, startupOverlay.isVisible(true)));

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
            port.scheduleOnRenderThread(
                () -> onCompleted(token, observedLoadFuture, result, throwable, port)));
  }

  void requestReload(CatalogLoadIntentPort port) {
    if (loader == null) {
      port.dispatchIntent(new UiIntent.CatalogReloadUnavailableIntent());
      return;
    }
    startLoad(loader, port);
  }

  void cancel(CatalogLoadIntentPort port) {
    cancelCurrentLoad();
    loadToken++;
    CatalogLoadState currentState = port.currentCatalogLoadState();
    if (currentState instanceof CatalogLoadState.Loading loading) {
      CatalogLoadState nextState =
          loading.previous() == null ? CatalogLoadState.initial() : loading.previous();
      port.dispatchIntent(
          new UiIntent.CatalogLoadCancelledIntent(
              nextState, startupOverlay.isVisible(nextState.isLoading())));
    }
  }

  void setStartupOverlayMinDuration(Duration minimumDuration) {
    startupOverlay.setMinDuration(minimumDuration);
  }

  StartupOverlayTickResult tickStartupOverlay(CatalogLoadState loadState) {
    boolean repaintRequired = startupOverlay.tick(loadState.isLoading());
    return new StartupOverlayTickResult(
        startupOverlay.isVisible(loadState.isLoading()), repaintRequired);
  }

  private void onCompleted(
      long token,
      CompletableFuture<?> loadFuture,
      ExtensionCatalogLoadResult result,
      Throwable throwable,
      CatalogLoadIntentPort port) {
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
      CatalogLoadFailure failure = failure(port.currentCatalogLoadState(), throwable);
      port.dispatchIntent(
          new UiIntent.CatalogLoadFailedIntent(
              failure, startupOverlay.isVisible(failure.nextState().isLoading())));
      return;
    }
    if (result == null) {
      CatalogLoadFailure failure =
          failure(port.currentCatalogLoadState(), "Catalog load failed: empty load result");
      port.dispatchIntent(
          new UiIntent.CatalogLoadFailedIntent(
              failure, startupOverlay.isVisible(failure.nextState().isLoading())));
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
      CatalogLoadFailure failure =
          failure(port.currentCatalogLoadState(), "Catalog load returned no extensions");
      port.dispatchIntent(
          new UiIntent.CatalogLoadFailedIntent(
              failure, startupOverlay.isVisible(failure.nextState().isLoading())));
      return;
    }

    String statusMessage =
        !result.detailMessage().isBlank()
            ? result.detailMessage()
            : CoreTuiController.catalogLoadedStatusMessage(result.source(), result.stale());
    CatalogLoadSuccess success =
        new CatalogLoadSuccess(
            items,
            result.metadata(),
            result.presetExtensionsByName(),
            CatalogLoadState.loaded(result.source().label(), result.stale()),
            statusMessage);
    port.dispatchIntent(
        new UiIntent.CatalogLoadSucceededIntent(
            success, startupOverlay.isVisible(success.nextState().isLoading())));
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
