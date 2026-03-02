package dev.ayagmar.quarkusforge;

import dev.ayagmar.quarkusforge.api.CatalogSnapshotCache;
import dev.ayagmar.quarkusforge.api.ForgeDataPaths;
import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Immutable configuration bundle passed through the application. Holds all file paths and the API
 * base URI so that tests can redirect to a local server or temp directory without altering global
 * state.
 */
record RuntimeConfig(
    URI apiBaseUri, Path catalogCacheFile, Path favoritesFile, Path preferencesFile) {
  RuntimeConfig {
    Objects.requireNonNull(apiBaseUri);
    Objects.requireNonNull(catalogCacheFile);
    Objects.requireNonNull(favoritesFile);
    Objects.requireNonNull(preferencesFile);
  }

  static RuntimeConfig defaults() {
    return new RuntimeConfig(
        URI.create("https://code.quarkus.io"),
        CatalogSnapshotCache.defaultCacheFile(),
        ForgeDataPaths.favoritesFile(),
        ForgeDataPaths.preferencesFile());
  }
}
