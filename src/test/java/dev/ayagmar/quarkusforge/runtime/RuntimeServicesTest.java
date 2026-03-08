package dev.ayagmar.quarkusforge.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeServicesTest {
  @TempDir Path tempDir;

  @Test
  void openBuildsSharedSessionServicesForRuntimeConfig() {
    RuntimeConfig runtimeConfig =
        new RuntimeConfig(
            URI.create("http://localhost:18080"),
            tempDir.resolve("catalog-cache.json"),
            tempDir.resolve("favorites.json"),
            tempDir.resolve("preferences.json"));

    try (RuntimeServices runtimeServices = RuntimeServices.open(runtimeConfig)) {
      assertThat(runtimeServices.apiClient()).isNotNull();
      assertThat(runtimeServices.catalogDataService()).isNotNull();
      assertThat(runtimeServices.projectArchiveService()).isNotNull();
      assertThat(runtimeServices.favoritesStore()).isNotNull();
      assertThat(runtimeServices.favoritesStore().loadFavoriteExtensionIds()).isEmpty();
    }
  }

  @Test
  void userPreferencesStoreUsesConfiguredPreferencesFile() {
    RuntimeConfig runtimeConfig =
        new RuntimeConfig(
            URI.create("http://localhost:18080"),
            tempDir.resolve("catalog-cache.json"),
            tempDir.resolve("favorites.json"),
            tempDir.resolve("preferences.json"));

    assertThat(RuntimeServices.userPreferencesStore(runtimeConfig).loadLastRequest()).isNull();
  }
}
