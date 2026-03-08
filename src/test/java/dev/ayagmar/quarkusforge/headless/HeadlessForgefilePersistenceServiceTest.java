package dev.ayagmar.quarkusforge.headless;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.ayagmar.quarkusforge.api.JsonSupport;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.forge.Forgefile;
import dev.ayagmar.quarkusforge.forge.ForgefileLock;
import dev.ayagmar.quarkusforge.forge.ForgefileStore;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HeadlessForgefilePersistenceServiceTest {
  @TempDir Path tempDir;

  private final HeadlessForgefilePersistenceService service =
      new HeadlessForgefilePersistenceService();

  @Test
  void validateLockDriftIgnoresEquivalentPresetAndExtensionOrdering() {
    Forgefile forgefile =
        new Forgefile(
            "com.team",
            "ordered-app",
            null,
            null,
            null,
            null,
            "maven",
            "21",
            List.of("favorites", "web"),
            List.of("io.quarkus:quarkus-arc", "io.quarkus:quarkus-rest"),
            new ForgefileLock(
                "io.quarkus.platform:3.31",
                "maven",
                "21",
                List.of("web", "favorites"),
                List.of("io.quarkus:quarkus-rest", "io.quarkus:quarkus-arc")));
    HeadlessGenerationInputs inputs =
        new HeadlessGenerationInputs(
            forgefile,
            List.of("favorites", "web"),
            List.of("io.quarkus:quarkus-arc", "io.quarkus:quarkus-rest"),
            forgefile,
            null,
            false,
            true,
            null);

    assertThatCode(
            () ->
                service.validateLockDrift(
                    inputs,
                    new ProjectRequest(
                        "com.team",
                        "ordered-app",
                        "1.0.0-SNAPSHOT",
                        "com.team.ordered",
                        ".",
                        "io.quarkus.platform:3.31",
                        "maven",
                        "21"),
                    List.of("io.quarkus:quarkus-rest", "io.quarkus:quarkus-arc")))
        .doesNotThrowAnyException();
  }

  @Test
  void persistSaveAsPreservesOmittedFieldsAndExistingLock() throws Exception {
    Path saveAsPath = tempDir.resolve("copy.forgefile.json");
    Forgefile template =
        new Forgefile(
            null,
            "team-svc",
            null,
            null,
            null,
            null,
            null,
            "21",
            List.of(),
            List.of("io.quarkus:quarkus-rest"),
            new ForgefileLock(
                "io.quarkus.platform:3.31",
                "maven",
                "21",
                List.of(),
                List.of("io.quarkus:quarkus-rest")));

    service.persist(
        new HeadlessGenerationInputs(
            template,
            List.of(),
            List.of("io.quarkus:quarkus-rest"),
            template,
            null,
            false,
            false,
            saveAsPath),
        new ProjectRequest(
            "com.example",
            "team-svc",
            "1.0.0-SNAPSHOT",
            "com.example.team",
            ".",
            "io.quarkus.platform:3.31",
            "maven",
            "21"),
        List.of("io.quarkus:quarkus-rest"));

    Forgefile saved = ForgefileStore.load(saveAsPath);
    Map<String, Object> root = JsonSupport.parseObject(java.nio.file.Files.readString(saveAsPath));
    assertThat(saved.groupId()).isNull();
    assertThat(saved.version()).isNull();
    assertThat(saved.outputDirectory()).isNull();
    assertThat(saved.buildTool()).isNull();
    assertThat(saved.locked()).isNotNull();
    assertThat(saved.locked().buildTool()).isEqualTo("maven");
    assertThat(root).doesNotContainKeys("groupId", "version", "outputDirectory", "buildTool");
  }

  @Test
  void persistUpdatesLockAtSourcePathWhenWriteLockEnabled() throws Exception {
    Path sourcePath = tempDir.resolve("locked-source.forgefile.json");
    Forgefile template =
        new Forgefile(
            null, "team-svc", null, null, null, null, null, "21", List.of(), List.of(), null);

    service.persist(
        new HeadlessGenerationInputs(
            template,
            List.of("web"),
            List.of("io.quarkus:quarkus-rest"),
            template,
            sourcePath,
            true,
            false,
            null),
        new ProjectRequest(
            "com.example",
            "team-svc",
            "1.0.0-SNAPSHOT",
            "com.example.team",
            ".",
            "io.quarkus.platform:3.31",
            "maven",
            "21"),
        List.of("io.quarkus:quarkus-rest"));

    Forgefile saved = ForgefileStore.load(sourcePath);
    assertThat(saved.locked()).isNotNull();
    assertThat(saved.locked().platformStream()).isEqualTo("io.quarkus.platform:3.31");
    assertThat(saved.locked().buildTool()).isEqualTo("maven");
    assertThat(saved.locked().javaVersion()).isEqualTo("21");
    assertThat(saved.locked().presets()).containsExactly("web");
    assertThat(saved.locked().extensions()).containsExactly("io.quarkus:quarkus-rest");
  }
}
