package dev.ayagmar.quarkusforge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CatalogSnapshotCacheTest {
  @TempDir Path tempDir;

  @Test
  void writeThenReadReturnsFreshSnapshot() {
    Clock clock = Clock.fixed(Instant.parse("2026-02-22T00:00:00Z"), ZoneOffset.UTC);
    CatalogSnapshotCache cache =
        new CatalogSnapshotCache(
            tempDir.resolve("catalog-snapshot.json"),
            CatalogSnapshotCache.defaultPayloadCodec(),
            clock,
            Duration.ofHours(6),
            2L * 1024L * 1024L);

    CacheWriteOutcome writeOutcome = cache.write(sampleMetadata(), sampleExtensions());

    assertThat(writeOutcome.written()).isTrue();
    CachedCatalogSnapshot snapshot = cache.read().orElseThrow();
    assertThat(snapshot.stale()).isFalse();
    assertThat(snapshot.metadata()).isEqualTo(sampleMetadata());
    assertThat(snapshot.extensions()).containsExactlyElementsOf(sampleExtensions());
  }

  @Test
  void writeThenReadPreservesExtensionDescriptions() {
    Clock clock = Clock.fixed(Instant.parse("2026-02-22T00:00:00Z"), ZoneOffset.UTC);
    CatalogSnapshotCache cache =
        new CatalogSnapshotCache(
            tempDir.resolve("catalog-snapshot.json"),
            CatalogSnapshotCache.defaultPayloadCodec(),
            clock,
            Duration.ofHours(6),
            2L * 1024L * 1024L);
    List<ExtensionDto> extensions =
        List.of(
            new ExtensionDto(
                "io.quarkus:quarkus-rest", "REST", "rest", "Web", 10, "REST endpoint support"));

    assertThat(cache.write(sampleMetadata(), extensions).written()).isTrue();

    CachedCatalogSnapshot snapshot = cache.read().orElseThrow();
    assertThat(snapshot.extensions().getFirst().description()).isEqualTo("REST endpoint support");
  }

  @Test
  void readMarksSnapshotAsStaleWhenTtlExpired() {
    Path cacheFile = tempDir.resolve("catalog-snapshot.json");
    CatalogSnapshotCache writer =
        new CatalogSnapshotCache(
            cacheFile,
            CatalogSnapshotCache.defaultPayloadCodec(),
            Clock.fixed(Instant.parse("2026-02-22T00:00:00Z"), ZoneOffset.UTC),
            Duration.ofHours(6),
            2L * 1024L * 1024L);

    assertThat(writer.write(sampleMetadata(), sampleExtensions()).written()).isTrue();

    CatalogSnapshotCache reader =
        new CatalogSnapshotCache(
            cacheFile,
            CatalogSnapshotCache.defaultPayloadCodec(),
            Clock.fixed(Instant.parse("2026-02-22T06:00:01Z"), ZoneOffset.UTC),
            Duration.ofHours(6),
            2L * 1024L * 1024L);

    CachedCatalogSnapshot snapshot = reader.read().orElseThrow();
    assertThat(snapshot.stale()).isTrue();
  }

  @Test
  void readMarksSnapshotAsStaleExactlyAtTtlBoundary() {
    Path cacheFile = tempDir.resolve("catalog-snapshot.json");
    CatalogSnapshotCache writer =
        new CatalogSnapshotCache(
            cacheFile,
            CatalogSnapshotCache.defaultPayloadCodec(),
            Clock.fixed(Instant.parse("2026-02-22T00:00:00Z"), ZoneOffset.UTC),
            Duration.ofHours(6),
            2L * 1024L * 1024L);

    assertThat(writer.write(sampleMetadata(), sampleExtensions()).written()).isTrue();

    CatalogSnapshotCache reader =
        new CatalogSnapshotCache(
            cacheFile,
            CatalogSnapshotCache.defaultPayloadCodec(),
            Clock.fixed(Instant.parse("2026-02-22T06:00:00Z"), ZoneOffset.UTC),
            Duration.ofHours(6),
            2L * 1024L * 1024L);

    CachedCatalogSnapshot snapshot = reader.read().orElseThrow();
    assertThat(snapshot.stale()).isTrue();
  }

  @Test
  void readRejectsSchemaVersionMismatch() throws Exception {
    Path cacheFile = tempDir.resolve("catalog-snapshot.json");
    Files.writeString(
        cacheFile,
        """
        {
          "schemaVersion": 99,
          "fetchedAtEpochMillis": 1700000000000,
          "metadata": {
            "javaVersions": ["25"],
            "buildTools": ["maven"],
            "compatibility": {"maven": ["25"]}
          },
          "extensions": [{"id":"io.quarkus:quarkus-rest","name":"REST","shortName":"rest"}]
        }
        """);

    CatalogSnapshotCache cache =
        new CatalogSnapshotCache(
            cacheFile,
            CatalogSnapshotCache.defaultPayloadCodec(),
            Clock.systemUTC(),
            Duration.ofHours(6),
            2L * 1024L * 1024L);

    assertThat(cache.read()).isEmpty();
  }

  @Test
  void writeRejectsOversizedPayloadWithoutPersisting() {
    Path cacheFile = tempDir.resolve("catalog-snapshot.json");
    CatalogSnapshotCache cache =
        new CatalogSnapshotCache(
            cacheFile,
            CatalogSnapshotCache.defaultPayloadCodec(),
            Clock.fixed(Instant.parse("2026-02-22T00:00:00Z"), ZoneOffset.UTC),
            Duration.ofHours(6),
            128L);

    CacheWriteOutcome outcome = cache.write(sampleMetadata(), sampleExtensions());

    assertThat(outcome.written()).isFalse();
    assertThat(outcome.rejected()).isTrue();
    assertThat(outcome.detail()).contains("exceeds max size");
    assertThat(Files.exists(cacheFile)).isFalse();
    assertThat(cache.read()).isEmpty();
  }

  @Test
  void writeConvertsSerializationRuntimeFailureToWriteFailedOutcome() {
    Path cacheFile = tempDir.resolve("catalog-snapshot.json");
    CatalogSnapshotCache.SnapshotPayloadCodec failingCodec =
        new CatalogSnapshotCache.SnapshotPayloadCodec() {
          @Override
          public CatalogSnapshotPayload read(Path file) throws java.io.IOException {
            return CatalogSnapshotCache.defaultPayloadCodec().read(file);
          }

          @Override
          public byte[] write(CatalogSnapshotPayload payload) {
            throw new IllegalArgumentException("serializer failed");
          }
        };
    CatalogSnapshotCache cache =
        new CatalogSnapshotCache(
            cacheFile,
            failingCodec,
            Clock.fixed(Instant.parse("2026-02-22T00:00:00Z"), ZoneOffset.UTC),
            Duration.ofHours(6),
            2L * 1024L * 1024L);

    CacheWriteOutcome outcome = cache.write(sampleMetadata(), sampleExtensions());

    assertThat(outcome.written()).isFalse();
    assertThat(outcome.rejected()).isFalse();
    assertThat(outcome.detail()).contains("failed to serialize cache snapshot");
    assertThat(Files.exists(cacheFile)).isFalse();
  }

  @Test
  void readReturnsEmptyWhenCodecReportsMalformedPayload() {
    Path cacheFile = tempDir.resolve("catalog-snapshot.json");
    CatalogSnapshotCache.SnapshotPayloadCodec malformedCodec =
        new CatalogSnapshotCache.SnapshotPayloadCodec() {
          @Override
          public CatalogSnapshotPayload read(Path file) {
            throw new ApiContractException("Malformed JSON payload");
          }

          @Override
          public byte[] write(CatalogSnapshotPayload payload) throws java.io.IOException {
            return CatalogSnapshotCache.defaultPayloadCodec().write(payload);
          }
        };
    CatalogSnapshotCache cache =
        new CatalogSnapshotCache(
            cacheFile, malformedCodec, Clock.systemUTC(), Duration.ofHours(6), 2L * 1024L * 1024L);

    assertThat(cache.write(sampleMetadata(), sampleExtensions()).written()).isTrue();
    assertThat(cache.read()).isEmpty();
  }

  @Test
  void readPropagatesUnexpectedRuntimeFailureFromCodec() {
    Path cacheFile = tempDir.resolve("catalog-snapshot.json");
    CatalogSnapshotCache.SnapshotPayloadCodec failingCodec =
        new CatalogSnapshotCache.SnapshotPayloadCodec() {
          @Override
          public CatalogSnapshotPayload read(Path file) {
            throw new IllegalStateException("codec bug");
          }

          @Override
          public byte[] write(CatalogSnapshotPayload payload) throws java.io.IOException {
            return CatalogSnapshotCache.defaultPayloadCodec().write(payload);
          }
        };
    CatalogSnapshotCache cache =
        new CatalogSnapshotCache(
            cacheFile, failingCodec, Clock.systemUTC(), Duration.ofHours(6), 2L * 1024L * 1024L);

    assertThat(cache.write(sampleMetadata(), sampleExtensions()).written()).isTrue();
    assertThatThrownBy(cache::read)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("codec bug");
  }

  private static MetadataDto sampleMetadata() {
    return new MetadataDto(
        List.of("21", "25"),
        List.of("maven", "gradle"),
        Map.of("maven", List.of("21", "25"), "gradle", List.of("25")));
  }

  private static List<ExtensionDto> sampleExtensions() {
    return List.of(
        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest"),
        new ExtensionDto("io.quarkus:quarkus-arc", "CDI", "cdi"));
  }

  // ── Constructor validation ──────────────────────────────────────────

  @Test
  void constructorRejectsNegativeTtl() {
    assertThatThrownBy(
            () ->
                new CatalogSnapshotCache(
                    tempDir.resolve("cache.json"),
                    CatalogSnapshotCache.defaultPayloadCodec(),
                    Clock.systemUTC(),
                    Duration.ofHours(-1),
                    2L * 1024L * 1024L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ttl must be > 0");
  }

  @Test
  void constructorRejectsZeroTtl() {
    assertThatThrownBy(
            () ->
                new CatalogSnapshotCache(
                    tempDir.resolve("cache.json"),
                    CatalogSnapshotCache.defaultPayloadCodec(),
                    Clock.systemUTC(),
                    Duration.ZERO,
                    2L * 1024L * 1024L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ttl must be > 0");
  }

  @Test
  void constructorRejectsZeroMaxBytes() {
    assertThatThrownBy(
            () ->
                new CatalogSnapshotCache(
                    tempDir.resolve("cache.json"),
                    CatalogSnapshotCache.defaultPayloadCodec(),
                    Clock.systemUTC(),
                    Duration.ofHours(6),
                    0L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxBytes must be > 0");
  }

  // ── write edge cases ──────────────────────────────────────────

  @Test
  void writeRejectsEmptyExtensionsList() {
    CatalogSnapshotCache cache =
        new CatalogSnapshotCache(
            tempDir.resolve("cache.json"),
            CatalogSnapshotCache.defaultPayloadCodec(),
            Clock.systemUTC(),
            Duration.ofHours(6),
            2L * 1024L * 1024L);

    CacheWriteOutcome outcome = cache.write(sampleMetadata(), List.of());

    assertThat(outcome.written()).isFalse();
    assertThat(outcome.rejected()).isTrue();
    assertThat(outcome.detail()).contains("empty catalog");
  }

  // ── read edge cases ──────────────────────────────────────────

  @Test
  void readReturnsEmptyWhenFileDoesNotExist() {
    CatalogSnapshotCache cache =
        new CatalogSnapshotCache(
            tempDir.resolve("nonexistent.json"),
            CatalogSnapshotCache.defaultPayloadCodec(),
            Clock.systemUTC(),
            Duration.ofHours(6),
            2L * 1024L * 1024L);

    assertThat(cache.read()).isEmpty();
  }

  @Test
  void readReturnsEmptyForEmptyFile() throws Exception {
    Path cacheFile = tempDir.resolve("empty.json");
    Files.writeString(cacheFile, "");

    CatalogSnapshotCache cache =
        new CatalogSnapshotCache(
            cacheFile,
            CatalogSnapshotCache.defaultPayloadCodec(),
            Clock.systemUTC(),
            Duration.ofHours(6),
            2L * 1024L * 1024L);

    assertThat(cache.read()).isEmpty();
  }

  @Test
  void readReturnsEmptyWhenFileSizeExceedsMaxBytes() throws Exception {
    Path cacheFile = tempDir.resolve("oversized.json");
    // Write a valid snapshot first
    CatalogSnapshotCache writer =
        new CatalogSnapshotCache(
            cacheFile,
            CatalogSnapshotCache.defaultPayloadCodec(),
            Clock.fixed(Instant.parse("2026-02-22T00:00:00Z"), ZoneOffset.UTC),
            Duration.ofHours(6),
            2L * 1024L * 1024L);
    CacheWriteOutcome writeOutcome = writer.write(sampleMetadata(), sampleExtensions());
    assertThat(writeOutcome.written()).isTrue();
    assertThat(cacheFile).exists();

    // Read with a much smaller maxBytes
    CatalogSnapshotCache reader =
        new CatalogSnapshotCache(
            cacheFile,
            CatalogSnapshotCache.defaultPayloadCodec(),
            Clock.systemUTC(),
            Duration.ofHours(6),
            10L); // much too small

    assertThat(reader.read()).isEmpty();
  }

  @Test
  void readReturnsEmptyForNegativeFetchedAt() throws Exception {
    Path cacheFile = tempDir.resolve("neg-fetched.json");
    Files.writeString(
        cacheFile,
        """
        {
          "schemaVersion": 1,
          "fetchedAtEpochMillis": -1,
          "metadata": {
            "javaVersions": ["25"],
            "buildTools": ["maven"],
            "compatibility": {"maven": ["25"]}
          },
          "extensions": [{"id":"io.quarkus:quarkus-rest","name":"REST","shortName":"rest"}]
        }
        """);

    CatalogSnapshotCache cache =
        new CatalogSnapshotCache(
            cacheFile,
            CatalogSnapshotCache.defaultPayloadCodec(),
            Clock.systemUTC(),
            Duration.ofHours(6),
            2L * 1024L * 1024L);

    assertThat(cache.read()).isEmpty();
  }

  @Test
  void readReturnsEmptyForNullMetadata() throws Exception {
    Path cacheFile = tempDir.resolve("null-metadata.json");
    Files.writeString(
        cacheFile,
        """
        {
          "schemaVersion": 1,
          "fetchedAtEpochMillis": 1700000000000,
          "extensions": [{"id":"io.quarkus:quarkus-rest","name":"REST","shortName":"rest"}]
        }
        """);

    CatalogSnapshotCache cache =
        new CatalogSnapshotCache(
            cacheFile,
            CatalogSnapshotCache.defaultPayloadCodec(),
            Clock.systemUTC(),
            Duration.ofHours(6),
            2L * 1024L * 1024L);

    assertThat(cache.read()).isEmpty();
  }

  @Test
  void readReturnsEmptyForEmptyExtensions() throws Exception {
    Path cacheFile = tempDir.resolve("empty-ext.json");
    Files.writeString(
        cacheFile,
        """
        {
          "schemaVersion": 1,
          "fetchedAtEpochMillis": 1700000000000,
          "metadata": {
            "javaVersions": ["25"],
            "buildTools": ["maven"],
            "compatibility": {"maven": ["25"]}
          },
          "extensions": []
        }
        """);

    CatalogSnapshotCache cache =
        new CatalogSnapshotCache(
            cacheFile,
            CatalogSnapshotCache.defaultPayloadCodec(),
            Clock.systemUTC(),
            Duration.ofHours(6),
            2L * 1024L * 1024L);

    assertThat(cache.read()).isEmpty();
  }

  @Test
  void readReturnsEmptyForCorruptJsonFile() throws Exception {
    Path cacheFile = tempDir.resolve("corrupt.json");
    Files.writeString(cacheFile, "not valid json at all");

    CatalogSnapshotCache cache =
        new CatalogSnapshotCache(
            cacheFile,
            CatalogSnapshotCache.defaultPayloadCodec(),
            Clock.systemUTC(),
            Duration.ofHours(6),
            2L * 1024L * 1024L);

    assertThat(cache.read()).isEmpty();
  }

  @Test
  void readReturnsEmptyForNullExtensions() throws Exception {
    Path cacheFile = tempDir.resolve("null-ext.json");
    Files.writeString(
        cacheFile,
        """
        {
          "schemaVersion": 1,
          "fetchedAtEpochMillis": 1700000000000,
          "metadata": {
            "javaVersions": ["25"],
            "buildTools": ["maven"],
            "compatibility": {"maven": ["25"]}
          }
        }
        """);

    CatalogSnapshotCache cache =
        new CatalogSnapshotCache(
            cacheFile,
            CatalogSnapshotCache.defaultPayloadCodec(),
            Clock.systemUTC(),
            Duration.ofHours(6),
            2L * 1024L * 1024L);

    assertThat(cache.read()).isEmpty();
  }

  @Test
  void readReturnsEmptyForMissingFetchedAt() throws Exception {
    Path cacheFile = tempDir.resolve("missing-fetched.json");
    Files.writeString(
        cacheFile,
        """
        {
          "schemaVersion": 1,
          "metadata": {
            "javaVersions": ["25"],
            "buildTools": ["maven"],
            "compatibility": {"maven": ["25"]}
          },
          "extensions": [{"id":"io.quarkus:quarkus-rest","name":"REST","shortName":"rest"}]
        }
        """);

    CatalogSnapshotCache cache =
        new CatalogSnapshotCache(
            cacheFile,
            CatalogSnapshotCache.defaultPayloadCodec(),
            Clock.systemUTC(),
            Duration.ofHours(6),
            2L * 1024L * 1024L);

    assertThat(cache.read()).isEmpty();
  }

  @Test
  void writeReturnsWriteFailedWhenFilePersistFails() throws Exception {
    // Use an existing directory as the cache file location to force an IO error during persist
    Path dirAsCacheFile = tempDir.resolve("cache-dir");
    Files.createDirectories(dirAsCacheFile);

    CatalogSnapshotCache cache =
        new CatalogSnapshotCache(
            dirAsCacheFile,
            CatalogSnapshotCache.defaultPayloadCodec(),
            Clock.fixed(Instant.parse("2026-02-22T00:00:00Z"), ZoneOffset.UTC),
            Duration.ofHours(6),
            2L * 1024L * 1024L);

    CacheWriteOutcome outcome = cache.write(sampleMetadata(), sampleExtensions());
    assertThat(outcome.written()).isFalse();
    assertThat(outcome.rejected()).isFalse();
    assertThat(outcome.detail()).contains("failed to persist cache snapshot");
  }

  @Test
  void readReturnsEmptyForMissingSchemaVersion() throws Exception {
    Path cacheFile = tempDir.resolve("missing-schema.json");
    Files.writeString(
        cacheFile,
        """
        {
          "fetchedAtEpochMillis": 1700000000000,
          "metadata": {
            "javaVersions": ["25"],
            "buildTools": ["maven"],
            "compatibility": {"maven": ["25"]}
          },
          "extensions": [{"id":"io.quarkus:quarkus-rest","name":"REST","shortName":"rest"}]
        }
        """);

    CatalogSnapshotCache cache =
        new CatalogSnapshotCache(
            cacheFile,
            CatalogSnapshotCache.defaultPayloadCodec(),
            Clock.systemUTC(),
            Duration.ofHours(6),
            2L * 1024L * 1024L);

    assertThat(cache.read()).isEmpty();
  }

  @Test
  void constructorRejectsNullCacheFile() {
    assertThatThrownBy(
            () ->
                new CatalogSnapshotCache(
                    null,
                    CatalogSnapshotCache.defaultPayloadCodec(),
                    Clock.systemUTC(),
                    Duration.ofHours(6),
                    2L * 1024L * 1024L))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void constructorRejectsNegativeMaxBytes() {
    assertThatThrownBy(
            () ->
                new CatalogSnapshotCache(
                    tempDir.resolve("cache.json"),
                    CatalogSnapshotCache.defaultPayloadCodec(),
                    Clock.systemUTC(),
                    Duration.ofHours(6),
                    -1L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxBytes must be > 0");
  }
}
