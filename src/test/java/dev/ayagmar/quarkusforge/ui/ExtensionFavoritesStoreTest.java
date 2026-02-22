package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtensionFavoritesStoreTest {
  @TempDir Path tempDir;

  @Test
  void fileBackedStorePersistsFavoriteIdsAcrossInstances() {
    Path favoritesFile = tempDir.resolve("favorites.json");
    ExtensionFavoritesStore writerStore = ExtensionFavoritesStore.fileBacked(favoritesFile);

    writerStore.saveFavoriteExtensionIds(
        Set.of("io.quarkus:quarkus-rest", "io.quarkus:quarkus-arc"));

    ExtensionFavoritesStore readerStore = ExtensionFavoritesStore.fileBacked(favoritesFile);
    assertThat(readerStore.loadFavoriteExtensionIds())
        .containsExactlyInAnyOrder("io.quarkus:quarkus-rest", "io.quarkus:quarkus-arc");
  }

  @Test
  void invalidSchemaReturnsEmptyFavorites() throws Exception {
    Path favoritesFile = tempDir.resolve("favorites.json");
    Files.writeString(
        favoritesFile,
        """
        {
          "schemaVersion": 999,
          "favoriteExtensionIds": ["io.quarkus:quarkus-rest"]
        }
        """);

    ExtensionFavoritesStore store = ExtensionFavoritesStore.fileBacked(favoritesFile);
    assertThat(store.loadFavoriteExtensionIds()).isEmpty();
  }
}
