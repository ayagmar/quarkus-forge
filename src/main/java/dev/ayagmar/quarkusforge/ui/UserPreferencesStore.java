package dev.ayagmar.quarkusforge.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
    JsonNode root = loadRoot();
    if (root == null) {
      return null;
    }
    return new CliPrefill(
        readText(root, "groupId"),
        readText(root, "artifactId"),
        readText(root, "version"),
        readText(root, "packageName"),
        readText(root, "outputDirectory"),
        readText(root, "platformStream"),
        readText(root, "buildTool"),
        readText(root, "javaVersion"));
  }

  public void saveLastRequest(ProjectRequest request) {
    Objects.requireNonNull(request);
    try {
      ObjectNode root = objectMapper.createObjectNode();
      root.put("schemaVersion", SCHEMA_VERSION);
      root.put("groupId", request.groupId());
      root.put("artifactId", request.artifactId());
      root.put("version", request.version());
      root.put("packageName", request.packageName());
      root.put("outputDirectory", request.outputDirectory());
      root.put("platformStream", request.platformStream());
      root.put("buildTool", request.buildTool());
      root.put("javaVersion", request.javaVersion());
      AtomicFileStore.writeBytes(file, objectMapper.writeValueAsBytes(root), "forge-preferences-");
    } catch (IOException ignored) {
      // Best-effort persistence only.
    }
  }

  private JsonNode loadRoot() {
    if (!Files.isRegularFile(file)) {
      return null;
    }
    try {
      JsonNode root = objectMapper.readTree(file.toFile());
      if (!root.isObject()) {
        return null;
      }
      JsonNode schemaVersion = root.get("schemaVersion");
      if (schemaVersion == null || !schemaVersion.canConvertToInt()) {
        return null;
      }
      if (schemaVersion.intValue() != SCHEMA_VERSION) {
        return null;
      }
      return root;
    } catch (IOException ignored) {
      return null;
    }
  }

  private static String readText(JsonNode root, String field) {
    JsonNode value = root.get(field);
    if (value == null || !value.isTextual()) {
      return "";
    }
    return value.textValue();
  }
}
