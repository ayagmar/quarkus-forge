package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ayagmar.quarkusforge.api.CatalogSource;
import dev.ayagmar.quarkusforge.api.ExtensionDto;
import dev.ayagmar.quarkusforge.api.MetadataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExtensionCatalogLoadResultTest {

  private static ExtensionDto extension(String id) {
    return new ExtensionDto(id, id, id);
  }

  @Test
  void liveFactoryCreatesNonStaleResult() {
    var result = ExtensionCatalogLoadResult.live(List.of(extension("rest")));

    assertThat(result.source()).isEqualTo(CatalogSource.LIVE);
    assertThat(result.stale()).isFalse();
    assertThat(result.detailMessage()).isEmpty();
    assertThat(result.metadata()).isNull();
    assertThat(result.presetExtensionsByName()).isEmpty();
  }

  @Test
  void cacheSourceAllowsStaleFlag() {
    var result =
        new ExtensionCatalogLoadResult(
            List.of(extension("rest")), CatalogSource.CACHE, true, "stale data", null);

    assertThat(result.stale()).isTrue();
    assertThat(result.metadataSource()).isEqualTo(MetadataSource.CACHE);
    assertThat(result.metadataSourceLabel()).isEqualTo("cache [stale]");
    assertThat(result.detailMessage()).isEqualTo("stale data");
  }

  @Test
  void supportsLiveCatalogWithBundledSnapshotMetadata() {
    var result =
        new ExtensionCatalogLoadResult(
            List.of(extension("rest")),
            CatalogSource.LIVE,
            MetadataSource.SNAPSHOT,
            false,
            "snapshot fallback",
            null,
            Map.of());

    assertThat(result.metadataSource()).isEqualTo(MetadataSource.SNAPSHOT);
    assertThat(result.metadataSourceLabel()).isEqualTo("bundled snapshot");
  }

  @Test
  void liveSourceRejectsStaleFlag() {
    assertThatThrownBy(
            () ->
                new ExtensionCatalogLoadResult(
                    List.of(extension("rest")), CatalogSource.LIVE, true, "", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("stale");
  }

  @Test
  void nullDetailMessageDefaultsToEmpty() {
    var result =
        new ExtensionCatalogLoadResult(
            List.of(extension("rest")), CatalogSource.LIVE, false, null, null);

    assertThat(result.detailMessage()).isEmpty();
  }

  @Test
  void incompatibleMetadataSourceIsRejected() {
    assertThatThrownBy(
            () ->
                new ExtensionCatalogLoadResult(
                    List.of(extension("rest")),
                    CatalogSource.CACHE,
                    MetadataSource.SNAPSHOT,
                    false,
                    "",
                    null,
                    Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cache metadata");
  }

  @Test
  void detailMessageIsStripped() {
    var result =
        new ExtensionCatalogLoadResult(
            List.of(extension("rest")), CatalogSource.LIVE, false, "  trimmed  ", null);

    assertThat(result.detailMessage()).isEqualTo("trimmed");
  }

  @Test
  void extensionsAreImmutableCopy() {
    var mutable = new java.util.ArrayList<>(List.of(extension("rest")));
    var result = new ExtensionCatalogLoadResult(mutable, CatalogSource.LIVE, false, "", null);

    mutable.clear();
    assertThat(result.extensions()).hasSize(1);
    assertThatThrownBy(() -> result.extensions().add(extension("jdbc")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void presetMapNormalizesKeys() {
    Map<String, List<String>> presets = new LinkedHashMap<>();
    presets.put("  Web  ", List.of("rest", "resteasy"));
    presets.put(null, List.of("should-skip"));
    presets.put("  ", List.of("should-skip-too"));

    var result =
        new ExtensionCatalogLoadResult(
            List.of(extension("rest")), CatalogSource.LIVE, false, "", null, presets);

    assertThat(result.presetExtensionsByName()).containsKey("web");
    assertThat(result.presetExtensionsByName()).doesNotContainKey("  Web  ");
    assertThat(result.presetExtensionsByName()).hasSize(1);
  }

  @Test
  void presetMapHandlesNullValueList() {
    Map<String, List<String>> presets = new LinkedHashMap<>();
    presets.put("core", null);

    var result =
        new ExtensionCatalogLoadResult(
            List.of(extension("rest")), CatalogSource.LIVE, false, "", null, presets);

    assertThat(result.presetExtensionsByName().get("core")).isEmpty();
  }

  @Test
  void nullPresetMapDefaultsToEmpty() {
    var result =
        new ExtensionCatalogLoadResult(
            List.of(extension("rest")), CatalogSource.LIVE, false, "", null, null);

    assertThat(result.presetExtensionsByName()).isEmpty();
  }

  @Test
  void emptyPresetMapDefaultsToEmpty() {
    var result =
        new ExtensionCatalogLoadResult(
            List.of(extension("rest")), CatalogSource.LIVE, false, "", null, Map.of());

    assertThat(result.presetExtensionsByName()).isEmpty();
  }
}
