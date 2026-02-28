package dev.ayagmar.quarkusforge.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ayagmar.quarkusforge.api.AtomicFileStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

final class FileBackedExtensionFavoritesStore implements ExtensionFavoritesStore {
  private final Path file;
  private final ObjectMapper objectMapper;

  FileBackedExtensionFavoritesStore(Path file, ObjectMapper objectMapper) {
    this.file = Objects.requireNonNull(file);
    this.objectMapper = Objects.requireNonNull(objectMapper);
  }

  @Override
  public Set<String> loadFavoriteExtensionIds() {
    ExtensionFavoritesPayload payload = loadPayload();
    if (payload == null || payload.favoriteExtensionIds() == null) {
      return Set.of();
    }
    return ExtensionFavoriteIds.normalizeSet(payload.favoriteExtensionIds());
  }

  @Override
  public List<String> loadRecentExtensionIds() {
    ExtensionFavoritesPayload payload = loadPayload();
    if (payload == null || payload.recentExtensionIds() == null) {
      return List.of();
    }
    return ExtensionFavoriteIds.normalizeList(payload.recentExtensionIds());
  }

  @Override
  public void saveFavoriteExtensionIds(Set<String> favoriteExtensionIds) {
    writeState(ExtensionFavoriteIds.normalizeSet(favoriteExtensionIds), loadRecentExtensionIds());
  }

  @Override
  public void saveRecentExtensionIds(List<String> recentExtensionIds) {
    writeState(loadFavoriteExtensionIds(), ExtensionFavoriteIds.normalizeList(recentExtensionIds));
  }

  private ExtensionFavoritesPayload loadPayload() {
    if (!Files.isRegularFile(file)) {
      return null;
    }
    try {
      ExtensionFavoritesPayload payload =
          objectMapper.readValue(file.toFile(), ExtensionFavoritesPayload.class);
      if (payload == null || payload.schemaVersion() != SCHEMA_VERSION) {
        return null;
      }
      return payload;
    } catch (IOException ignored) {
      return null;
    }
  }

  private void writeState(Set<String> favoriteExtensionIds, List<String> recentExtensionIds) {
    try {
      ExtensionFavoritesPayload payload =
          new ExtensionFavoritesPayload(
              SCHEMA_VERSION, new TreeSet<>(favoriteExtensionIds), List.copyOf(recentExtensionIds));
      AtomicFileStore.writeBytes(
          file, objectMapper.writeValueAsBytes(payload), "extension-favorites-");
    } catch (IOException ignored) {
      // Best-effort persistence only.
    }
  }
}
