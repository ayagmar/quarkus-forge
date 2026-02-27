package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.domain.ProjectRequest;
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
}
