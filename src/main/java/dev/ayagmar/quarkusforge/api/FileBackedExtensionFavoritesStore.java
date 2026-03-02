package dev.ayagmar.quarkusforge.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

final class FileBackedExtensionFavoritesStore implements ExtensionFavoritesStore {
  private final Path file;

  FileBackedExtensionFavoritesStore(Path file) {
    this.file = Objects.requireNonNull(file);
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
  public void saveAll(Set<String> favoriteExtensionIds, List<String> recentExtensionIds) {
    try {
      ExtensionFavoritesPayload payload =
          new ExtensionFavoritesPayload(
              SCHEMA_VERSION,
              new TreeSet<>(ExtensionFavoriteIds.normalizeSet(favoriteExtensionIds)),
              List.copyOf(ExtensionFavoriteIds.normalizeList(recentExtensionIds)));
      AtomicFileStore.writeBytes(
          file, JsonSupport.writeBytes(toJsonMap(payload)), "extension-favorites-");
    } catch (IOException ignored) {
      // Best-effort persistence only.
    }
  }

  private ExtensionFavoritesPayload loadPayload() {
    if (!Files.isRegularFile(file)) {
      return null;
    }
    try {
      Map<String, Object> root = JsonSupport.parseObject(Files.readString(file));
      Integer schemaVersion = JsonFieldReader.readInt(root, "schemaVersion");
      if (schemaVersion == null || schemaVersion != SCHEMA_VERSION) {
        return null;
      }
      return new ExtensionFavoritesPayload(
          schemaVersion,
          JsonFieldReader.readStringSet(root, "favoriteExtensionIds"),
          JsonFieldReader.readStringList(root, "recentExtensionIds"));
    } catch (IOException | RuntimeException ignored) {
      return null;
    }
  }

  private static Map<String, Object> toJsonMap(ExtensionFavoritesPayload payload) {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("schemaVersion", payload.schemaVersion());
    root.put("favoriteExtensionIds", payload.favoriteExtensionIds());
    root.put("recentExtensionIds", payload.recentExtensionIds());
    return root;
  }
}
