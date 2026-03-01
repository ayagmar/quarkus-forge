package dev.ayagmar.quarkusforge.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    return ForgeDataPaths.catalogSnapshotFile();
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
    } catch (IOException | RuntimeException serializationException) {
      return CacheWriteOutcome.writeFailed("failed to serialize cache snapshot");
    }

    if (payload.length > maxBytes) {
      return CacheWriteOutcome.writeRejected(
          "snapshot payload exceeds max size (%d > %d bytes)".formatted(payload.length, maxBytes));
    }

    try {
      AtomicFileStore.writeBytes(cacheFile, payload, "catalog-snapshot-");
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

      CatalogSnapshotPayload payload =
          objectMapper.readValue(cacheFile.toFile(), CatalogSnapshotPayload.class);
      int schemaVersion = payload.schemaVersion();
      if (schemaVersion != SCHEMA_VERSION) {
        return Optional.empty();
      }

      long fetchedAtMillis = payload.fetchedAtEpochMillis();
      if (fetchedAtMillis < 0) {
        return Optional.empty();
      }

      MetadataDto metadata = payload.metadata();
      if (metadata == null) {
        return Optional.empty();
      }
      List<ExtensionDto> extensions = payload.extensions();
      if (extensions == null || extensions.isEmpty()) {
        return Optional.empty();
      }
      extensions = List.copyOf(extensions);

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
    CatalogSnapshotPayload payload =
        new CatalogSnapshotPayload(
            SCHEMA_VERSION, clock.instant().toEpochMilli(), metadata, List.copyOf(extensions));
    return objectMapper.writeValueAsBytes(payload);
  }
}
