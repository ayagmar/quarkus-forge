package dev.ayagmar.quarkusforge.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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
            new ObjectMapper(),
            clock,
            Duration.ofHours(6),
            2L * 1024L * 1024L);

    CatalogSnapshotCache.CacheWriteOutcome writeOutcome =
        cache.write(sampleMetadata(), sampleExtensions());

    assertThat(writeOutcome.written()).isTrue();
    CatalogSnapshotCache.CachedCatalogSnapshot snapshot = cache.read().orElseThrow();
    assertThat(snapshot.stale()).isFalse();
    assertThat(snapshot.metadata()).isEqualTo(sampleMetadata());
    assertThat(snapshot.extensions()).containsExactlyElementsOf(sampleExtensions());
  }

  @Test
  void readMarksSnapshotAsStaleWhenTtlExpired() {
    Path cacheFile = tempDir.resolve("catalog-snapshot.json");
    CatalogSnapshotCache writer =
        new CatalogSnapshotCache(
            cacheFile,
            new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-02-22T00:00:00Z"), ZoneOffset.UTC),
            Duration.ofHours(6),
            2L * 1024L * 1024L);

    assertThat(writer.write(sampleMetadata(), sampleExtensions()).written()).isTrue();

    CatalogSnapshotCache reader =
        new CatalogSnapshotCache(
            cacheFile,
            new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-02-22T06:00:01Z"), ZoneOffset.UTC),
            Duration.ofHours(6),
            2L * 1024L * 1024L);

    CatalogSnapshotCache.CachedCatalogSnapshot snapshot = reader.read().orElseThrow();
    assertThat(snapshot.stale()).isTrue();
  }

  @Test
  void readMarksSnapshotAsStaleExactlyAtTtlBoundary() {
    Path cacheFile = tempDir.resolve("catalog-snapshot.json");
    CatalogSnapshotCache writer =
        new CatalogSnapshotCache(
            cacheFile,
            new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-02-22T00:00:00Z"), ZoneOffset.UTC),
            Duration.ofHours(6),
            2L * 1024L * 1024L);

    assertThat(writer.write(sampleMetadata(), sampleExtensions()).written()).isTrue();

    CatalogSnapshotCache reader =
        new CatalogSnapshotCache(
            cacheFile,
            new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-02-22T06:00:00Z"), ZoneOffset.UTC),
            Duration.ofHours(6),
            2L * 1024L * 1024L);

    CatalogSnapshotCache.CachedCatalogSnapshot snapshot = reader.read().orElseThrow();
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
            new ObjectMapper(),
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
            new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-02-22T00:00:00Z"), ZoneOffset.UTC),
            Duration.ofHours(6),
            128L);

    CatalogSnapshotCache.CacheWriteOutcome outcome =
        cache.write(sampleMetadata(), sampleExtensions());

    assertThat(outcome.written()).isFalse();
    assertThat(outcome.rejected()).isTrue();
    assertThat(outcome.detail()).contains("exceeds max size");
    assertThat(Files.exists(cacheFile)).isFalse();
    assertThat(cache.read()).isEmpty();
  }

  @Test
  void writeConvertsSerializationRuntimeFailureToWriteFailedOutcome() {
    Path cacheFile = tempDir.resolve("catalog-snapshot.json");
    ObjectMapper failingMapper =
        new ObjectMapper() {
          @Override
          public <T extends com.fasterxml.jackson.databind.JsonNode> T valueToTree(
              Object fromValue) {
            throw new IllegalArgumentException("serializer failed");
          }

          @Override
          public com.fasterxml.jackson.databind.node.ObjectNode createObjectNode() {
            return JsonNodeFactory.instance.objectNode();
          }
        };
    CatalogSnapshotCache cache =
        new CatalogSnapshotCache(
            cacheFile,
            failingMapper,
            Clock.fixed(Instant.parse("2026-02-22T00:00:00Z"), ZoneOffset.UTC),
            Duration.ofHours(6),
            2L * 1024L * 1024L);

    CatalogSnapshotCache.CacheWriteOutcome outcome =
        cache.write(sampleMetadata(), sampleExtensions());

    assertThat(outcome.written()).isFalse();
    assertThat(outcome.rejected()).isFalse();
    assertThat(outcome.detail()).contains("failed to serialize cache snapshot");
    assertThat(Files.exists(cacheFile)).isFalse();
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
}
