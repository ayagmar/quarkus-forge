package dev.ayagmar.quarkusforge.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class CatalogSnapshotCache {
  static final int SCHEMA_VERSION = 1;
  static final Duration DEFAULT_TTL = Duration.ofHours(6);
  static final long DEFAULT_MAX_BYTES = 2L * 1024L * 1024L;
  private static final System.Logger LOGGER =
      System.getLogger(CatalogSnapshotCache.class.getName());

  private final Path cacheFile;
  private final SnapshotPayloadCodec payloadCodec;
  private final Clock clock;
  private final Duration ttl;
  private final long maxBytes;

  public CatalogSnapshotCache(Path cacheFile) {
    this(cacheFile, defaultPayloadCodec(), Clock.systemUTC(), DEFAULT_TTL, DEFAULT_MAX_BYTES);
  }

  CatalogSnapshotCache(
      Path cacheFile, SnapshotPayloadCodec payloadCodec, Clock clock, Duration ttl, long maxBytes) {
    this.cacheFile = Objects.requireNonNull(cacheFile);
    this.payloadCodec = Objects.requireNonNull(payloadCodec);
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

  static SnapshotPayloadCodec defaultPayloadCodec() {
    return JsonSnapshotPayloadCodec.INSTANCE;
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

      CatalogSnapshotPayload payload = payloadCodec.read(cacheFile);
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
    } catch (IOException | ApiContractException exception) {
      LOGGER.log(
          System.Logger.Level.DEBUG,
          "Failed to read catalog snapshot cache from " + cacheFile,
          exception);
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
    return payloadCodec.write(payload);
  }

  interface SnapshotPayloadCodec {
    CatalogSnapshotPayload read(Path file) throws IOException;

    byte[] write(CatalogSnapshotPayload payload) throws IOException;
  }

  private static final class JsonSnapshotPayloadCodec implements SnapshotPayloadCodec {
    private static final JsonSnapshotPayloadCodec INSTANCE = new JsonSnapshotPayloadCodec();

    @Override
    public CatalogSnapshotPayload read(Path file) throws IOException {
      Map<String, Object> root = JsonSupport.parseObject(Files.readString(file));

      Integer schemaVersion = JsonFieldReader.readInt(root, "schemaVersion");
      Long fetchedAtEpochMillis = JsonFieldReader.readLong(root, "fetchedAtEpochMillis");
      Map<String, Object> metadataObject = JsonFieldReader.readObject(root, "metadata");
      List<Object> extensionsArray = JsonFieldReader.readArray(root, "extensions");

      MetadataDto metadata =
          metadataObject == null ? null : ApiPayloadParser.parseMetadataObject(metadataObject);
      List<ExtensionDto> extensions =
          extensionsArray == null ? null : ApiPayloadParser.parseExtensionsArray(extensionsArray);

      return new CatalogSnapshotPayload(
          schemaVersion == null ? -1 : schemaVersion,
          fetchedAtEpochMillis == null ? -1L : fetchedAtEpochMillis,
          metadata,
          extensions);
    }

    @Override
    public byte[] write(CatalogSnapshotPayload payload) throws IOException {
      Map<String, Object> root = new LinkedHashMap<>();
      root.put("schemaVersion", payload.schemaVersion());
      root.put("fetchedAtEpochMillis", payload.fetchedAtEpochMillis());
      root.put("metadata", toMetadataMap(payload.metadata()));
      root.put("extensions", toExtensionsArray(payload.extensions()));
      return JsonSupport.writeBytes(root);
    }

    private static Map<String, Object> toMetadataMap(MetadataDto metadata) {
      if (metadata == null) {
        return null;
      }
      Map<String, Object> root = new LinkedHashMap<>();
      root.put("javaVersions", metadata.javaVersions());
      root.put("buildTools", metadata.buildTools());
      root.put("compatibility", metadata.compatibility());

      List<Object> platformStreams = new ArrayList<>();
      for (PlatformStream stream : metadata.platformStreams()) {
        Map<String, Object> streamObject = new LinkedHashMap<>();
        streamObject.put("key", stream.key());
        streamObject.put("platformVersion", stream.platformVersion());
        streamObject.put("recommended", stream.recommended());
        streamObject.put("javaVersions", stream.javaVersions());
        platformStreams.add(streamObject);
      }
      root.put("platformStreams", platformStreams);
      return root;
    }

    private static List<Object> toExtensionsArray(List<ExtensionDto> extensions) {
      if (extensions == null) {
        return null;
      }
      List<Object> extensionArray = new ArrayList<>();
      for (ExtensionDto extension : extensions) {
        Map<String, Object> extensionObject = new LinkedHashMap<>();
        extensionObject.put("id", extension.id());
        extensionObject.put("name", extension.name());
        extensionObject.put("shortName", extension.shortName());
        extensionObject.put("category", extension.category());
        extensionObject.put("order", extension.order());
        extensionObject.put("description", extension.description());
        extensionArray.add(extensionObject);
      }
      return extensionArray;
    }
  }
}
