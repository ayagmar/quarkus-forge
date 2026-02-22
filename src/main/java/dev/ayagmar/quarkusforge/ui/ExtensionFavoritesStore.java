package dev.ayagmar.quarkusforge.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.ayagmar.quarkusforge.api.ObjectMapperProvider;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public interface ExtensionFavoritesStore {
  int SCHEMA_VERSION = 1;

  Set<String> loadFavoriteExtensionIds();

  void saveFavoriteExtensionIds(Set<String> favoriteExtensionIds);

  static ExtensionFavoritesStore inMemory() {
    return new InMemoryExtensionFavoritesStore();
  }

  static ExtensionFavoritesStore fileBacked(Path file) {
    return new FileBackedExtensionFavoritesStore(file, ObjectMapperProvider.shared());
  }

  static Path defaultFile() {
    Path home = Path.of(System.getProperty("user.home", "."));
    return home.resolve(".quarkus-forge").resolve("favorites.json");
  }

  final class InMemoryExtensionFavoritesStore implements ExtensionFavoritesStore {
    private Set<String> favoriteExtensionIds = Set.of();

    @Override
    public synchronized Set<String> loadFavoriteExtensionIds() {
      return favoriteExtensionIds;
    }

    @Override
    public synchronized void saveFavoriteExtensionIds(Set<String> favoriteExtensionIds) {
      this.favoriteExtensionIds = normalizeIds(favoriteExtensionIds);
    }
  }

  final class FileBackedExtensionFavoritesStore implements ExtensionFavoritesStore {
    private final Path file;
    private final ObjectMapper objectMapper;

    private FileBackedExtensionFavoritesStore(Path file, ObjectMapper objectMapper) {
      this.file = Objects.requireNonNull(file);
      this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public Set<String> loadFavoriteExtensionIds() {
      if (!Files.isRegularFile(file)) {
        return Set.of();
      }
      try {
        JsonNode root = objectMapper.readTree(file.toFile());
        if (!root.isObject()) {
          return Set.of();
        }
        JsonNode schemaVersion = root.get("schemaVersion");
        if (schemaVersion == null || !schemaVersion.canConvertToInt()) {
          return Set.of();
        }
        if (schemaVersion.intValue() != SCHEMA_VERSION) {
          return Set.of();
        }
        JsonNode favoritesNode = root.get("favoriteExtensionIds");
        if (favoritesNode == null || !favoritesNode.isArray()) {
          return Set.of();
        }
        List<String> favoriteIds = new ArrayList<>();
        for (JsonNode favoriteNode : favoritesNode) {
          if (!favoriteNode.isTextual()) {
            return Set.of();
          }
          favoriteIds.add(favoriteNode.textValue());
        }
        return normalizeIds(favoriteIds);
      } catch (IOException ignored) {
        return Set.of();
      }
    }

    @Override
    public void saveFavoriteExtensionIds(Set<String> favoriteExtensionIds) {
      Set<String> normalizedIds = normalizeIds(favoriteExtensionIds);
      try {
        Path parent = file.toAbsolutePath().normalize().getParent();
        if (parent == null) {
          return;
        }
        Files.createDirectories(parent);
        Path tempFile = Files.createTempFile(parent, "extension-favorites-", ".tmp");
        try {
          ObjectNode root = objectMapper.createObjectNode();
          root.put("schemaVersion", SCHEMA_VERSION);
          root.set("favoriteExtensionIds", objectMapper.valueToTree(new TreeSet<>(normalizedIds)));
          Files.write(
              tempFile,
              objectMapper.writeValueAsBytes(root),
              StandardOpenOption.TRUNCATE_EXISTING,
              StandardOpenOption.WRITE);
          moveAtomicallyWithFallback(tempFile, file);
        } finally {
          Files.deleteIfExists(tempFile);
        }
      } catch (IOException ignored) {
        // Best-effort persistence only.
      }
    }

    private static void moveAtomicallyWithFallback(Path source, Path target) throws IOException {
      try {
        Files.move(
            source,
            target,
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            java.nio.file.StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException ignored) {
        Files.move(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      }
    }
  }

  private static Set<String> normalizeIds(Iterable<String> source) {
    Set<String> normalized = new LinkedHashSet<>();
    for (String extensionId : source) {
      if (extensionId == null) {
        continue;
      }
      String trimmed = extensionId.trim();
      if (!trimmed.isBlank()) {
        normalized.add(trimmed);
      }
    }
    return Set.copyOf(normalized);
  }
}
