package dev.ayagmar.quarkusforge.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MetadataDtoEdgeCaseTest {

  @Test
  void recommendedPlatformStreamKeyReturnsRecommendedStream() {
    MetadataDto metadata =
        new MetadataDto(
            List.of("21"),
            List.of("maven"),
            Map.of("maven", List.of("21")),
            List.of(
                new PlatformStream("io.quarkus.platform:3.30", "3.30", false, List.of("21")),
                new PlatformStream("io.quarkus.platform:3.31", "3.31", true, List.of("21"))));

    assertThat(metadata.recommendedPlatformStreamKey()).isEqualTo("io.quarkus.platform:3.31");
  }

  @Test
  void recommendedPlatformStreamKeyFallsBackToFirstWhenNoneRecommended() {
    MetadataDto metadata =
        new MetadataDto(
            List.of("21"),
            List.of("maven"),
            Map.of("maven", List.of("21")),
            List.of(
                new PlatformStream("io.quarkus.platform:3.30", "3.30", false, List.of("21")),
                new PlatformStream("io.quarkus.platform:3.31", "3.31", false, List.of("21"))));

    assertThat(metadata.recommendedPlatformStreamKey()).isEqualTo("io.quarkus.platform:3.30");
  }

  @Test
  void recommendedPlatformStreamKeyReturnsEmptyWhenNoStreams() {
    MetadataDto metadata =
        new MetadataDto(List.of("21"), List.of("maven"), Map.of("maven", List.of("21")));

    assertThat(metadata.recommendedPlatformStreamKey()).isEmpty();
  }

  @Test
  void findPlatformStreamReturnsCaseInsensitiveMatch() {
    MetadataDto metadata =
        new MetadataDto(
            List.of("21"),
            List.of("maven"),
            Map.of("maven", List.of("21")),
            List.of(new PlatformStream("io.quarkus.platform:3.31", "3.31", true, List.of("21"))));

    PlatformStream found = metadata.findPlatformStream("IO.QUARKUS.PLATFORM:3.31");
    assertThat(found).isNotNull();
    assertThat(found.key()).isEqualTo("io.quarkus.platform:3.31");
  }

  @Test
  void findPlatformStreamReturnsNullForBlankKey() {
    MetadataDto metadata =
        new MetadataDto(
            List.of("21"),
            List.of("maven"),
            Map.of("maven", List.of("21")),
            List.of(new PlatformStream("io.quarkus.platform:3.31", "3.31", true, List.of("21"))));

    assertThat(metadata.findPlatformStream("  ")).isNull();
  }

  @Test
  void findPlatformStreamReturnsNullForUnknownKey() {
    MetadataDto metadata =
        new MetadataDto(
            List.of("21"),
            List.of("maven"),
            Map.of("maven", List.of("21")),
            List.of(new PlatformStream("io.quarkus.platform:3.31", "3.31", true, List.of("21"))));

    assertThat(metadata.findPlatformStream("io.quarkus.platform:99.99")).isNull();
  }

  @Test
  void deduplicatesPlatformStreamsByNormalizedKey() {
    MetadataDto metadata =
        new MetadataDto(
            List.of("21"),
            List.of("maven"),
            Map.of("maven", List.of("21")),
            List.of(
                new PlatformStream("IO.QUARKUS.PLATFORM:3.31", "3.31", true, List.of("21")),
                new PlatformStream("io.quarkus.platform:3.31", "3.31", false, List.of("21"))));

    assertThat(metadata.platformStreams()).hasSize(1);
  }

  @Test
  void javaVersionsAreNormalized() {
    MetadataDto metadata =
        new MetadataDto(List.of(" 21 ", " 25 "), List.of("maven"), Map.of("maven", List.of("21")));

    assertThat(metadata.javaVersions()).containsExactly("21", "25");
  }
}
