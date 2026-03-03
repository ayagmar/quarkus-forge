package dev.ayagmar.quarkusforge.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MetadataSnapshotLoaderTest {

  @Test
  void loadDefaultReturnsValidMetadataFromResource() {
    MetadataDto metadata = MetadataSnapshotLoader.loadDefault();

    assertThat(metadata).isNotNull();
    assertThat(metadata.javaVersions()).isNotEmpty();
    assertThat(metadata.buildTools()).isNotEmpty();
    assertThat(metadata.platformStreams()).isNotEmpty();
  }

  @Test
  void loadDefaultContainsRecommendedStream() {
    MetadataDto metadata = MetadataSnapshotLoader.loadDefault();

    String recommended = metadata.recommendedPlatformStreamKey();
    assertThat(recommended).isNotBlank();
  }
}
