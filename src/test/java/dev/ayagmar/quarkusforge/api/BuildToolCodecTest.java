package dev.ayagmar.quarkusforge.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BuildToolCodecTest {
  @Test
  void toApiValueNormalizesKnownBuildTools() {
    assertThat(BuildToolCodec.toApiValue("maven")).isEqualTo("MAVEN");
    assertThat(BuildToolCodec.toApiValue("GRADLE")).isEqualTo("GRADLE");
    assertThat(BuildToolCodec.toApiValue("gradle-kotlin-dsl")).isEqualTo("GRADLE_KOTLIN_DSL");
  }

  @Test
  void toUiValueNormalizesKnownApiEnums() {
    assertThat(BuildToolCodec.toUiValue("MAVEN")).isEqualTo("maven");
    assertThat(BuildToolCodec.toUiValue("GRADLE")).isEqualTo("gradle");
    assertThat(BuildToolCodec.toUiValue("GRADLE_KOTLIN_DSL")).isEqualTo("gradle-kotlin-dsl");
  }
}
