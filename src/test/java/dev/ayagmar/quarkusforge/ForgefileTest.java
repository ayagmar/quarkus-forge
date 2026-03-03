package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ForgefileTest {

  @Test
  void fullConstructorNormalizesFields() {
    Forgefile forgefile =
        new Forgefile(
            "  org.acme  ",
            "  demo  ",
            "  1.0  ",
            "  org.acme  ",
            "  .  ",
            "  3.31  ",
            "  maven  ",
            "  25  ",
            List.of("  web  "),
            List.of("  rest  "),
            null);

    assertThat(forgefile.groupId()).isEqualTo("org.acme");
    assertThat(forgefile.artifactId()).isEqualTo("demo");
    assertThat(forgefile.version()).isEqualTo("1.0");
    assertThat(forgefile.buildTool()).isEqualTo("maven");
    assertThat(forgefile.locked()).isNull();
  }

  @Test
  void shortConstructorHasNoLockedSection() {
    Forgefile forgefile =
        new Forgefile(
            "org.acme",
            "demo",
            "1.0",
            "org.acme",
            ".",
            "3.31",
            "maven",
            "25",
            List.of(),
            List.of());

    assertThat(forgefile.locked()).isNull();
  }

  @Test
  void nullFieldsNormalizeToEmpty() {
    Forgefile forgefile =
        new Forgefile(null, null, null, null, null, null, null, null, null, null, null);

    assertThat(forgefile.groupId()).isEmpty();
    assertThat(forgefile.artifactId()).isEmpty();
    assertThat(forgefile.version()).isEmpty();
    assertThat(forgefile.packageName()).isEmpty();
    assertThat(forgefile.outputDirectory()).isEmpty();
    assertThat(forgefile.platformStream()).isEmpty();
    assertThat(forgefile.buildTool()).isEmpty();
    assertThat(forgefile.javaVersion()).isEmpty();
    assertThat(forgefile.presets()).isEmpty();
    assertThat(forgefile.extensions()).isEmpty();
  }

  @Test
  void toRequestOptionsUsesDefaultsForBlanks() {
    Forgefile forgefile = new Forgefile("", "", "", "", "", "", "", "", List.of(), List.of());

    RequestOptions options = forgefile.toRequestOptions();

    assertThat(options.groupId).isEqualTo(RequestOptions.DEFAULT_GROUP_ID);
    assertThat(options.artifactId).isEqualTo(RequestOptions.DEFAULT_ARTIFACT_ID);
    assertThat(options.version).isEqualTo(RequestOptions.DEFAULT_VERSION);
    assertThat(options.outputDirectory).isEqualTo(RequestOptions.DEFAULT_OUTPUT_DIRECTORY);
    assertThat(options.buildTool).isEqualTo(RequestOptions.DEFAULT_BUILD_TOOL);
    assertThat(options.javaVersion).isEqualTo(RequestOptions.DEFAULT_JAVA_VERSION);
  }

  @Test
  void toRequestOptionsPreservesNonBlankValues() {
    Forgefile forgefile =
        new Forgefile(
            "com.example",
            "my-app",
            "2.0.0",
            "com.example.app",
            "./out",
            "3.31",
            "gradle",
            "21",
            List.of(),
            List.of());

    RequestOptions options = forgefile.toRequestOptions();

    assertThat(options.groupId).isEqualTo("com.example");
    assertThat(options.artifactId).isEqualTo("my-app");
    assertThat(options.version).isEqualTo("2.0.0");
    assertThat(options.packageName).isEqualTo("com.example.app");
    assertThat(options.buildTool).isEqualTo("gradle");
  }

  @Test
  void toRequestOptionsSetsPackageNameNullForBlank() {
    Forgefile forgefile =
        new Forgefile("org.acme", "demo", "1.0", "", ".", "", "maven", "25", List.of(), List.of());

    RequestOptions options = forgefile.toRequestOptions();

    assertThat(options.packageName).isNull();
  }

  @Test
  void withLockReturnsNewForgefileWithLock() {
    Forgefile original =
        new Forgefile("org.acme", "demo", "1.0", "", ".", "", "maven", "25", List.of(), List.of());

    ForgefileLock lock = new ForgefileLock("3.31", "maven", "25", List.of("rest"), List.of());
    Forgefile locked = original.withLock(lock);

    assertThat(locked.locked()).isSameAs(lock);
    assertThat(locked.groupId()).isEqualTo(original.groupId());
    assertThat(locked.artifactId()).isEqualTo(original.artifactId());
  }

  @Test
  void fromRequestOptionsCreatesForgefileWithoutLock() {
    RequestOptions options = new RequestOptions();
    options.groupId = "org.acme";
    options.artifactId = "demo";
    options.version = "1.0";
    options.packageName = "org.acme.demo";
    options.outputDirectory = ".";
    options.platformStream = "3.31";
    options.buildTool = "maven";
    options.javaVersion = "25";

    Forgefile forgefile = Forgefile.from(options, List.of("web"), List.of("rest", "arc"));

    assertThat(forgefile.locked()).isNull();
    assertThat(forgefile.groupId()).isEqualTo("org.acme");
    assertThat(forgefile.presets()).containsExactly("web");
    assertThat(forgefile.extensions()).containsExactly("rest", "arc");
  }

  @Test
  void nullListsDefaultToEmpty() {
    Forgefile forgefile =
        new Forgefile("org.acme", "demo", "1.0", "", ".", "", "maven", "25", null, null, null);

    assertThat(forgefile.presets()).isEmpty();
    assertThat(forgefile.extensions()).isEmpty();
  }
}
