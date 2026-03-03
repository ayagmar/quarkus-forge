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

  @Test
  void toApiValueHandlesUnknownBuildTool() {
    assertThat(BuildToolCodec.toApiValue("custom-tool")).isEqualTo("CUSTOM_TOOL");
  }

  @Test
  void toUiValueHandlesUnknownBuildTool() {
    assertThat(BuildToolCodec.toUiValue("CUSTOM_TOOL")).isEqualTo("custom-tool");
  }

  @Test
  void toApiValueHandlesNullValue() {
    assertThat(BuildToolCodec.toApiValue(null)).isEmpty();
  }

  @Test
  void toUiValueHandlesNullValue() {
    assertThat(BuildToolCodec.toUiValue(null)).isEmpty();
  }

  @Test
  void toApiValueTrimsWhitespace() {
    assertThat(BuildToolCodec.toApiValue("  maven  ")).isEqualTo("MAVEN");
  }

  @Test
  void toUiValueTrimsWhitespace() {
    assertThat(BuildToolCodec.toUiValue("  GRADLE  ")).isEqualTo("gradle");
  }

  @Test
  void toApiValueIsCaseInsensitive() {
    assertThat(BuildToolCodec.toApiValue("MAVEN")).isEqualTo("MAVEN");
    assertThat(BuildToolCodec.toApiValue("Maven")).isEqualTo("MAVEN");
    assertThat(BuildToolCodec.toApiValue("Gradle-Kotlin-DsL")).isEqualTo("GRADLE_KOTLIN_DSL");
  }

  @Test
  void toUiValueIsCaseInsensitive() {
    assertThat(BuildToolCodec.toUiValue("gradle_kotlin_dsl")).isEqualTo("gradle-kotlin-dsl");
    assertThat(BuildToolCodec.toUiValue("Gradle_Kotlin_Dsl")).isEqualTo("gradle-kotlin-dsl");
  }
}
