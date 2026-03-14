package dev.ayagmar.quarkusforge.runtime;

import dev.ayagmar.quarkusforge.api.CatalogSnapshotCache;
import dev.ayagmar.quarkusforge.api.ForgeDataPaths;
import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable configuration bundle passed through the application. Holds all file paths and the API
 * base URI so that tests can redirect to a local server or temp directory without altering global
 * state.
 */
public record RuntimeConfig(
    URI apiBaseUri, Path catalogCacheFile, Path favoritesFile, Path preferencesFile) {
  public RuntimeConfig {
    Objects.requireNonNull(apiBaseUri);
    Objects.requireNonNull(catalogCacheFile);
    Objects.requireNonNull(favoritesFile);
    Objects.requireNonNull(preferencesFile);

    apiBaseUri = validatedApiBaseUri(apiBaseUri);
    catalogCacheFile = catalogCacheFile.toAbsolutePath().normalize();
    favoritesFile = favoritesFile.toAbsolutePath().normalize();
    preferencesFile = preferencesFile.toAbsolutePath().normalize();
  }

  public static RuntimeConfig defaults() {
    return new RuntimeConfig(
        URI.create("https://code.quarkus.io"),
        CatalogSnapshotCache.defaultCacheFile(),
        ForgeDataPaths.favoritesFile(),
        ForgeDataPaths.preferencesFile());
  }

  private static URI validatedApiBaseUri(URI apiBaseUri) {
    if (!apiBaseUri.isAbsolute()) {
      throw new IllegalArgumentException("apiBaseUri must be absolute");
    }
    if (apiBaseUri.getRawUserInfo() != null) {
      throw new IllegalArgumentException("apiBaseUri must not contain user info");
    }
    if (apiBaseUri.getRawQuery() != null || apiBaseUri.getRawFragment() != null) {
      throw new IllegalArgumentException("apiBaseUri must not contain query or fragment data");
    }
    String scheme = apiBaseUri.getScheme();
    if (scheme == null) {
      throw new IllegalArgumentException("apiBaseUri must include a scheme");
    }
    String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
    if (!normalizedScheme.equals("https") && !normalizedScheme.equals("http")) {
      throw new IllegalArgumentException("apiBaseUri must use http or https");
    }
    String host = apiBaseUri.getHost();
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("apiBaseUri must include a host");
    }
    if (normalizedScheme.equals("http") && !LoopbackHosts.isLoopbackHost(host)) {
      throw new IllegalArgumentException(
          "apiBaseUri must use https unless it targets localhost or a loopback address");
    }
    return apiBaseUri.normalize();
  }
}
