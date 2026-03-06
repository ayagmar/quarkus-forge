package dev.ayagmar.quarkusforge.forge;

import dev.ayagmar.quarkusforge.api.AtomicFileStore;
import dev.ayagmar.quarkusforge.api.JsonFieldReader;
import dev.ayagmar.quarkusforge.api.JsonSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reads and writes {@link Forgefile} instances as JSON. */
public final class ForgefileStore {
  private ForgefileStore() {}

  public static Forgefile load(Path file) {
    String content;
    try {
      content = Files.readString(file);
    } catch (NoSuchFileException noSuchFileException) {
      throw new IllegalArgumentException(
          "Forgefile not found: '" + file + "'", noSuchFileException);
    } catch (IOException ioException) {
      throw new IllegalArgumentException(
          "Failed to read Forgefile '" + file + "': " + ioException.getMessage(), ioException);
    }

    Map<String, Object> root;
    try {
      root = JsonSupport.parseObject(content);
    } catch (IOException ioException) {
      throw new IllegalArgumentException(
          "Failed to parse Forgefile '" + file + "': " + ioException.getMessage(), ioException);
    } catch (RuntimeException runtimeException) {
      throw new IllegalArgumentException(
          "Failed to parse Forgefile '" + file + "': " + runtimeException.getMessage(),
          runtimeException);
    }

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
  }

  public static void save(Path file, Forgefile forgefile) {
    try {
      Map<String, Object> root = new LinkedHashMap<>();
      root.put("groupId", forgefile.groupId());
      root.put("artifactId", forgefile.artifactId());
      root.put("version", forgefile.version());
      putIfNotBlank(root, "packageName", forgefile.packageName());
      putIfNotBlank(root, "outputDirectory", forgefile.outputDirectory());
      putIfNotBlank(root, "platformStream", forgefile.platformStream());
      root.put("buildTool", forgefile.buildTool());
      root.put("javaVersion", forgefile.javaVersion());
      root.put("presets", forgefile.presets());
      root.put("extensions", forgefile.extensions());
      if (forgefile.locked() != null) {
        root.put("locked", lockedToMap(forgefile.locked()));
      }
      byte[] bytes = JsonSupport.writePrettyString(root).getBytes(StandardCharsets.UTF_8);
      AtomicFileStore.writeBytes(file, bytes, "forgefile-");
    } catch (IOException exception) {
      throw new IllegalArgumentException(
          "Failed to write Forgefile '" + file + "': " + exception.getMessage(), exception);
    }
  }

  private static void putIfNotBlank(Map<String, Object> map, String key, String value) {
    if (value != null && !value.isBlank()) {
      map.put(key, value);
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
}
