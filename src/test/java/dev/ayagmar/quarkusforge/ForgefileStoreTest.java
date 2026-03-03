package dev.ayagmar.quarkusforge;

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
    Forgefile loaded = ForgefileStore.load(file);

    assertThat(loaded.groupId()).isEqualTo("com.acme");
    assertThat(loaded.artifactId()).isEqualTo("my-service");
    assertThat(loaded.version()).isEqualTo("2.0.0");
    assertThat(loaded.packageName()).isEqualTo("com.acme.svc");
    assertThat(loaded.outputDirectory()).isEqualTo("/projects");
    assertThat(loaded.platformStream()).isEqualTo("io.quarkus.platform:3.31");
    assertThat(loaded.buildTool()).isEqualTo("gradle");
    assertThat(loaded.javaVersion()).isEqualTo("21");
    assertThat(loaded.presets()).containsExactly("web", "data");
    assertThat(loaded.extensions())
        .containsExactly("io.quarkus:quarkus-rest", "io.quarkus:quarkus-arc");
    assertThat(loaded.locked()).isNull();
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
            "",
            ".",
            "",
            "maven",
            "25",
            List.of("web"),
            List.of("io.quarkus:quarkus-rest"),
            lock);

    Path file = tempDir.resolve("Forgefile-locked");
    ForgefileStore.save(file, original);
    Forgefile loaded = ForgefileStore.load(file);

    assertThat(loaded.locked()).isNotNull();
    assertThat(loaded.locked().platformStream()).isEqualTo("io.quarkus.platform:3.31");
    assertThat(loaded.locked().buildTool()).isEqualTo("maven");
    assertThat(loaded.locked().javaVersion()).isEqualTo("25");
    assertThat(loaded.locked().presets()).containsExactly("web");
    assertThat(loaded.locked().extensions()).containsExactly("io.quarkus:quarkus-rest");
  }

  @Test
  void roundTripWithoutLockedSection() {
    Forgefile original =
        new Forgefile(
            "org.test", "no-lock", "1.0.0", "", ".", "", "maven", "21", List.of(), List.of());

    Path file = tempDir.resolve("no-lock");
    ForgefileStore.save(file, original);
    Forgefile loaded = ForgefileStore.load(file);

    assertThat(loaded.locked()).isNull();
    assertThat(loaded.artifactId()).isEqualTo("no-lock");
  }

  @Test
  void withLockCreatesNewForgefileWithLockedSection() {
    Forgefile original =
        new Forgefile("org.test", "app", "1.0.0", "", ".", "", "maven", "21", List.of(), List.of());
    assertThat(original.locked()).isNull();

    ForgefileLock lock = ForgefileLock.of("stream:1", "maven", "21", List.of(), List.of());
    Forgefile withLock = original.withLock(lock);

    assertThat(withLock.locked()).isNotNull();
    assertThat(withLock.locked().platformStream()).isEqualTo("stream:1");
    assertThat(withLock.artifactId()).isEqualTo("app");
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
  void loadUnreadablePathThrowsReadError() throws Exception {
    Path directory = tempDir.resolve("as-directory");
    Files.createDirectory(directory);

    assertThatThrownBy(() -> ForgefileStore.load(directory))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Failed to read Forgefile '")
        .hasMessageContaining("as-directory");
  }

  @Test
  void saveCreatesParentDirectories() {
    Path nested = tempDir.resolve("a/b/c/Forgefile");
    Forgefile forgefile =
        new Forgefile(
            "org.test", "nested", "1.0.0", "", ".", "", "maven", "21", List.of(), List.of());

    ForgefileStore.save(nested, forgefile);

    assertThat(nested).exists();
    Forgefile loaded = ForgefileStore.load(nested);
    assertThat(loaded.artifactId()).isEqualTo("nested");
  }

  @Test
  void emptyFieldsNormalizeToBlank() {
    Path file = tempDir.resolve("empty-fields");
    Forgefile forgefile =
        new Forgefile(null, "app", null, null, null, null, null, null, null, null);

    ForgefileStore.save(file, forgefile);
    Forgefile loaded = ForgefileStore.load(file);

    assertThat(loaded.groupId()).isEmpty();
    assertThat(loaded.version()).isEmpty();
    assertThat(loaded.presets()).isEmpty();
    assertThat(loaded.extensions()).isEmpty();
  }

  @Test
  void toRequestOptionsAppliesDefaults() {
    Forgefile forgefile = new Forgefile("", "", "", "", "", "", "", "", List.of(), List.of());

    RequestOptions options = forgefile.toRequestOptions();

    assertThat(options.groupId).isEqualTo("org.acme");
    assertThat(options.artifactId).isEqualTo("quarkus-app");
    assertThat(options.version).isEqualTo("1.0.0-SNAPSHOT");
    assertThat(options.buildTool).isEqualTo("maven");
    assertThat(options.javaVersion).isEqualTo("25");
    assertThat(options.outputDirectory).isEqualTo(".");
  }

  @Test
  void fromRequestOptionsCreatesForgefileWithoutLock() {
    RequestOptions options = new RequestOptions();
    options.groupId = "com.test";
    options.artifactId = "my-app";
    options.buildTool = "gradle";

    Forgefile forgefile = Forgefile.from(options, List.of("web"), List.of("ext1"));

    assertThat(forgefile.groupId()).isEqualTo("com.test");
    assertThat(forgefile.artifactId()).isEqualTo("my-app");
    assertThat(forgefile.buildTool()).isEqualTo("gradle");
    assertThat(forgefile.presets()).containsExactly("web");
    assertThat(forgefile.extensions()).containsExactly("ext1");
    assertThat(forgefile.locked()).isNull();
  }

  @Test
  void loadIgnoresNonMapLockedSection() throws Exception {
    // Write JSON where "locked" is a string instead of a map
    Path file = tempDir.resolve("locked-string.json");
    Files.writeString(
        file,
        """
        {
          "groupId": "org.acme",
          "artifactId": "test",
          "version": "1.0.0",
          "buildTool": "maven",
          "javaVersion": "21",
          "presets": [],
          "extensions": [],
          "locked": "not-a-map"
        }
        """);

    Forgefile loaded = ForgefileStore.load(file);

    assertThat(loaded.locked()).isNull();
    assertThat(loaded.artifactId()).isEqualTo("test");
  }

  @Test
  void saveOmitsBlankPackageNameOutputDirectoryAndPlatformStream() throws Exception {
    Forgefile forgefile =
        new Forgefile("org.acme", "app", "1.0.0", "", "", "", "maven", "21", List.of(), List.of());

    Path file = tempDir.resolve("omit-blanks");
    ForgefileStore.save(file, forgefile);
    String raw = Files.readString(file);
    Forgefile loaded = ForgefileStore.load(file);

    assertThat(raw).doesNotContain("\"packageName\"");
    assertThat(raw).doesNotContain("\"outputDirectory\"");
    assertThat(raw).doesNotContain("\"platformStream\"");
    // Blank packageName and outputDirectory should be omitted in JSON, loaded as empty
    assertThat(loaded.packageName()).isEmpty();
    assertThat(loaded.outputDirectory()).isEmpty();
    assertThat(loaded.platformStream()).isEmpty();
  }

  @Test
  void saveIncludesNonBlankOptionalFields() {
    Forgefile forgefile =
        new Forgefile(
            "org.acme",
            "app",
            "1.0.0",
            "org.acme.app",
            "/projects",
            "io.quarkus.platform:3.31",
            "maven",
            "21",
            List.of(),
            List.of());

    Path file = tempDir.resolve("include-optionals");
    ForgefileStore.save(file, forgefile);
    Forgefile loaded = ForgefileStore.load(file);

    assertThat(loaded.packageName()).isEqualTo("org.acme.app");
    assertThat(loaded.outputDirectory()).isEqualTo("/projects");
    assertThat(loaded.platformStream()).isEqualTo("io.quarkus.platform:3.31");
  }

  @Test
  void loadFileWithNullLockedFieldReturnsNullLock() throws Exception {
    Path file = tempDir.resolve("null-locked.json");
    Files.writeString(
        file,
        """
        {
          "groupId": "org.acme",
          "artifactId": "test",
          "version": "1.0",
          "buildTool": "maven",
          "javaVersion": "21",
          "presets": [],
          "extensions": [],
          "locked": null
        }
        """);

    Forgefile loaded = ForgefileStore.load(file);

    assertThat(loaded.locked()).isNull();
  }
}
