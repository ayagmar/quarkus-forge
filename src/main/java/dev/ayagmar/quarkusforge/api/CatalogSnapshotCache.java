package dev.ayagmar.quarkusforge.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class CatalogSnapshotCache {
  static final int SCHEMA_VERSION = 1;
  static final Duration DEFAULT_TTL = Duration.ofHours(6);
  static final long DEFAULT_MAX_BYTES = 2L * 1024L * 1024L;

  private final Path cacheFile;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final Duration ttl;
  private final long maxBytes;

  public CatalogSnapshotCache(Path cacheFile) {
    this(
        cacheFile,
        ObjectMapperProvider.shared(),
        Clock.systemUTC(),
        DEFAULT_TTL,
        DEFAULT_MAX_BYTES);
  }

  CatalogSnapshotCache(
      Path cacheFile, ObjectMapper objectMapper, Clock clock, Duration ttl, long maxBytes) {
    this.cacheFile = Objects.requireNonNull(cacheFile);
    this.objectMapper = Objects.requireNonNull(objectMapper);
    this.clock = Objects.requireNonNull(clock);
    this.ttl = Objects.requireNonNull(ttl);
    this.maxBytes = maxBytes;

    if (ttl.isNegative() || ttl.isZero()) {
      throw new IllegalArgumentException("ttl must be > 0");
    }
    if (maxBytes <= 0L) {
      throw new IllegalArgumentException("maxBytes must be > 0");
    }
  }

  public static Path defaultCacheFile() {
    Path home = Path.of(System.getProperty("user.home", "."));
    return home.resolve(".quarkus-forge").resolve("catalog-snapshot.json");
  }

  public CacheWriteOutcome write(MetadataDto metadata, List<ExtensionDto> extensions) {
    Objects.requireNonNull(metadata);
    Objects.requireNonNull(extensions);

    if (extensions.isEmpty()) {
      return CacheWriteOutcome.writeRejected("empty catalog");
    }

    byte[] payload;
    try {
      payload = toPayload(metadata, extensions);
    } catch (IOException ioException) {
      return CacheWriteOutcome.writeFailed("failed to serialize cache snapshot");
    }

    if (payload.length > maxBytes) {
      return CacheWriteOutcome.writeRejected(
          "snapshot payload exceeds max size (%d > %d bytes)".formatted(payload.length, maxBytes));
    }

    try {
      Path parent = resolvedParentDirectory();
      Files.createDirectories(parent);
      Path tempFile = Files.createTempFile(parent, "catalog-snapshot-", ".tmp");
      try {
        Files.write(tempFile, payload, StandardOpenOption.TRUNCATE_EXISTING);
        moveAtomicallyWithFallback(tempFile, cacheFile);
      } finally {
        Files.deleteIfExists(tempFile);
      }
      return CacheWriteOutcome.writeSucceeded();
    } catch (IOException ioException) {
      return CacheWriteOutcome.writeFailed("failed to persist cache snapshot");
    }
  }

  public Optional<CachedCatalogSnapshot> read() {
    if (!Files.isRegularFile(cacheFile)) {
      return Optional.empty();
    }

    try {
      long size = Files.size(cacheFile);
      if (size <= 0 || size > maxBytes) {
        return Optional.empty();
      }

      JsonNode root = objectMapper.readTree(cacheFile.toFile());
      if (!root.isObject()) {
        return Optional.empty();
      }

      int schemaVersion = requiredInt(root, "schemaVersion");
      if (schemaVersion != SCHEMA_VERSION) {
        return Optional.empty();
      }

      long fetchedAtMillis = requiredLong(root, "fetchedAtEpochMillis");
      if (fetchedAtMillis < 0) {
        return Optional.empty();
      }

      JsonNode metadataNode = root.get("metadata");
      JsonNode extensionsNode = root.get("extensions");
      if (metadataNode == null || !metadataNode.isObject()) {
        return Optional.empty();
      }
      if (extensionsNode == null || !extensionsNode.isArray()) {
        return Optional.empty();
      }

      MetadataDto metadata =
          QuarkusApiClient.parseMetadataPayload(metadataNode.toString(), objectMapper);
      List<ExtensionDto> extensions =
          QuarkusApiClient.parseExtensionsPayload(extensionsNode.toString(), objectMapper);
      if (extensions.isEmpty()) {
        return Optional.empty();
      }

      Instant fetchedAt = Instant.ofEpochMilli(fetchedAtMillis);
      boolean stale = isStale(fetchedAt);
      return Optional.of(new CachedCatalogSnapshot(metadata, extensions, fetchedAt, stale));
    } catch (IOException | RuntimeException ignored) {
      return Optional.empty();
    }
  }

  private boolean isStale(Instant fetchedAt) {
    Instant staleAt = fetchedAt.plus(ttl);
    return !staleAt.isAfter(clock.instant());
  }

  private byte[] toPayload(MetadataDto metadata, List<ExtensionDto> extensions) throws IOException {
    ObjectNode root = objectMapper.createObjectNode();
    root.put("schemaVersion", SCHEMA_VERSION);
    root.put("fetchedAtEpochMillis", clock.instant().toEpochMilli());
    root.set("metadata", objectMapper.valueToTree(metadata));
    root.set("extensions", objectMapper.valueToTree(extensions));
    return objectMapper.writeValueAsBytes(root);
  }

  private static int requiredInt(JsonNode root, String fieldName) {
    JsonNode node = root.get(fieldName);
    if (node == null || !node.isInt()) {
      throw new ApiContractException("cache field '%s' must be an int".formatted(fieldName));
    }
    return node.intValue();
  }

  private static long requiredLong(JsonNode root, String fieldName) {
    JsonNode node = root.get(fieldName);
    if (node == null || !node.canConvertToLong()) {
      throw new ApiContractException("cache field '%s' must be a long".formatted(fieldName));
    }
    return node.longValue();
  }

  private static void moveAtomicallyWithFallback(Path source, Path target) throws IOException {
    try {
      Files.move(
          source,
          target,
          java.nio.file.StandardCopyOption.REPLACE_EXISTING,
          java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException unsupportedException) {
      Files.move(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private Path resolvedParentDirectory() throws IOException {
    Path normalized = cacheFile.toAbsolutePath().normalize();
    Path parent = normalized.getParent();
    if (parent == null) {
      throw new IOException("Cache file path has no parent directory: " + cacheFile);
    }
    return parent;
  }

  public record CachedCatalogSnapshot(
      MetadataDto metadata, List<ExtensionDto> extensions, Instant fetchedAt, boolean stale) {
    public CachedCatalogSnapshot {
      metadata = Objects.requireNonNull(metadata);
      extensions = List.copyOf(Objects.requireNonNull(extensions));
      fetchedAt = Objects.requireNonNull(fetchedAt);
      if (extensions.isEmpty()) {
        throw new IllegalArgumentException("cached extensions must not be empty");
      }
    }
  }

  public record CacheWriteOutcome(boolean written, boolean rejected, String detail) {
    public CacheWriteOutcome {
      detail = detail == null ? "" : detail.strip();
    }

    static CacheWriteOutcome writeSucceeded() {
      return new CacheWriteOutcome(true, false, "");
    }

    static CacheWriteOutcome writeRejected(String detail) {
      return new CacheWriteOutcome(false, true, detail);
    }

    static CacheWriteOutcome writeFailed(String detail) {
      return new CacheWriteOutcome(false, false, detail);
    }
  }
}
