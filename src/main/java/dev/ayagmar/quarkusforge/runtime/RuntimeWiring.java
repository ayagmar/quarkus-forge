package dev.ayagmar.quarkusforge.runtime;

import dev.ayagmar.quarkusforge.api.CatalogDataService;
import dev.ayagmar.quarkusforge.api.CatalogSnapshotCache;
import dev.ayagmar.quarkusforge.api.QuarkusApiClient;
import dev.ayagmar.quarkusforge.archive.ProjectArchiveService;
import dev.ayagmar.quarkusforge.archive.SafeZipExtractor;
import dev.ayagmar.quarkusforge.domain.CliPrefill;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.headless.HeadlessGenerationService;
import dev.ayagmar.quarkusforge.persistence.ExtensionFavoritesStore;
import dev.ayagmar.quarkusforge.persistence.UserPreferencesStore;

public final class RuntimeWiring {
  private RuntimeWiring() {}

  static ExtensionFavoritesStore favoritesStore(RuntimeConfig runtimeConfig) {
    return ExtensionFavoritesStore.fileBacked(runtimeConfig.favoritesFile());
  }

  static UserPreferencesStore userPreferencesStore(RuntimeConfig runtimeConfig) {
    return UserPreferencesStore.fileBacked(runtimeConfig.preferencesFile());
  }

  public static CliPrefill loadStoredCliPrefill(RuntimeConfig runtimeConfig) {
    return userPreferencesStore(runtimeConfig).loadLastRequest();
  }

  public static void saveLastRequest(RuntimeConfig runtimeConfig, ProjectRequest request) {
    userPreferencesStore(runtimeConfig).saveLastRequest(request);
  }

  static CatalogDataService catalogDataService(
      QuarkusApiClient apiClient, RuntimeConfig runtimeConfig) {
    return new CatalogDataService(
        apiClient, new CatalogSnapshotCache(runtimeConfig.catalogCacheFile()));
  }

  static ProjectArchiveService projectArchiveService(QuarkusApiClient apiClient) {
    return new ProjectArchiveService(apiClient, new SafeZipExtractor());
  }

  public static HeadlessGenerationService headlessGenerationService(RuntimeConfig runtimeConfig) {
    QuarkusApiClient apiClient = new QuarkusApiClient(runtimeConfig.apiBaseUri());
    return HeadlessGenerationService.create(
        apiClient,
        catalogDataService(apiClient, runtimeConfig),
        projectArchiveService(apiClient),
        favoritesStore(runtimeConfig));
  }
}
