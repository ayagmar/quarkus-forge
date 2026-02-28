package dev.ayagmar.quarkusforge;

import dev.ayagmar.quarkusforge.api.CatalogSnapshotCache;
import dev.ayagmar.quarkusforge.ui.ExtensionFavoritesStore;
import dev.ayagmar.quarkusforge.ui.UserPreferencesStore;
import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

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
        ExtensionFavoritesStore.defaultFile(),
        UserPreferencesStore.defaultFile());
  }
}
