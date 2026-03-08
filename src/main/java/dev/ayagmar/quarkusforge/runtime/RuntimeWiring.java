package dev.ayagmar.quarkusforge.runtime;

import dev.ayagmar.quarkusforge.domain.CliPrefill;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.headless.HeadlessGenerationService;
import dev.ayagmar.quarkusforge.persistence.UserPreferencesStore;

public final class RuntimeWiring {
  private RuntimeWiring() {}

  static UserPreferencesStore userPreferencesStore(RuntimeConfig runtimeConfig) {
    return RuntimeServices.userPreferencesStore(runtimeConfig);
  }

  public static CliPrefill loadStoredCliPrefill(RuntimeConfig runtimeConfig) {
    return userPreferencesStore(runtimeConfig).loadLastRequest();
  }

  public static void saveLastRequest(RuntimeConfig runtimeConfig, ProjectRequest request) {
    userPreferencesStore(runtimeConfig).saveLastRequest(request);
  }

  public static HeadlessGenerationService headlessGenerationService(RuntimeConfig runtimeConfig) {
    RuntimeServices runtimeServices = RuntimeServices.open(runtimeConfig);
    return HeadlessGenerationService.create(
        runtimeServices.apiClient(),
        runtimeServices.catalogDataService(),
        runtimeServices.projectArchiveService(),
        runtimeServices.favoritesStore());
  }
}
