package dev.ayagmar.quarkusforge.headless;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ayagmar.quarkusforge.api.CatalogData;
import dev.ayagmar.quarkusforge.persistence.ExtensionFavoritesStore;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HeadlessExtensionResolutionServiceTest {
  private static final Set<String> KNOWN_EXTENSION_IDS =
      Set.of("io.quarkus:quarkus-rest", "io.quarkus:quarkus-arc");

  @Test
  void resolvesBuiltInPresetsAndExplicitExtensionsWithoutDuplicates() throws Exception {
    StubCatalogLoader catalogLoader = new StubCatalogLoader();
    HeadlessExtensionResolutionService service =
        new HeadlessExtensionResolutionService(catalogLoader, ExtensionFavoritesStore.inMemory());

    List<String> resolved =
        service.resolveExtensionIds(
            "io.quarkus.platform:3.31",
            List.of("io.quarkus:quarkus-arc"),
            List.of("web"),
            KNOWN_EXTENSION_IDS,
            Duration.ofSeconds(1));

    assertThat(resolved).containsExactly("io.quarkus:quarkus-rest", "io.quarkus:quarkus-arc");
    assertThat(catalogLoader.lastPresetPlatformStream).isEqualTo("io.quarkus.platform:3.31");
  }

  @Test
  void favoritesPresetFiltersRemovedFavoriteIds() throws Exception {
    ExtensionFavoritesStore favoritesStore = ExtensionFavoritesStore.inMemory();
    favoritesStore.saveAll(Set.of("io.quarkus:quarkus-rest", "io.quarkus:removed"), List.of());
    HeadlessExtensionResolutionService service =
        new HeadlessExtensionResolutionService(new StubCatalogLoader(), favoritesStore);

    List<String> resolved =
        service.resolveExtensionIds(
            "io.quarkus.platform:3.31",
            List.of(),
            List.of("favorites"),
            KNOWN_EXTENSION_IDS,
            Duration.ofSeconds(1));

    assertThat(resolved).containsExactly("io.quarkus:quarkus-rest");
  }

  @Test
  void unknownPresetListsAllowedValues() {
    HeadlessExtensionResolutionService service =
        new HeadlessExtensionResolutionService(
            new StubCatalogLoader(), ExtensionFavoritesStore.inMemory());

    assertThatThrownBy(
            () ->
                service.resolveExtensionIds(
                    "io.quarkus.platform:3.31",
                    List.of(),
                    List.of("nonexistent"),
                    KNOWN_EXTENSION_IDS,
                    Duration.ofSeconds(1)))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("unknown preset 'nonexistent'")
        .hasMessageContaining("favorites")
        .hasMessageContaining("web");
  }

  @Test
  void blankAndUnknownExtensionInputsFailValidation() {
    HeadlessExtensionResolutionService service =
        new HeadlessExtensionResolutionService(
            new StubCatalogLoader(), ExtensionFavoritesStore.inMemory());

    assertThatThrownBy(
            () ->
                service.resolveExtensionIds(
                    "io.quarkus.platform:3.31",
                    List.of("   ", "io.quarkus:missing"),
                    List.of(),
                    KNOWN_EXTENSION_IDS,
                    Duration.ofSeconds(1)))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("must not be blank")
        .hasMessageContaining("unknown extension id 'io.quarkus:missing'");
  }

  @Test
  void normalizePresetsTrimsCaseAndDuplicates() {
    assertThat(
            HeadlessExtensionResolutionService.normalizePresets(
                Arrays.asList("  Web  ", "favorites", "web", "", null)))
        .containsExactly("web", "favorites");
  }

  @Test
  void nullPresetAndExtensionInputsBehaveLikeEmptyLists() throws Exception {
    HeadlessExtensionResolutionService service =
        new HeadlessExtensionResolutionService(
            new StubCatalogLoader(), ExtensionFavoritesStore.inMemory());

    List<String> resolved =
        service.resolveExtensionIds(
            "io.quarkus.platform:3.31", null, null, KNOWN_EXTENSION_IDS, Duration.ofSeconds(1));

    assertThat(resolved).isEmpty();
  }

  private static final class StubCatalogLoader implements HeadlessCatalogLoader {
    String lastPresetPlatformStream;

    @Override
    public CatalogData loadCatalogData(Duration timeout) {
      throw new UnsupportedOperationException("not used in this test");
    }

    @Override
    public Map<String, List<String>> loadBuiltInPresets(String platformStream, Duration timeout) {
      lastPresetPlatformStream = platformStream;
      return Map.of("web", List.of("io.quarkus:quarkus-rest"));
    }

    @Override
    public void close() {}
  }
}
