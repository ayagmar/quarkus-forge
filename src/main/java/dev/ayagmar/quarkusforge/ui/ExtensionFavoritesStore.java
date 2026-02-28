package dev.ayagmar.quarkusforge.ui;

import dev.ayagmar.quarkusforge.api.ForgeDataPaths;
import dev.ayagmar.quarkusforge.api.ObjectMapperProvider;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public interface ExtensionFavoritesStore {
  int SCHEMA_VERSION = 1;

  Set<String> loadFavoriteExtensionIds();

  void saveFavoriteExtensionIds(Set<String> favoriteExtensionIds);

  List<String> loadRecentExtensionIds();

  void saveRecentExtensionIds(List<String> recentExtensionIds);

  static ExtensionFavoritesStore inMemory() {
    return new InMemoryExtensionFavoritesStore();
  }

  static ExtensionFavoritesStore fileBacked(Path file) {
    return new FileBackedExtensionFavoritesStore(file, ObjectMapperProvider.shared());
  }

  static Path defaultFile() {
    return ForgeDataPaths.favoritesFile();
  }
}
