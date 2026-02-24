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

  List<String> loadRecentExtensionIds();

  void saveRecentExtensionIds(List<String> recentExtensionIds);

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
    private List<String> recentExtensionIds = List.of();

    @Override
    public synchronized Set<String> loadFavoriteExtensionIds() {
      return favoriteExtensionIds;
    }

    @Override
    public synchronized void saveFavoriteExtensionIds(Set<String> favoriteExtensionIds) {
      this.favoriteExtensionIds = normalizeIds(favoriteExtensionIds);
    }

    @Override
    public synchronized List<String> loadRecentExtensionIds() {
      return recentExtensionIds;
    }

    @Override
    public synchronized void saveRecentExtensionIds(List<String> recentExtensionIds) {
      this.recentExtensionIds = normalizeIdList(recentExtensionIds);
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
      JsonNode root = loadRoot();
      if (root == null) {
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
    }

    @Override
    public List<String> loadRecentExtensionIds() {
      JsonNode root = loadRoot();
      if (root == null) {
        return List.of();
      }
      JsonNode recentNode = root.get("recentExtensionIds");
      if (recentNode == null || !recentNode.isArray()) {
        return List.of();
      }
      List<String> recentIds = new ArrayList<>();
      for (JsonNode recentEntry : recentNode) {
        if (!recentEntry.isTextual()) {
          return List.of();
        }
        recentIds.add(recentEntry.textValue());
      }
      return normalizeIdList(recentIds);
    }

    @Override
    public void saveFavoriteExtensionIds(Set<String> favoriteExtensionIds) {
      writeState(normalizeIds(favoriteExtensionIds), loadRecentExtensionIds());
    }

    @Override
    public void saveRecentExtensionIds(List<String> recentExtensionIds) {
      writeState(loadFavoriteExtensionIds(), normalizeIdList(recentExtensionIds));
    }

    private JsonNode loadRoot() {
      if (!Files.isRegularFile(file)) {
        return null;
      }
      try {
        JsonNode root = objectMapper.readTree(file.toFile());
        if (!root.isObject()) {
          return null;
        }
        JsonNode schemaVersion = root.get("schemaVersion");
        if (schemaVersion == null || !schemaVersion.canConvertToInt()) {
          return null;
        }
        if (schemaVersion.intValue() != SCHEMA_VERSION) {
          return null;
        }
        return root;
      } catch (IOException ignored) {
        return null;
      }
    }

    private void writeState(Set<String> favoriteExtensionIds, List<String> recentExtensionIds) {
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
          root.set(
              "favoriteExtensionIds",
              objectMapper.valueToTree(new TreeSet<>(favoriteExtensionIds)));
          root.set("recentExtensionIds", objectMapper.valueToTree(recentExtensionIds));
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

  private static List<String> normalizeIdList(Iterable<String> source) {
    List<String> normalized = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (String extensionId : source) {
      if (extensionId == null) {
        continue;
      }
      String trimmed = extensionId.trim();
      if (trimmed.isBlank() || !seen.add(trimmed)) {
        continue;
      }
      normalized.add(trimmed);
    }
    return List.copyOf(normalized);
  }
}
