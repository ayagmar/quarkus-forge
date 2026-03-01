package dev.ayagmar.quarkusforge;

import dev.ayagmar.quarkusforge.api.JsonFieldReader;
import dev.ayagmar.quarkusforge.api.JsonSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ForgeRecipeLockStore {
  private ForgeRecipeLockStore() {}

  public static ForgeRecipe loadRecipe(Path file) {
    try {
      Map<String, Object> root = JsonSupport.parseObject(Files.readString(file));
      return new ForgeRecipe(
          JsonFieldReader.readStringOrEmpty(root, "groupId"),
          JsonFieldReader.readStringOrEmpty(root, "artifactId"),
          JsonFieldReader.readStringOrEmpty(root, "version"),
          JsonFieldReader.readStringOrEmpty(root, "packageName"),
          JsonFieldReader.readStringOrEmpty(root, "outputDirectory"),
          JsonFieldReader.readStringOrEmpty(root, "platformStream"),
          JsonFieldReader.readStringOrEmpty(root, "buildTool"),
          JsonFieldReader.readStringOrEmpty(root, "javaVersion"),
          JsonFieldReader.readStringListOrEmpty(root, "presets"),
          JsonFieldReader.readStringListOrEmpty(root, "extensions"));
    } catch (IOException | RuntimeException ioException) {
      throw new IllegalArgumentException(
          "Failed to read recipe '" + file + "': " + ioException.getMessage(), ioException);
    }
  }

  public static void writeRecipe(Path file, ForgeRecipe recipe) {
    try {
      createParentDirectories(file);
      Map<String, Object> root = new LinkedHashMap<>();
      root.put("groupId", recipe.groupId());
      root.put("artifactId", recipe.artifactId());
      root.put("version", recipe.version());
      root.put("packageName", recipe.packageName());
      root.put("outputDirectory", recipe.outputDirectory());
      root.put("platformStream", recipe.platformStream());
      root.put("buildTool", recipe.buildTool());
      root.put("javaVersion", recipe.javaVersion());
      root.put("presets", recipe.presets());
      root.put("extensions", recipe.extensions());
      Files.writeString(file, JsonSupport.writePrettyString(root), StandardCharsets.UTF_8);
    } catch (IOException ioException) {
      throw new IllegalArgumentException(
          "Failed to write recipe '" + file + "': " + ioException.getMessage(), ioException);
    }
  }

  public static ForgeLock loadLock(Path file) {
    try {
      Map<String, Object> root = JsonSupport.parseObject(Files.readString(file));
      return new ForgeLock(
          JsonFieldReader.readStringOrEmpty(root, "platformStream"),
          JsonFieldReader.readStringOrEmpty(root, "buildTool"),
          JsonFieldReader.readStringOrEmpty(root, "javaVersion"),
          JsonFieldReader.readStringListOrEmpty(root, "presets"),
          JsonFieldReader.readStringListOrEmpty(root, "extensions"));
    } catch (IOException | RuntimeException ioException) {
      throw new IllegalArgumentException(
          "Failed to read lock file '" + file + "': " + ioException.getMessage(), ioException);
    }
  }

  public static void writeLock(Path file, ForgeLock lock) {
    try {
      createParentDirectories(file);
      Map<String, Object> root = new LinkedHashMap<>();
      root.put("platformStream", lock.platformStream());
      root.put("buildTool", lock.buildTool());
      root.put("javaVersion", lock.javaVersion());
      root.put("presets", lock.presets());
      root.put("extensions", lock.extensions());
      Files.writeString(file, JsonSupport.writePrettyString(root), StandardCharsets.UTF_8);
    } catch (IOException ioException) {
      throw new IllegalArgumentException(
          "Failed to write lock file '" + file + "': " + ioException.getMessage(), ioException);
    }
  }

  private static void createParentDirectories(Path file) throws IOException {
    Path parent = file.toAbsolutePath().normalize().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
  }
}
