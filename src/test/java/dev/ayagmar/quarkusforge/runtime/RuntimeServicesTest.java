package dev.ayagmar.quarkusforge.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.headless.HeadlessGenerationService;
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

  @Test
  void loadAndSaveStoredCliPrefillRoundTripThroughConfiguredPreferencesFile() {
    RuntimeConfig runtimeConfig =
        new RuntimeConfig(
            URI.create("http://localhost:18080"),
            tempDir.resolve("catalog-cache.json"),
            tempDir.resolve("favorites.json"),
            tempDir.resolve("preferences.json"));

    RuntimeServices.saveLastRequest(
        runtimeConfig,
        new ProjectRequest(
            "com.example", "demo-app", "1.0.0", "com.example.demo", ".", "maven", "21"));

    var prefill = RuntimeServices.loadStoredCliPrefill(runtimeConfig);

    assertThat(prefill).isNotNull();
    assertThat(prefill.groupId()).isEqualTo("com.example");
    assertThat(prefill.artifactId()).isEqualTo("demo-app");
  }

  @Test
  void openHeadlessGenerationServiceBuildsCloseableService() {
    RuntimeConfig runtimeConfig =
        new RuntimeConfig(
            URI.create("http://localhost:18080"),
            tempDir.resolve("catalog-cache.json"),
            tempDir.resolve("favorites.json"),
            tempDir.resolve("preferences.json"));

    try (HeadlessGenerationService service =
        RuntimeServices.openHeadlessGenerationService(runtimeConfig)) {
      assertThat(service).isNotNull();
    }
  }

  @Test
  void runtimeSessionOwnsHeadlessGenerationServiceOpening() {
    RuntimeConfig runtimeConfig =
        new RuntimeConfig(
            URI.create("http://localhost:18080"),
            tempDir.resolve("catalog-cache.json"),
            tempDir.resolve("favorites.json"),
            tempDir.resolve("preferences.json"));

    try (RuntimeServices runtimeServices = RuntimeServices.open(runtimeConfig)) {
      try (HeadlessGenerationService service = runtimeServices.openHeadlessGenerationService()) {
        assertThat(service).isNotNull();
      }
    }
  }
}
