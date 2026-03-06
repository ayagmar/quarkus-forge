package dev.ayagmar.quarkusforge.forge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ForgefileStoreTest {
  @TempDir Path tempDir;

  @Test
  void roundTripPreservesAllFields() {
    Forgefile original =
        new Forgefile(
            "com.acme",
            "my-service",
            "2.0.0",
            "com.acme.svc",
            "/projects",
            "io.quarkus.platform:3.31",
            "gradle",
            "21",
            List.of("web", "data"),
            List.of("io.quarkus:quarkus-rest", "io.quarkus:quarkus-arc"));

    Path file = tempDir.resolve("Forgefile");
    ForgefileStore.save(file, original);

    assertThat(ForgefileStore.load(file)).isEqualTo(original);
  }

  @Test
  void roundTripPreservesLockedSection() {
    ForgefileLock lock =
        ForgefileLock.of(
            "io.quarkus.platform:3.31",
            "maven",
            "25",
            List.of("web"),
            List.of("io.quarkus:quarkus-rest"));
    Forgefile original =
        new Forgefile(
            "com.acme",
            "locked-app",
            "1.0.0",
            null,
            null,
            null,
            "maven",
            "25",
            List.of("web"),
            List.of("io.quarkus:quarkus-rest"),
            lock);

    Path file = tempDir.resolve("Forgefile-locked");
    ForgefileStore.save(file, original);

    assertThat(ForgefileStore.load(file).locked()).isEqualTo(lock);
  }

  @Test
  void saveCreatesParentDirectories() {
    Path nested = tempDir.resolve("a/b/c/Forgefile");
    Forgefile forgefile =
        new Forgefile("org.test", "nested", "1.0.0", null, null, null, "maven", "21", null, null);

    ForgefileStore.save(nested, forgefile);

    assertThat(nested).exists();
    assertThat(ForgefileStore.load(nested).artifactId()).isEqualTo("nested");
  }

  @Test
  void omittedTopLevelFieldsStayOmittedAcrossRoundTrip() throws Exception {
    Path file = tempDir.resolve("omit-top-level.json");
    Forgefile forgefile =
        new Forgefile(null, "app", null, null, null, null, null, null, null, null);

    ForgefileStore.save(file, forgefile);
    String raw = Files.readString(file);
    Forgefile loaded = ForgefileStore.load(file);

    assertThat(raw).contains("\"artifactId\"");
    assertThat(raw).doesNotContain("\"groupId\"");
    assertThat(raw).doesNotContain("\"version\"");
    assertThat(raw).doesNotContain("\"buildTool\"");
    assertThat(raw).doesNotContain("\"javaVersion\"");
    assertThat(raw).doesNotContain("\"presets\"");
    assertThat(raw).doesNotContain("\"extensions\"");
    assertThat(loaded.groupId()).isNull();
    assertThat(loaded.version()).isNull();
    assertThat(loaded.buildTool()).isNull();
    assertThat(loaded.javaVersion()).isNull();
    assertThat(loaded.presets()).isNull();
    assertThat(loaded.extensions()).isNull();
  }

  @Test
  void saveOmitsBlankTopLevelStringFields() throws Exception {
    Path file = tempDir.resolve("blank-top-level.json");
    Forgefile forgefile = new Forgefile(" ", "app", "", "   ", " ", "", "", "", null, null);

    ForgefileStore.save(file, forgefile);
    String raw = Files.readString(file);

    assertThat(raw).contains("\"artifactId\"");
    assertThat(raw).doesNotContain("\"groupId\"");
    assertThat(raw).doesNotContain("\"version\"");
    assertThat(raw).doesNotContain("\"packageName\"");
    assertThat(raw).doesNotContain("\"outputDirectory\"");
    assertThat(raw).doesNotContain("\"platformStream\"");
    assertThat(raw).doesNotContain("\"buildTool\"");
    assertThat(raw).doesNotContain("\"javaVersion\"");
  }

  @Test
  void loadPreservesMissingFieldsAsNull() throws Exception {
    Path file = tempDir.resolve("missing-fields.json");
    Files.writeString(
        file,
        """
        {
          "artifactId": "team-svc",
          "extensions": ["io.quarkus:quarkus-rest"]
        }
        """);

    Forgefile loaded = ForgefileStore.load(file);

    assertThat(loaded.groupId()).isNull();
    assertThat(loaded.artifactId()).isEqualTo("team-svc");
    assertThat(loaded.extensions()).containsExactly("io.quarkus:quarkus-rest");
    assertThat(loaded.presets()).isNull();
    assertThat(loaded.buildTool()).isNull();
  }

  @Test
  void loadMissingFileThrows() {
    Path missing = tempDir.resolve("nonexistent");

    assertThatThrownBy(() -> ForgefileStore.load(missing))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Forgefile not found: '")
        .hasMessageContaining("nonexistent");
  }

  @Test
  void loadMalformedJsonThrows() throws Exception {
    Path file = tempDir.resolve("bad.json");
    Files.writeString(file, "not json");

    assertThatThrownBy(() -> ForgefileStore.load(file))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Failed to parse Forgefile '")
        .hasMessageContaining("bad.json");
  }

  @Test
  void saveDirectoryTargetThrowsWriteError() throws Exception {
    Path directoryTarget = tempDir.resolve("existing-directory");
    Files.createDirectory(directoryTarget);
    Forgefile forgefile =
        new Forgefile("com.acme", "demo", "1.0.0", null, null, null, "maven", "25", null, null);

    assertThatThrownBy(() -> ForgefileStore.save(directoryTarget, forgefile))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Failed to write Forgefile '")
        .hasMessageContaining("existing-directory");
  }

  @Test
  void loadUnreadablePathThrowsReadError() throws Exception {
    Path directory = tempDir.resolve("as-directory");
    Files.createDirectory(directory);

    assertThatThrownBy(() -> ForgefileStore.load(directory))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Failed to read Forgefile '")
        .hasMessageContaining("as-directory");
  }

  @Test
  void loadIgnoresNonMapLockedSection() throws Exception {
    Path file = tempDir.resolve("locked-string.json");
    Files.writeString(
        file,
        """
        {
          "artifactId": "test",
          "locked": "not-a-map"
        }
        """);

    Forgefile loaded = ForgefileStore.load(file);

    assertThat(loaded.locked()).isNull();
    assertThat(loaded.artifactId()).isEqualTo("test");
  }

  @Test
  void loadFileWithNullLockedFieldReturnsNullLock() throws Exception {
    Path file = tempDir.resolve("null-locked.json");
    Files.writeString(
        file,
        """
        {
          "artifactId": "test",
          "locked": null
        }
        """);

    assertThat(ForgefileStore.load(file).locked()).isNull();
  }
}
