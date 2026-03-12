package dev.ayagmar.quarkusforge.api;

import dev.ayagmar.quarkusforge.util.FilePermissionSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ForgeDataPaths {
  private static final String APP_DIR = ".quarkus-forge";

  private ForgeDataPaths() {}

  public static Path appDataRoot() {
    return Path.of(System.getProperty("user.home", ".")).resolve(APP_DIR);
  }

  public static Path catalogSnapshotFile() {
    return appDataRoot().resolve("catalog-snapshot.json");
  }

  public static Path preferencesFile() {
    return appDataRoot().resolve("preferences.json");
  }

  public static Path favoritesFile() {
    return appDataRoot().resolve("favorites.json");
  }

  public static Path recipesRoot() {
    return appDataRoot().resolve("recipes");
  }

  public static boolean isManagedPath(Path path) {
    if (path == null) {
      return false;
    }
    return path.toAbsolutePath().normalize().startsWith(appDataRoot().toAbsolutePath().normalize());
  }

  public static void ensureManagedDirectoryHierarchy(Path directory) throws IOException {
    Path normalizedDirectory = directory.toAbsolutePath().normalize();
    if (!isManagedPath(normalizedDirectory)) {
      Files.createDirectories(normalizedDirectory);
      return;
    }

    Path root = appDataRoot().toAbsolutePath().normalize();
    Path current = root;
    Files.createDirectories(current);
    FilePermissionSupport.ensureOwnerOnlyDirectory(current);

    for (Path segment : root.relativize(normalizedDirectory)) {
      current = current.resolve(segment);
      Files.createDirectories(current);
      FilePermissionSupport.ensureOwnerOnlyDirectory(current);
    }
  }
}
