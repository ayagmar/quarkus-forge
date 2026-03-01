package dev.ayagmar.quarkusforge.api;

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
}
