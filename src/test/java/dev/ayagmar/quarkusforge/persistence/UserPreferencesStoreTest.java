package dev.ayagmar.quarkusforge.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.testsupport.EncodingProbeSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UserPreferencesStoreTest {
  @TempDir Path tempDir;

  @Test
  void fileBackedStorePersistsLastRequestAcrossInstances() {
    Path preferencesFile = tempDir.resolve("preferences.json");
    UserPreferencesStore writerStore = UserPreferencesStore.fileBacked(preferencesFile);

    writerStore.saveLastRequest(
        new ProjectRequest(
            "org.demo",
            "demo-app",
            "1.2.3",
            "org.demo.app",
            "/tmp/out",
            "io.quarkus.platform:3.31",
            "gradle",
            "25"));

    UserPreferencesStore readerStore = UserPreferencesStore.fileBacked(preferencesFile);
    assertThat(readerStore.loadLastRequest()).isNotNull();
    assertThat(readerStore.loadLastRequest().groupId()).isEqualTo("org.demo");
    assertThat(readerStore.loadLastRequest().artifactId()).isEqualTo("demo-app");
    assertThat(readerStore.loadLastRequest().buildTool()).isEqualTo("gradle");
  }

  @Test
  void invalidSchemaReturnsNullPreferences() throws Exception {
    Path preferencesFile = tempDir.resolve("preferences.json");
    Files.writeString(
        preferencesFile,
        """
        {
          "schemaVersion": 999,
          "groupId": "org.bad"
        }
        """);

    UserPreferencesStore store = UserPreferencesStore.fileBacked(preferencesFile);
    assertThat(store.loadLastRequest()).isNull();
  }

  @Test
  void loadReturnsNullForNonexistentFile() {
    UserPreferencesStore store =
        UserPreferencesStore.fileBacked(tempDir.resolve("nonexistent.json"));

    assertThat(store.loadLastRequest()).isNull();
  }

  @Test
  void loadReturnsNullForCorruptJson() throws Exception {
    Path file = tempDir.resolve("corrupt.json");
    Files.writeString(file, "not valid json {{{");

    UserPreferencesStore store = UserPreferencesStore.fileBacked(file);

    assertThat(store.loadLastRequest()).isNull();
  }

  @Test
  void loadReturnsNullForMissingSchemaVersion() throws Exception {
    Path file = tempDir.resolve("noschema.json");
    Files.writeString(
        file,
        """
        {"groupId": "org.acme"}
        """);

    UserPreferencesStore store = UserPreferencesStore.fileBacked(file);

    assertThat(store.loadLastRequest()).isNull();
  }

  @Test
  void saveIgnoresIoErrorGracefully() throws Exception {
    Path directoryPath = Files.createDirectory(tempDir.resolve("prefs-dir"));
    UserPreferencesStore store = UserPreferencesStore.fileBacked(directoryPath);

    assertThatCode(
            () ->
                store.saveLastRequest(
                    new ProjectRequest(
                        "org.acme", "demo", "1.0", "org.acme", ".", "", "maven", "25")))
        .doesNotThrowAnyException();
  }

  @Test
  void defaultFileReturnsNonNullPath() {
    assertThat(UserPreferencesStore.defaultFile()).isNotNull();
  }

  @Test
  void loadReturnsEmptyStringsForNullFieldValues() throws Exception {
    Path file = tempDir.resolve("partial.json");
    Files.writeString(
        file,
        """
        {"schemaVersion": 1}
        """);

    UserPreferencesStore store = UserPreferencesStore.fileBacked(file);
    var result = store.loadLastRequest();

    assertThat(result).isNotNull();
    assertThat(result.groupId()).isEmpty();
    assertThat(result.artifactId()).isEmpty();
  }

  @Test
  void fileBackedStoreReadsUtf8PayloadsWhenJvmDefaultCharsetIsAscii() throws Exception {
    Path preferencesFile = tempDir.resolve("utf8-preferences.json");
    UserPreferencesStore store = UserPreferencesStore.fileBacked(preferencesFile);

    store.saveLastRequest(
        new ProjectRequest(
            "org.demo",
            "demo-app",
            "1.2.3",
            "org.demo.app",
            "./caf\u00e9",
            "io.quarkus.platform:3.31",
            "gradle",
            "25"));

    assertThat(EncodingProbeSupport.probe("preferences-output-dir", preferencesFile))
        .isEqualTo("./caf\\u00E9");
  }
}
