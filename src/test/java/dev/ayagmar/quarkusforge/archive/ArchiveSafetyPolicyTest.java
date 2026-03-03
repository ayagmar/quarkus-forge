package dev.ayagmar.quarkusforge.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ArchiveSafetyPolicyTest {

  @Test
  void defaultsProvidesReasonableValues() {
    ArchiveSafetyPolicy policy = ArchiveSafetyPolicy.defaults();

    assertThat(policy.maxEntries()).isEqualTo(20_000);
    assertThat(policy.maxTotalUncompressedBytes()).isEqualTo(512L * 1024L * 1024L);
    assertThat(policy.maxCompressionRatio()).isEqualTo(150.0d);
    assertThat(policy.minBytesForCompressionRatioCheck()).isEqualTo(1L * 1024L * 1024L);
  }

  @Test
  void validPolicyConstructs() {
    ArchiveSafetyPolicy policy = new ArchiveSafetyPolicy(100, 1024, 10.0d, 0);
    assertThat(policy.maxEntries()).isEqualTo(100);
  }

  @Test
  void rejectsZeroMaxEntries() {
    assertThatThrownBy(() -> new ArchiveSafetyPolicy(0, 1024, 10.0d, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxEntries must be >= 1");
  }

  @Test
  void rejectsNegativeMaxEntries() {
    assertThatThrownBy(() -> new ArchiveSafetyPolicy(-1, 1024, 10.0d, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxEntries must be >= 1");
  }

  @Test
  void rejectsZeroMaxTotalUncompressedBytes() {
    assertThatThrownBy(() -> new ArchiveSafetyPolicy(1, 0, 10.0d, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxTotalUncompressedBytes must be >= 1");
  }

  @Test
  void rejectsCompressionRatioBelowOne() {
    assertThatThrownBy(() -> new ArchiveSafetyPolicy(1, 1024, 0.5d, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxCompressionRatio must be >= 1");
  }

  @Test
  void rejectsNegativeMinBytesForCompressionRatioCheck() {
    assertThatThrownBy(() -> new ArchiveSafetyPolicy(1, 1024, 10.0d, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("minBytesForCompressionRatioCheck must be >= 0");
  }
}
