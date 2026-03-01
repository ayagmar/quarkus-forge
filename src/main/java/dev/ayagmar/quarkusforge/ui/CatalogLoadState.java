package dev.ayagmar.quarkusforge.ui;

/**
 * Immutable representation of the extension catalog's load state. Replaces four mutable fields
 * (loading, errorMessage, source, stale) with a single sealed type that makes the state machine
 * explicit.
 */
sealed interface CatalogLoadState {
  boolean isLoading();

  String errorMessage();

  String sourceLabel();

  boolean isStale();

  /** Initial state or actively loading from remote/cache. */
  record Loading(CatalogLoadState previous) implements CatalogLoadState {
    @Override
    public boolean isLoading() {
      return true;
    }

    @Override
    public String errorMessage() {
      return "";
    }

    @Override
    public String sourceLabel() {
      return previous != null ? previous.sourceLabel() : "";
    }

    @Override
    public boolean isStale() {
      return previous != null && previous.isStale();
    }
  }

  /** Catalog loaded successfully from a known source. */
  record Loaded(String sourceLabel, boolean isStale, boolean isLiveLoad)
      implements CatalogLoadState {
    @Override
    public boolean isLoading() {
      return false;
    }

    @Override
    public String errorMessage() {
      return "";
    }
  }

  /** Catalog load failed with an error message. */
  record Failed(String errorMessage) implements CatalogLoadState {
    @Override
    public boolean isLoading() {
      return false;
    }

    @Override
    public String sourceLabel() {
      return "";
    }

    @Override
    public boolean isStale() {
      return false;
    }
  }

  static CatalogLoadState loading() {
    return new Loading(null);
  }

  static CatalogLoadState loadingFrom(CatalogLoadState previous) {
    return new Loading(previous);
  }

  static CatalogLoadState loaded(String sourceLabel, boolean stale) {
    return new Loaded(sourceLabel, stale, true);
  }

  static CatalogLoadState failed(String errorMessage) {
    return new Failed(errorMessage);
  }

  static CatalogLoadState initial() {
    return new Loaded("snapshot", false, false);
  }
}
