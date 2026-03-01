package dev.ayagmar.quarkusforge.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ayagmar.quarkusforge.api.AtomicFileStore;
import dev.ayagmar.quarkusforge.api.ForgeDataPaths;
import dev.ayagmar.quarkusforge.api.ObjectMapperProvider;
import dev.ayagmar.quarkusforge.domain.CliPrefill;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class UserPreferencesStore {
  private static final int SCHEMA_VERSION = 1;

  private final Path file;
  private final ObjectMapper objectMapper;

  private UserPreferencesStore(Path file, ObjectMapper objectMapper) {
    this.file = Objects.requireNonNull(file);
    this.objectMapper = Objects.requireNonNull(objectMapper);
  }

  public static UserPreferencesStore fileBacked(Path file) {
    return new UserPreferencesStore(file, ObjectMapperProvider.shared());
  }

  public static Path defaultFile() {
    return ForgeDataPaths.preferencesFile();
  }

  public CliPrefill loadLastRequest() {
    UserPreferencesPayload payload = loadPayload();
    if (payload == null) {
      return null;
    }
    return new CliPrefill(
        normalize(payload.groupId()),
        normalize(payload.artifactId()),
        normalize(payload.version()),
        normalize(payload.packageName()),
        normalize(payload.outputDirectory()),
        normalize(payload.platformStream()),
        normalize(payload.buildTool()),
        normalize(payload.javaVersion()));
  }

  public void saveLastRequest(ProjectRequest request) {
    Objects.requireNonNull(request);
    try {
      UserPreferencesPayload payload =
          new UserPreferencesPayload(
              SCHEMA_VERSION,
              request.groupId(),
              request.artifactId(),
              request.version(),
              request.packageName(),
              request.outputDirectory(),
              request.platformStream(),
              request.buildTool(),
              request.javaVersion());
      AtomicFileStore.writeBytes(
          file, objectMapper.writeValueAsBytes(payload), "forge-preferences-");
    } catch (IOException ignored) {
      // Best-effort persistence only.
    }
  }

  private UserPreferencesPayload loadPayload() {
    if (!Files.isRegularFile(file)) {
      return null;
    }
    try {
      UserPreferencesPayload payload =
          objectMapper.readValue(file.toFile(), UserPreferencesPayload.class);
      if (payload == null || payload.schemaVersion() != SCHEMA_VERSION) {
        return null;
      }
      return payload;
    } catch (IOException ignored) {
      return null;
    }
  }

  private static String normalize(String value) {
    return value == null ? "" : value;
  }
}
