package dev.ayagmar.quarkusforge.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtensionFavoritesStoreTest {
  @TempDir Path tempDir;

  @Test
  void fileBackedStorePersistsFavoriteIdsAcrossInstances() {
    Path favoritesFile = tempDir.resolve("favorites.json");
    ExtensionFavoritesStore writerStore = ExtensionFavoritesStore.fileBacked(favoritesFile);

    writerStore.saveAll(Set.of("io.quarkus:quarkus-rest", "io.quarkus:quarkus-arc"), List.of());

    ExtensionFavoritesStore readerStore = ExtensionFavoritesStore.fileBacked(favoritesFile);
    assertThat(readerStore.loadFavoriteExtensionIds())
        .containsExactlyInAnyOrder("io.quarkus:quarkus-rest", "io.quarkus:quarkus-arc");
  }

  @Test
  void fileBackedStorePersistsRecentIdsAcrossInstances() {
    Path favoritesFile = tempDir.resolve("favorites.json");
    ExtensionFavoritesStore writerStore = ExtensionFavoritesStore.fileBacked(favoritesFile);

    writerStore.saveAll(
        Set.of(),
        List.of("io.quarkus:quarkus-rest", "io.quarkus:quarkus-arc", "io.quarkus:quarkus-rest"));

    ExtensionFavoritesStore readerStore = ExtensionFavoritesStore.fileBacked(favoritesFile);
    assertThat(readerStore.loadRecentExtensionIds())
        .containsExactly("io.quarkus:quarkus-rest", "io.quarkus:quarkus-arc");
  }

  @Test
  void fileBackedStoreSaveAllPersistsBothAtomically() {
    Path favoritesFile = tempDir.resolve("favorites.json");
    ExtensionFavoritesStore writerStore = ExtensionFavoritesStore.fileBacked(favoritesFile);

    writerStore.saveAll(Set.of("io.quarkus:quarkus-rest"), List.of("io.quarkus:quarkus-arc"));

    ExtensionFavoritesStore readerStore = ExtensionFavoritesStore.fileBacked(favoritesFile);
    assertThat(readerStore.loadFavoriteExtensionIds()).containsExactly("io.quarkus:quarkus-rest");
    assertThat(readerStore.loadRecentExtensionIds()).containsExactly("io.quarkus:quarkus-arc");
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
    assertThat(store.loadRecentExtensionIds()).isEmpty();
  }

  @Test
  void inMemoryStoreWorksIsolated() {
    ExtensionFavoritesStore store = ExtensionFavoritesStore.inMemory();
    assertThat(store.loadFavoriteExtensionIds()).isEmpty();
    assertThat(store.loadRecentExtensionIds()).isEmpty();

    store.saveAll(Set.of("io.quarkus:quarkus-rest"), List.of("io.quarkus:quarkus-arc"));

    assertThat(store.loadFavoriteExtensionIds()).containsExactly("io.quarkus:quarkus-rest");
    assertThat(store.loadRecentExtensionIds()).containsExactly("io.quarkus:quarkus-arc");
  }

  @Test
  void fileBackedStoreLoadsEmptyFromNonExistentFile() {
    Path favoritesFile = tempDir.resolve("nonexistent-favorites.json");
    ExtensionFavoritesStore store = ExtensionFavoritesStore.fileBacked(favoritesFile);

    assertThat(store.loadFavoriteExtensionIds()).isEmpty();
    assertThat(store.loadRecentExtensionIds()).isEmpty();
  }

  @Test
  void fileBackedStoreLoadsEmptyFromCorruptFile() throws Exception {
    Path favoritesFile = tempDir.resolve("corrupt-favorites.json");
    Files.writeString(favoritesFile, "not valid json");

    ExtensionFavoritesStore store = ExtensionFavoritesStore.fileBacked(favoritesFile);
    assertThat(store.loadFavoriteExtensionIds()).isEmpty();
    assertThat(store.loadRecentExtensionIds()).isEmpty();
  }

  @Test
  void fileBackedStoreLoadsEmptyFromWrongTypedPayload() throws Exception {
    Path favoritesFile = tempDir.resolve("typed-favorites.json");
    Files.writeString(
        favoritesFile,
        """
        {
          "schemaVersion": 1,
          "favoriteExtensionIds": "io.quarkus:quarkus-rest",
          "recentExtensionIds": ["io.quarkus:quarkus-arc"]
        }
        """);

    ExtensionFavoritesStore store = ExtensionFavoritesStore.fileBacked(favoritesFile);
    assertThat(store.loadFavoriteExtensionIds()).isEmpty();
    assertThat(store.loadRecentExtensionIds()).isEmpty();
  }
}
