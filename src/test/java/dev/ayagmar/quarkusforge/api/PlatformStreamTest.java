package dev.ayagmar.quarkusforge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class PlatformStreamTest {

  @Test
  void validStreamConstructsSuccessfully() {
    PlatformStream stream =
        new PlatformStream("io.quarkus.platform:3.31", "3.31", true, List.of("21", "25"));

    assertThat(stream.key()).isEqualTo("io.quarkus.platform:3.31");
    assertThat(stream.platformVersion()).isEqualTo("3.31");
    assertThat(stream.recommended()).isTrue();
    assertThat(stream.javaVersions()).containsExactly("21", "25");
  }

  @Test
  void blankKeyThrows() {
    assertThatThrownBy(() -> new PlatformStream("   ", "3.31", false, List.of("21")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be blank");
  }

  @Test
  void blankPlatformVersionDerivesFallbackFromKey() {
    PlatformStream stream = new PlatformStream("io.quarkus.platform:3.31", "", false, List.of());

    assertThat(stream.platformVersion()).isEqualTo("3.31");
  }

  @Test
  void blankPlatformVersionFallbackWithNoDelimiterReturnsFullKey() {
    PlatformStream stream = new PlatformStream("quarkus-bom", "", false, List.of());

    assertThat(stream.platformVersion()).isEqualTo("quarkus-bom");
  }

  @Test
  void blankPlatformVersionFallbackWithTrailingDelimiterReturnsFullKey() {
    PlatformStream stream = new PlatformStream("io.quarkus.platform:", "", false, List.of());

    // key ends with ':', derivePlatformVersion returns full key
    assertThat(stream.platformVersion()).isEqualTo("io.quarkus.platform:");
  }

  @Test
  void nullJavaVersionsDefaultsToEmptyList() {
    PlatformStream stream = new PlatformStream("io.quarkus.platform:3.31", "3.31", false, null);

    assertThat(stream.javaVersions()).isEmpty();
  }

  @Test
  void javaVersionsAreNormalized() {
    PlatformStream stream =
        new PlatformStream("io.quarkus.platform:3.31", "3.31", false, List.of(" 21 ", " 25 "));

    assertThat(stream.javaVersions()).containsExactly("21", "25");
  }

  @Test
  void keyIsNormalized() {
    PlatformStream stream =
        new PlatformStream("  io.quarkus.platform:3.31  ", "3.31", false, List.of());

    assertThat(stream.key()).isEqualTo("io.quarkus.platform:3.31");
  }
}
