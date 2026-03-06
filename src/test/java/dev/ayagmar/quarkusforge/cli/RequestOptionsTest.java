package dev.ayagmar.quarkusforge.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RequestOptions}, focusing on:
 *
 * <ul>
 *   <li>Default values are correct and consistent with {@link RequestOptions#defaults()}
 *   <li>{@link RequestOptions#isExplicitlySet} fallback (no Picocli spec injected)
 * </ul>
 */
class RequestOptionsTest {

  // ── Default values ──────────────────────────────────────────────────────────

  @Test
  void defaultsFactoryPopulatesAllKnownDefaults() {
    RequestOptions opts = RequestOptions.defaults();

    assertThat(opts.groupId).isEqualTo(RequestOptions.DEFAULT_GROUP_ID);
    assertThat(opts.artifactId).isEqualTo(RequestOptions.DEFAULT_ARTIFACT_ID);
    assertThat(opts.version).isEqualTo(RequestOptions.DEFAULT_VERSION);
    assertThat(opts.packageName).isNull();
    assertThat(opts.outputDirectory).isEqualTo(RequestOptions.DEFAULT_OUTPUT_DIRECTORY);
    assertThat(opts.platformStream).isEqualTo(RequestOptions.DEFAULT_PLATFORM_STREAM);
    assertThat(opts.buildTool).isEqualTo(RequestOptions.DEFAULT_BUILD_TOOL);
    assertThat(opts.javaVersion).isEqualTo(RequestOptions.DEFAULT_JAVA_VERSION);
  }

  // ── isExplicitlySet fallback (no CommandSpec injected) ───────────────────────

  @Test
  void isExplicitlySetReturnsFalseWhenValueEqualsDefault() {
    RequestOptions opts = new RequestOptions(); // spec == null
    // current value equals the default → not explicitly set
    assertThat(opts.isExplicitlySet(RequestOptions.OPT_GROUP_ID, "org.acme", "org.acme")).isFalse();
  }

  @Test
  void isExplicitlySetReturnsTrueWhenValueDiffersFromDefault() {
    RequestOptions opts = new RequestOptions(); // spec == null
    // current value differs from the default → explicitly set
    assertThat(opts.isExplicitlySet(RequestOptions.OPT_GROUP_ID, "com.example", "org.acme"))
        .isTrue();
  }

  @Test
  void isExplicitlySetHandlesNullCurrentValue() {
    RequestOptions opts = new RequestOptions(); // spec == null
    // null current value (e.g. --package-name not provided), default also null
    assertThat(opts.isExplicitlySet(RequestOptions.OPT_PACKAGE_NAME, null, null)).isFalse();
  }

  @Test
  void isExplicitlySetReturnsTrueWhenCurrentNullButDefaultIsPresent() {
    RequestOptions opts = new RequestOptions();
    // current is null but default is non-null → treated as explicitly nulled out
    assertThat(opts.isExplicitlySet(RequestOptions.OPT_GROUP_ID, null, "org.acme")).isTrue();
  }

  @Test
  void isExplicitlySetReturnsTrueWhenDefaultNullButCurrentIsPresent() {
    RequestOptions opts = new RequestOptions();
    assertThat(opts.isExplicitlySet(RequestOptions.OPT_PACKAGE_NAME, "com.example", null)).isTrue();
  }

  @Test
  void isExplicitlySetReturnsFalseForEmptyPlatformStreamDefault() {
    RequestOptions opts = new RequestOptions();
    assertThat(opts.isExplicitlySet(RequestOptions.OPT_PLATFORM_STREAM, "", "")).isFalse();
  }
}
