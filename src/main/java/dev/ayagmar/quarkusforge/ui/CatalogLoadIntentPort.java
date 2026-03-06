package dev.ayagmar.quarkusforge.ui;

interface CatalogLoadIntentPort {
  CatalogLoadState currentCatalogLoadState();

  void scheduleOnRenderThread(Runnable task);

  void dispatchIntent(UiIntent intent);
}
