package dev.ayagmar.quarkusforge;

import dev.ayagmar.quarkusforge.api.JsonFieldReader;
import dev.ayagmar.quarkusforge.api.JsonSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reads and writes {@link Forgefile} instances as JSON. */
public final class ForgefileStore {
  private ForgefileStore() {}

  public static Forgefile load(Path file) {
    try {
      Map<String, Object> root = JsonSupport.parseObject(Files.readString(file));
      ForgefileLock locked = readLockedSection(root);
      return new Forgefile(
          JsonFieldReader.readStringOrEmpty(root, "groupId"),
          JsonFieldReader.readStringOrEmpty(root, "artifactId"),
          JsonFieldReader.readStringOrEmpty(root, "version"),
          JsonFieldReader.readStringOrEmpty(root, "packageName"),
          JsonFieldReader.readStringOrEmpty(root, "outputDirectory"),
          JsonFieldReader.readStringOrEmpty(root, "platformStream"),
          JsonFieldReader.readStringOrEmpty(root, "buildTool"),
          JsonFieldReader.readStringOrEmpty(root, "javaVersion"),
          JsonFieldReader.readStringListOrEmpty(root, "presets"),
          JsonFieldReader.readStringListOrEmpty(root, "extensions"),
          locked);
    } catch (IOException | RuntimeException exception) {
      throw new IllegalArgumentException(
          "Failed to read Forgefile '" + file + "': " + exception.getMessage(), exception);
    }
  }

  public static void save(Path file, Forgefile forgefile) {
    try {
      createParentDirectories(file);
      Map<String, Object> root = new LinkedHashMap<>();
      root.put("groupId", forgefile.groupId());
      root.put("artifactId", forgefile.artifactId());
      root.put("version", forgefile.version());
      root.put("packageName", forgefile.packageName());
      root.put("outputDirectory", forgefile.outputDirectory());
      root.put("platformStream", forgefile.platformStream());
      root.put("buildTool", forgefile.buildTool());
      root.put("javaVersion", forgefile.javaVersion());
      root.put("presets", forgefile.presets());
      root.put("extensions", forgefile.extensions());
      if (forgefile.locked() != null) {
        root.put("locked", lockedToMap(forgefile.locked()));
      }
      Files.writeString(file, JsonSupport.writePrettyString(root), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new IllegalArgumentException(
          "Failed to write Forgefile '" + file + "': " + exception.getMessage(), exception);
    }
  }

  @SuppressWarnings("unchecked")
  private static ForgefileLock readLockedSection(Map<String, Object> root) {
    Object lockedObj = root.get("locked");
    if (lockedObj == null) {
      return null;
    }
    if (!(lockedObj instanceof Map<?, ?> lockedMap)) {
      return null;
    }
    Map<String, Object> locked = (Map<String, Object>) lockedMap;
    return new ForgefileLock(
        JsonFieldReader.readStringOrEmpty(locked, "platformStream"),
        JsonFieldReader.readStringOrEmpty(locked, "buildTool"),
        JsonFieldReader.readStringOrEmpty(locked, "javaVersion"),
        JsonFieldReader.readStringListOrEmpty(locked, "presets"),
        JsonFieldReader.readStringListOrEmpty(locked, "extensions"));
  }

  private static Map<String, Object> lockedToMap(ForgefileLock lock) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("platformStream", lock.platformStream());
    map.put("buildTool", lock.buildTool());
    map.put("javaVersion", lock.javaVersion());
    map.put("presets", lock.presets());
    map.put("extensions", lock.extensions());
    return map;
  }

  private static void createParentDirectories(Path file) throws IOException {
    Path parent = file.toAbsolutePath().normalize().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
  }
}
