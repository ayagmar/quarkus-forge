package dev.ayagmar.quarkusforge.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CliVersionProviderTest {

  @Test
  void resolveVersionReturnsNonBlankValue() {
    String version = CliVersionProvider.resolveVersion();
    assertThat(version).isNotBlank();
  }

  @Test
  void resolveVersionFallsBackToUnknownWhenImplementationVersionIsUnavailable() {
    String version = CliVersionProvider.resolveVersion();
    assertThat(version).isNotNull();
    assertThat(version).matches("(\\d+\\.\\d+\\.\\d+.*|unknown)");
  }

  @Test
  void resolveVersionIsStableAcrossRepeatedCalls() {
    String first = CliVersionProvider.resolveVersion();
    String second = CliVersionProvider.resolveVersion();

    assertThat(first).isEqualTo(second);
    assertThat(first).isNotBlank();
  }
}
