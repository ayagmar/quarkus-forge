package dev.ayagmar.quarkusforge.api;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public interface ExtensionFavoritesStore {
  int SCHEMA_VERSION = 1;

  Set<String> loadFavoriteExtensionIds();

  List<String> loadRecentExtensionIds();

  /**
   * Atomically persists both favorites and recents in a single write. Callers must always provide
   * both values to avoid silent data loss from read-merge-write races.
   */
  void saveAll(Set<String> favoriteExtensionIds, List<String> recentExtensionIds);

  static ExtensionFavoritesStore inMemory() {
    return new InMemoryExtensionFavoritesStore();
  }

  static ExtensionFavoritesStore fileBacked(Path file) {
    return new FileBackedExtensionFavoritesStore(file);
  }

  static Path defaultFile() {
    return ForgeDataPaths.favoritesFile();
  }
}
