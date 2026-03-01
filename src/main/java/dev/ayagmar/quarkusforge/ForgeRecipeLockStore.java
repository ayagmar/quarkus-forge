package dev.ayagmar.quarkusforge;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ayagmar.quarkusforge.api.ObjectMapperProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ForgeRecipeLockStore {
  private static final ObjectMapper OBJECT_MAPPER = ObjectMapperProvider.shared();

  private ForgeRecipeLockStore() {}

  public static ForgeRecipe loadRecipe(Path file) {
    try {
      return OBJECT_MAPPER.readValue(Files.readString(file), ForgeRecipe.class);
    } catch (IOException ioException) {
      throw new IllegalArgumentException(
          "Failed to read recipe '" + file + "': " + ioException.getMessage(), ioException);
    }
  }

  public static void writeRecipe(Path file, ForgeRecipe recipe) {
    try {
      createParentDirectories(file);
      OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), recipe);
    } catch (IOException ioException) {
      throw new IllegalArgumentException(
          "Failed to write recipe '" + file + "': " + ioException.getMessage(), ioException);
    }
  }

  public static ForgeLock loadLock(Path file) {
    try {
      return OBJECT_MAPPER.readValue(Files.readString(file), ForgeLock.class);
    } catch (IOException ioException) {
      throw new IllegalArgumentException(
          "Failed to read lock file '" + file + "': " + ioException.getMessage(), ioException);
    }
  }

  public static void writeLock(Path file, ForgeLock lock) {
    try {
      createParentDirectories(file);
      OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), lock);
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
