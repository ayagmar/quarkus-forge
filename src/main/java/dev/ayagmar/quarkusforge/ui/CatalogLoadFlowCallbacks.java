package dev.ayagmar.quarkusforge.ui;

interface CatalogLoadFlowCallbacks {
  CatalogLoadState currentCatalogLoadState();

  void scheduleOnRenderThread(Runnable task);

  void onCatalogLoadStarted(CatalogLoadState nextState, String statusMessage);

  void onCatalogLoadCancelled(CatalogLoadState nextState);

  void onCatalogReloadUnavailable();

  void onCatalogLoadSucceeded(CatalogLoadSuccess success);

  void onCatalogLoadFailed(CatalogLoadFailure failure);
}
