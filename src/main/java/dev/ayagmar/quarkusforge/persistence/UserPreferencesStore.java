package dev.ayagmar.quarkusforge.persistence;

import dev.ayagmar.quarkusforge.api.AtomicFileStore;
import dev.ayagmar.quarkusforge.api.ForgeDataPaths;
import dev.ayagmar.quarkusforge.api.JsonFieldReader;
import dev.ayagmar.quarkusforge.api.JsonSupport;
import dev.ayagmar.quarkusforge.domain.CliPrefill;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class UserPreferencesStore {
  private static final int SCHEMA_VERSION = 1;

  private final Path file;

  private UserPreferencesStore(Path file) {
    this.file = Objects.requireNonNull(file);
  }

  public static UserPreferencesStore fileBacked(Path file) {
    return new UserPreferencesStore(file);
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
          file, JsonSupport.writeBytes(toJsonMap(payload)), "forge-preferences-");
    } catch (IOException ignored) {
      // Best-effort persistence only.
    }
  }

  private UserPreferencesPayload loadPayload() {
    if (!Files.isRegularFile(file)) {
      return null;
    }
    try {
      Map<String, Object> root = JsonSupport.parseObject(Files.readString(file));
      Integer schemaVersion = JsonFieldReader.readInt(root, "schemaVersion");
      if (schemaVersion == null || schemaVersion != SCHEMA_VERSION) {
        return null;
      }
      return new UserPreferencesPayload(
          schemaVersion,
          JsonFieldReader.readString(root, "groupId"),
          JsonFieldReader.readString(root, "artifactId"),
          JsonFieldReader.readString(root, "version"),
          JsonFieldReader.readString(root, "packageName"),
          JsonFieldReader.readString(root, "outputDirectory"),
          JsonFieldReader.readString(root, "platformStream"),
          JsonFieldReader.readString(root, "buildTool"),
          JsonFieldReader.readString(root, "javaVersion"));
    } catch (IOException | RuntimeException ignored) {
      return null;
    }
  }

  private static Map<String, Object> toJsonMap(UserPreferencesPayload payload) {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("schemaVersion", payload.schemaVersion());
    root.put("groupId", payload.groupId());
    root.put("artifactId", payload.artifactId());
    root.put("version", payload.version());
    root.put("packageName", payload.packageName());
    root.put("outputDirectory", payload.outputDirectory());
    root.put("platformStream", payload.platformStream());
    root.put("buildTool", payload.buildTool());
    root.put("javaVersion", payload.javaVersion());
    return root;
  }

  private static String normalize(String value) {
    return value == null ? "" : value;
  }
}
