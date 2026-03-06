package dev.ayagmar.quarkusforge.forge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ForgefileTest {

  @Test
  void fullConstructorNormalizesDocumentFields() {
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
            List.of("web"),
            List.of("rest"),
            null);

    assertThat(forgefile.groupId()).isEqualTo("org.acme");
    assertThat(forgefile.artifactId()).isEqualTo("demo");
    assertThat(forgefile.version()).isEqualTo("1.0");
    assertThat(forgefile.buildTool()).isEqualTo("maven");
    assertThat(forgefile.locked()).isNull();
  }

  @Test
  void nullFieldsRemainOmitted() {
    Forgefile forgefile =
        new Forgefile(null, null, null, null, null, null, null, null, null, null, null);

    assertThat(forgefile.groupId()).isNull();
    assertThat(forgefile.artifactId()).isNull();
    assertThat(forgefile.version()).isNull();
    assertThat(forgefile.packageName()).isNull();
    assertThat(forgefile.outputDirectory()).isNull();
    assertThat(forgefile.platformStream()).isNull();
    assertThat(forgefile.buildTool()).isNull();
    assertThat(forgefile.javaVersion()).isNull();
    assertThat(forgefile.presets()).isNull();
    assertThat(forgefile.extensions()).isNull();
  }

  @Test
  void withLockReturnsNewForgefileWithLock() {
    Forgefile original =
        new Forgefile("org.acme", "demo", "1.0", null, ".", null, "maven", "25", null, null);

    ForgefileLock lock = new ForgefileLock("3.31", "maven", "25", List.of("rest"), List.of());
    Forgefile locked = original.withLock(lock);

    assertThat(locked.locked()).isSameAs(lock);
    assertThat(locked.groupId()).isEqualTo(original.groupId());
    assertThat(locked.artifactId()).isEqualTo(original.artifactId());
  }

  @Test
  void withSelectionsPreservesTopLevelFieldsAndLock() {
    ForgefileLock lock =
        ForgefileLock.of("io.quarkus.platform:3.31", "maven", "25", List.of(), List.of("rest"));
    Forgefile forgefile =
        new Forgefile(null, "demo", null, null, null, null, null, "25", null, List.of("old"), lock);

    Forgefile updated = forgefile.withSelections(List.of("web"), List.of("rest", "arc"));

    assertThat(updated.groupId()).isNull();
    assertThat(updated.artifactId()).isEqualTo("demo");
    assertThat(updated.javaVersion()).isEqualTo("25");
    assertThat(updated.presets()).containsExactly("web");
    assertThat(updated.extensions()).containsExactly("rest", "arc");
    assertThat(updated.locked()).isEqualTo(lock);
  }

  @Test
  void withOverridesReplacesOnlyProvidedDocumentFields() {
    Forgefile base =
        new Forgefile(
            null,
            "demo",
            null,
            null,
            null,
            "io.quarkus.platform:3.31",
            null,
            "25",
            List.of("web"),
            List.of("rest"),
            null);
    Forgefile overrides =
        new Forgefile(
            "com.example",
            null,
            "2.0.0",
            "com.example.demo",
            null,
            null,
            "gradle",
            null,
            null,
            null,
            null);

    Forgefile merged = base.withOverrides(overrides);

    assertThat(merged.groupId()).isEqualTo("com.example");
    assertThat(merged.artifactId()).isEqualTo("demo");
    assertThat(merged.version()).isEqualTo("2.0.0");
    assertThat(merged.packageName()).isEqualTo("com.example.demo");
    assertThat(merged.platformStream()).isEqualTo("io.quarkus.platform:3.31");
    assertThat(merged.buildTool()).isEqualTo("gradle");
    assertThat(merged.javaVersion()).isEqualTo("25");
    assertThat(merged.presets()).containsExactly("web");
    assertThat(merged.extensions()).containsExactly("rest");
  }
}
