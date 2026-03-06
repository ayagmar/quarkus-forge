package dev.ayagmar.quarkusforge.forge;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.domain.CliPrefill;
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
  void toCliPrefillUsesDefaultsForBlanks() {
    Forgefile forgefile = new Forgefile("", "", "", "", "", "", "", "", List.of(), List.of());

    CliPrefill prefill = forgefile.toCliPrefill();

    assertThat(prefill.groupId()).isEqualTo("org.acme");
    assertThat(prefill.artifactId()).isEqualTo("quarkus-app");
    assertThat(prefill.version()).isEqualTo("1.0.0-SNAPSHOT");
    assertThat(prefill.outputDirectory()).isEqualTo(".");
    assertThat(prefill.buildTool()).isEqualTo("maven");
    assertThat(prefill.javaVersion()).isEqualTo("25");
  }

  @Test
  void toCliPrefillPreservesNonBlankValues() {
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

    CliPrefill prefill = forgefile.toCliPrefill();

    assertThat(prefill.groupId()).isEqualTo("com.example");
    assertThat(prefill.artifactId()).isEqualTo("my-app");
    assertThat(prefill.version()).isEqualTo("2.0.0");
    assertThat(prefill.packageName()).isEqualTo("com.example.app");
    assertThat(prefill.buildTool()).isEqualTo("gradle");
  }

  @Test
  void toCliPrefillSetsPackageNameNullForBlank() {
    Forgefile forgefile =
        new Forgefile("org.acme", "demo", "1.0", "", ".", "", "maven", "25", List.of(), List.of());

    CliPrefill prefill = forgefile.toCliPrefill();

    assertThat(prefill.packageName()).isNull();
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
  void fromCliPrefillCreatesForgefileWithoutLock() {
    CliPrefill prefill =
        new CliPrefill("org.acme", "demo", "1.0", "org.acme.demo", ".", "3.31", "maven", "25");

    Forgefile forgefile = Forgefile.from(prefill, List.of("web"), List.of("rest", "arc"));

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
