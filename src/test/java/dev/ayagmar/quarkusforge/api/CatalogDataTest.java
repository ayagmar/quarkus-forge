package dev.ayagmar.quarkusforge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CatalogDataTest {
  private static final MetadataDto METADATA =
      new MetadataDto(
          List.of("21"),
          List.of("maven"),
          Map.of("maven", List.of("21")),
          List.of(new PlatformStream("io.quarkus.platform:3.31", "3.31", true, List.of("21"))));

  private static final List<ExtensionDto> EXTENSIONS =
      List.of(new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest"));

  @Test
  void sourceLabelReturnsPlainLabelWhenNotStale() {
    CatalogData data = new CatalogData(METADATA, EXTENSIONS, CatalogSource.CACHE, false, "");
    assertThat(data.sourceLabel()).isEqualTo("cache");
  }

  @Test
  void sourceLabelAppendsStaleSuffixWhenStale() {
    CatalogData data = new CatalogData(METADATA, EXTENSIONS, CatalogSource.CACHE, true, "");
    assertThat(data.sourceLabel()).isEqualTo("cache [stale]");
  }

  @Test
  void sourceLabelLiveSourceShowsLive() {
    CatalogData data = new CatalogData(METADATA, EXTENSIONS, CatalogSource.LIVE, false, "");
    assertThat(data.sourceLabel()).isEqualTo("live");
  }

  @Test
  void rejectsStaleWithNonCacheSource() {
    assertThatThrownBy(() -> new CatalogData(METADATA, EXTENSIONS, CatalogSource.LIVE, true, ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Only cache-sourced");
  }

  @Test
  void rejectsEmptyExtensions() {
    assertThatThrownBy(() -> new CatalogData(METADATA, List.of(), CatalogSource.LIVE, false, ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be empty");
  }

  @Test
  void detailMessageIsStrippedAndDefaultedWhenNull() {
    CatalogData data = new CatalogData(METADATA, EXTENSIONS, CatalogSource.LIVE, false, null);
    assertThat(data.detailMessage()).isEmpty();

    CatalogData data2 =
        new CatalogData(METADATA, EXTENSIONS, CatalogSource.LIVE, false, "  detail  ");
    assertThat(data2.detailMessage()).isEqualTo("detail");
  }

  @Test
  void extensionsAreImmutableCopy() {
    var mutableExtensions =
        new java.util.ArrayList<>(
            List.of(new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest")));
    var expectedSnapshot = List.copyOf(mutableExtensions);
    CatalogData data = new CatalogData(METADATA, mutableExtensions, CatalogSource.LIVE, false, "");

    mutableExtensions.clear();

    assertThat(data.extensions()).isNotSameAs(mutableExtensions);
    assertThat(data.extensions()).containsExactlyElementsOf(expectedSnapshot);
    assertThat(data.extensions()).isUnmodifiable();
  }
}
