package dev.ayagmar.quarkusforge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CachedCatalogSnapshotTest {
  private static final MetadataDto METADATA =
      new MetadataDto(
          List.of("21"),
          List.of("maven"),
          Map.of("maven", List.of("21")),
          List.of(new PlatformStream("io.quarkus.platform:3.31", "3.31", true, List.of("21"))));

  private static final List<ExtensionDto> EXTENSIONS =
      List.of(new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest"));

  @Test
  void validConstructionSucceeds() {
    Instant now = Instant.now();
    CachedCatalogSnapshot snapshot = new CachedCatalogSnapshot(METADATA, EXTENSIONS, now, false);

    assertThat(snapshot.metadata()).isEqualTo(METADATA);
    assertThat(snapshot.extensions()).containsExactlyElementsOf(EXTENSIONS);
    assertThat(snapshot.fetchedAt()).isEqualTo(now);
    assertThat(snapshot.stale()).isFalse();
  }

  @Test
  void extensionsAreImmutableCopy() {
    CachedCatalogSnapshot snapshot =
        new CachedCatalogSnapshot(METADATA, EXTENSIONS, Instant.now(), false);

    assertThat(snapshot.extensions()).isUnmodifiable();
  }

  @Test
  void rejectsEmptyExtensions() {
    assertThatThrownBy(() -> new CachedCatalogSnapshot(METADATA, List.of(), Instant.now(), false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be empty");
  }

  @Test
  void rejectsNullMetadata() {
    assertThatThrownBy(() -> new CachedCatalogSnapshot(null, EXTENSIONS, Instant.now(), false))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejectsNullFetchedAt() {
    assertThatThrownBy(() -> new CachedCatalogSnapshot(METADATA, EXTENSIONS, null, false))
        .isInstanceOf(NullPointerException.class);
  }
}
