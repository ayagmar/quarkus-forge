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
  void resolveVersionFallsBackToResourceOrUnknown() {
    // In test environment, implementation version from manifest is typically null,
    // so it falls back to version.properties resource or "unknown"
    String version = CliVersionProvider.resolveVersion();
    assertThat(version).isNotNull();
    // Must be a version string or "unknown"
    assertThat(version).matches("(\\d+\\.\\d+\\.\\d+.*|unknown)");
  }

  @Test
  void getVersionReturnsNonEmptyArray() {
    CliVersionProvider provider = new CliVersionProvider();
    String[] versions = provider.getVersion();
    assertThat(versions).hasSize(1);
    assertThat(versions[0]).isNotBlank();
  }
}
