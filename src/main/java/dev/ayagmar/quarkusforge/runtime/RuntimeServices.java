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
import java.util.Objects;

/** Session-lifetime runtime services shared by interactive and headless entry paths. */
public record RuntimeServices(
    QuarkusApiClient apiClient,
    CatalogDataService catalogDataService,
    ProjectArchiveService projectArchiveService,
    ExtensionFavoritesStore favoritesStore)
    implements AutoCloseable {
  public RuntimeServices {
    Objects.requireNonNull(apiClient);
    Objects.requireNonNull(catalogDataService);
    Objects.requireNonNull(projectArchiveService);
    Objects.requireNonNull(favoritesStore);
  }

  public static RuntimeServices open(RuntimeConfig runtimeConfig) {
    QuarkusApiClient apiClient = new QuarkusApiClient(runtimeConfig.apiBaseUri());
    return new RuntimeServices(
        apiClient,
        catalogDataService(apiClient, runtimeConfig),
        projectArchiveService(apiClient),
        favoritesStore(runtimeConfig));
  }

  public static CliPrefill loadStoredCliPrefill(RuntimeConfig runtimeConfig) {
    return userPreferencesStore(runtimeConfig).loadLastRequest();
  }

  public static void saveLastRequest(RuntimeConfig runtimeConfig, ProjectRequest request) {
    userPreferencesStore(runtimeConfig).saveLastRequest(request);
  }

  public static HeadlessGenerationService openHeadlessGenerationService(
      RuntimeConfig runtimeConfig) {
    RuntimeServices runtimeServices = open(runtimeConfig);
    return HeadlessGenerationService.create(
        runtimeServices.apiClient(),
        runtimeServices.catalogDataService(),
        runtimeServices.projectArchiveService(),
        runtimeServices.favoritesStore(),
        runtimeServices);
  }

  static ExtensionFavoritesStore favoritesStore(RuntimeConfig runtimeConfig) {
    return ExtensionFavoritesStore.fileBacked(runtimeConfig.favoritesFile());
  }

  public static UserPreferencesStore userPreferencesStore(RuntimeConfig runtimeConfig) {
    return UserPreferencesStore.fileBacked(runtimeConfig.preferencesFile());
  }

  static CatalogDataService catalogDataService(
      QuarkusApiClient apiClient, RuntimeConfig runtimeConfig) {
    return new CatalogDataService(
        apiClient, new CatalogSnapshotCache(runtimeConfig.catalogCacheFile()));
  }

  static ProjectArchiveService projectArchiveService(QuarkusApiClient apiClient) {
    return new ProjectArchiveService(apiClient, new SafeZipExtractor());
  }

  @Override
  public void close() {
    apiClient.close();
  }
}
