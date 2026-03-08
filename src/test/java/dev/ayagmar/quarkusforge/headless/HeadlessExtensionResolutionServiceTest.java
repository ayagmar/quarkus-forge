package dev.ayagmar.quarkusforge.headless;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ayagmar.quarkusforge.api.CatalogData;
import dev.ayagmar.quarkusforge.persistence.ExtensionFavoritesStore;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class HeadlessExtensionResolutionServiceTest {
  private static final Duration TIMEOUT = Duration.ofSeconds(5);
  private static final Set<String> KNOWN_EXTENSION_IDS =
      Set.of("io.quarkus:quarkus-rest", "io.quarkus:quarkus-arc");

  @Test
  void resolvesFavoritesAndBuiltInPresetsIntoKnownExtensions()
      throws ExecutionException, InterruptedException, TimeoutException {
    StubCatalogLoader catalogLoader = new StubCatalogLoader();
    HeadlessExtensionResolutionService service =
        new HeadlessExtensionResolutionService(
            catalogLoader, favoritesStore("io.quarkus:quarkus-arc"));

    List<String> resolved =
        service.resolveExtensionIds(
            "io.quarkus.platform:3.31",
            List.of("io.quarkus:quarkus-rest"),
            List.of("favorites"),
            KNOWN_EXTENSION_IDS,
            TIMEOUT);

    assertThat(resolved).containsExactly("io.quarkus:quarkus-arc", "io.quarkus:quarkus-rest");
    assertThat(catalogLoader.loadBuiltInPresetsCalls).isZero();
  }

  @Test
  void loadsBuiltInPresetsOnlyWhenRequested()
      throws ExecutionException, InterruptedException, TimeoutException {
    StubCatalogLoader catalogLoader = new StubCatalogLoader();
    HeadlessExtensionResolutionService service =
        new HeadlessExtensionResolutionService(catalogLoader, favoritesStore());

    List<String> resolved =
        service.resolveExtensionIds(
            "io.quarkus.platform:3.31",
            List.of(),
            List.of("  Web  "),
            KNOWN_EXTENSION_IDS,
            TIMEOUT);

    assertThat(resolved).containsExactly("io.quarkus:quarkus-rest");
    assertThat(catalogLoader.loadBuiltInPresetsCalls).isEqualTo(1);
    assertThat(catalogLoader.lastPlatformStream).isEqualTo("io.quarkus.platform:3.31");
  }

  @Test
  void rejectsUnknownPresetWithAllowedPresetNames() {
    StubCatalogLoader catalogLoader = new StubCatalogLoader();
    HeadlessExtensionResolutionService service =
        new HeadlessExtensionResolutionService(catalogLoader, favoritesStore());

    assertThatThrownBy(
            () ->
                service.resolveExtensionIds(
                    "io.quarkus.platform:3.31",
                    List.of(),
                    List.of("missing"),
                    KNOWN_EXTENSION_IDS,
                    TIMEOUT))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("unknown preset 'missing'")
        .hasMessageContaining("favorites")
        .hasMessageContaining("web");
  }

  @Test
  void rejectsBlankAndUnknownExtensionInputs() {
    StubCatalogLoader catalogLoader = new StubCatalogLoader();
    HeadlessExtensionResolutionService service =
        new HeadlessExtensionResolutionService(catalogLoader, favoritesStore());

    assertThatThrownBy(
            () ->
                service.resolveExtensionIds(
                    "io.quarkus.platform:3.31",
                    List.of("   ", "io.quarkus:missing"),
                    List.of(),
                    KNOWN_EXTENSION_IDS,
                    TIMEOUT))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("extension: must not be blank")
        .hasMessageContaining("unknown extension id 'io.quarkus:missing'");
  }

  @Test
  void propagatesPresetLoadFailures() {
    StubCatalogLoader catalogLoader = new StubCatalogLoader();
    catalogLoader.presetLoadTimeout = new TimeoutException("timed out");
    HeadlessExtensionResolutionService service =
        new HeadlessExtensionResolutionService(catalogLoader, favoritesStore());

    assertThatThrownBy(
            () ->
                service.resolveExtensionIds(
                    "io.quarkus.platform:3.31",
                    List.of(),
                    List.of("web"),
                    KNOWN_EXTENSION_IDS,
                    TIMEOUT))
        .isInstanceOf(TimeoutException.class)
        .hasMessageContaining("timed out");
  }

  private static ExtensionFavoritesStore favoritesStore(String... favoriteExtensionIds) {
    Set<String> favorites = new LinkedHashSet<>(List.of(favoriteExtensionIds));
    return new ExtensionFavoritesStore() {
      @Override
      public Set<String> loadFavoriteExtensionIds() {
        return favorites;
      }

      @Override
      public List<String> loadRecentExtensionIds() {
        return List.of();
      }

      @Override
      public void saveAll(Set<String> favoriteExtensionIds, List<String> recentExtensionIds) {}
    };
  }

  private static final class StubCatalogLoader implements HeadlessCatalogLoader {
    TimeoutException presetLoadTimeout;
    int loadBuiltInPresetsCalls;
    String lastPlatformStream;

    @Override
    public CatalogData loadCatalogData(Duration timeout) {
      throw new UnsupportedOperationException("not used in this test");
    }

    @Override
    public Map<String, List<String>> loadBuiltInPresets(String platformStream, Duration timeout)
        throws TimeoutException {
      loadBuiltInPresetsCalls++;
      lastPlatformStream = platformStream;
      if (presetLoadTimeout != null) {
        throw presetLoadTimeout;
      }
      return Map.of("web", List.of("io.quarkus:quarkus-rest"));
    }

    @Override
    public void close() {}
  }
}
