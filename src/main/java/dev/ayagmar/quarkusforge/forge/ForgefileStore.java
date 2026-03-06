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
import java.util.List;
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
        JsonFieldReader.readString(root, "groupId"),
        JsonFieldReader.readString(root, "artifactId"),
        JsonFieldReader.readString(root, "version"),
        JsonFieldReader.readString(root, "packageName"),
        JsonFieldReader.readString(root, "outputDirectory"),
        JsonFieldReader.readString(root, "platformStream"),
        JsonFieldReader.readString(root, "buildTool"),
        JsonFieldReader.readString(root, "javaVersion"),
        JsonFieldReader.readStringList(root, "presets"),
        JsonFieldReader.readStringList(root, "extensions"),
        locked);
  }

  public static void save(Path file, Forgefile forgefile) {
    try {
      Map<String, Object> root = new LinkedHashMap<>();
      putIfPresent(root, "groupId", forgefile.groupId());
      putIfPresent(root, "artifactId", forgefile.artifactId());
      putIfPresent(root, "version", forgefile.version());
      putIfPresent(root, "packageName", forgefile.packageName());
      putIfPresent(root, "outputDirectory", forgefile.outputDirectory());
      putIfPresent(root, "platformStream", forgefile.platformStream());
      putIfPresent(root, "buildTool", forgefile.buildTool());
      putIfPresent(root, "javaVersion", forgefile.javaVersion());
      putIfPresent(root, "presets", forgefile.presets());
      putIfPresent(root, "extensions", forgefile.extensions());
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

  private static void putIfPresent(Map<String, Object> map, String key, String value) {
    if (value != null && !value.isBlank()) {
      map.put(key, value);
    }
  }

  private static void putIfPresent(Map<String, Object> map, String key, List<String> values) {
    if (values != null) {
      map.put(key, values);
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
