package dev.ayagmar.quarkusforge.ui;

import dev.ayagmar.quarkusforge.api.ApiContractException;
import dev.ayagmar.quarkusforge.api.AtomicFileStore;
import dev.ayagmar.quarkusforge.api.JsonSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
      Map<String, Object> root = JsonSupport.parseObject(Files.readString(file));
      Integer schemaVersion = readInt(root, "schemaVersion");
      if (schemaVersion == null || schemaVersion != SCHEMA_VERSION) {
        return null;
      }
      return new ExtensionFavoritesPayload(
          schemaVersion,
          readStringSet(root, "favoriteExtensionIds"),
          readStringList(root, "recentExtensionIds"));
    } catch (IOException | RuntimeException ignored) {
      return null;
    }
  }

  private void writeState(Set<String> favoriteExtensionIds, List<String> recentExtensionIds) {
    try {
      ExtensionFavoritesPayload payload =
          new ExtensionFavoritesPayload(
              SCHEMA_VERSION, new TreeSet<>(favoriteExtensionIds), List.copyOf(recentExtensionIds));
      AtomicFileStore.writeBytes(
          file, JsonSupport.writeBytes(toJsonMap(payload)), "extension-favorites-");
    } catch (IOException ignored) {
      // Best-effort persistence only.
    }
  }

  private static Map<String, Object> toJsonMap(ExtensionFavoritesPayload payload) {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("schemaVersion", payload.schemaVersion());
    root.put("favoriteExtensionIds", payload.favoriteExtensionIds());
    root.put("recentExtensionIds", payload.recentExtensionIds());
    return root;
  }

  private static Integer readInt(Map<String, Object> root, String key) {
    Object value = root.get(key);
    if (value == null) {
      return null;
    }
    if (!(value instanceof Number number)) {
      throw new ApiContractException("Malformed JSON payload");
    }
    long longValue = number.longValue();
    if (longValue > Integer.MAX_VALUE || longValue < Integer.MIN_VALUE) {
      throw new ApiContractException("Malformed JSON payload");
    }
    return (int) longValue;
  }

  private static Set<String> readStringSet(Map<String, Object> root, String key) {
    List<String> values = readStringList(root, key);
    if (values == null) {
      return null;
    }
    return Set.copyOf(values);
  }

  private static List<String> readStringList(Map<String, Object> root, String key) {
    Object value = root.get(key);
    if (value == null) {
      return null;
    }
    if (!(value instanceof List<?> rawList)) {
      throw new ApiContractException("Malformed JSON payload");
    }
    List<String> values = new ArrayList<>();
    for (Object element : rawList) {
      if (!(element instanceof String stringValue)) {
        throw new ApiContractException("Malformed JSON payload");
      }
      values.add(stringValue);
    }
    return List.copyOf(values);
  }
}
