package dev.ayagmar.quarkusforge.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CatalogDataServiceTest {
  @TempDir Path tempDir;

  private WireMockServer wireMockServer;

  @BeforeEach
  void setUp() {
    wireMockServer = new WireMockServer(0);
    wireMockServer.start();
    com.github.tomakehurst.wiremock.client.WireMock.configureFor(
        "localhost", wireMockServer.port());
  }

  @AfterEach
  void tearDown() {
    if (wireMockServer != null) {
      wireMockServer.stop();
    }
  }

  @Test
  void offlineLoadUsesCachedSnapshotWhenAvailable() {
    stubCatalogEndpoints();
    Path cacheFile = tempDir.resolve("catalog-snapshot.json");

    CatalogDataService onlineService =
        new CatalogDataService(
            onlineClient(),
            new CatalogSnapshotCache(
                cacheFile,
                ObjectMapperProvider.shared(),
                Clock.fixed(Instant.parse("2026-02-22T00:00:00Z"), ZoneOffset.UTC),
                Duration.ofHours(6),
                2L * 1024L * 1024L));

    CatalogData liveData = onlineService.load().join();
    assertThat(liveData.source()).isEqualTo(CatalogSource.LIVE);

    CatalogDataService offlineService =
        new CatalogDataService(
            offlineClient(),
            new CatalogSnapshotCache(
                cacheFile,
                ObjectMapperProvider.shared(),
                Clock.fixed(Instant.parse("2026-02-22T01:00:00Z"), ZoneOffset.UTC),
                Duration.ofHours(6),
                2L * 1024L * 1024L));

    CatalogData cachedData = offlineService.load().join();
    assertThat(cachedData.source()).isEqualTo(CatalogSource.CACHE);
    assertThat(cachedData.stale()).isFalse();
    assertThat(cachedData.detailMessage()).contains("using cached snapshot");
    assertThat(cachedData.extensions()).isEqualTo(liveData.extensions());
  }

  @Test
  void offlineLoadMarksCacheAsStaleWhenTtlIsExpired() {
    stubCatalogEndpoints();
    Path cacheFile = tempDir.resolve("catalog-snapshot.json");

    CatalogDataService onlineService =
        new CatalogDataService(
            onlineClient(),
            new CatalogSnapshotCache(
                cacheFile,
                ObjectMapperProvider.shared(),
                Clock.fixed(Instant.parse("2026-02-22T00:00:00Z"), ZoneOffset.UTC),
                Duration.ofHours(6),
                2L * 1024L * 1024L));
    onlineService.load().join();

    CatalogDataService offlineService =
        new CatalogDataService(
            offlineClient(),
            new CatalogSnapshotCache(
                cacheFile,
                ObjectMapperProvider.shared(),
                Clock.fixed(Instant.parse("2026-02-22T06:00:01Z"), ZoneOffset.UTC),
                Duration.ofHours(6),
                2L * 1024L * 1024L));

    CatalogData cachedData = offlineService.load().join();
    assertThat(cachedData.source()).isEqualTo(CatalogSource.CACHE);
    assertThat(cachedData.stale()).isTrue();
    assertThat(cachedData.detailMessage()).contains("stale");
  }

  @Test
  void offlineLoadWithoutCacheFailsWithActionableMessage() {
    Path cacheFile = tempDir.resolve("catalog-snapshot.json");
    CatalogDataService offlineService =
        new CatalogDataService(
            offlineClient(),
            new CatalogSnapshotCache(
                cacheFile,
                ObjectMapperProvider.shared(),
                Clock.fixed(Instant.parse("2026-02-22T00:00:00Z"), ZoneOffset.UTC),
                Duration.ofHours(6),
                2L * 1024L * 1024L));

    assertThatThrownBy(() -> offlineService.load().join())
        .hasCauseInstanceOf(ApiClientException.class)
        .cause()
        .hasMessageContaining("no valid cache snapshot found");
  }

  @Test
  void invalidCacheSchemaFallsBackToNoCacheFailure() throws Exception {
    Path cacheFile = tempDir.resolve("catalog-snapshot.json");
    java.nio.file.Files.writeString(
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

    CatalogDataService service =
        new CatalogDataService(
            offlineClient(),
            new CatalogSnapshotCache(
                cacheFile,
                ObjectMapperProvider.shared(),
                Clock.fixed(Instant.parse("2026-02-22T00:00:00Z"), ZoneOffset.UTC),
                Duration.ofHours(6),
                2L * 1024L * 1024L));

    assertThatThrownBy(() -> service.load().join())
        .hasCauseInstanceOf(ApiClientException.class)
        .cause()
        .hasMessageContaining("no valid cache snapshot found");
  }

  @Test
  void oversizedCacheWriteKeepsLiveResultButSkipsPersistence() {
    stubCatalogEndpoints();
    Path cacheFile = tempDir.resolve("catalog-snapshot.json");

    CatalogDataService service =
        new CatalogDataService(
            onlineClient(),
            new CatalogSnapshotCache(
                cacheFile,
                ObjectMapperProvider.shared(),
                Clock.fixed(Instant.parse("2026-02-22T00:00:00Z"), ZoneOffset.UTC),
                Duration.ofHours(6),
                128L));

    CatalogData liveData = service.load().join();

    assertThat(liveData.source()).isEqualTo(CatalogSource.LIVE);
    assertThat(liveData.detailMessage()).contains("cache update skipped");
    assertThat(new CatalogSnapshotCache(cacheFile).read()).isEmpty();
  }

  private void stubCatalogEndpoints() {
    stubFor(
        get(urlEqualTo("/api/extensions"))
            .willReturn(
                okJson(
                    """
                    [
                      {"id":"io.quarkus:quarkus-rest","name":"REST","shortName":"rest"},
                      {"id":"io.quarkus:quarkus-arc","name":"CDI","shortName":"cdi"}
                    ]
                    """)));

    stubFor(
        get(urlEqualTo("/api/metadata"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "javaVersions": ["21", "25"],
                          "buildTools": ["maven", "gradle"],
                          "compatibility": {
                            "maven": ["21", "25"],
                            "gradle": ["25"]
                          }
                        }
                        """)));
  }

  private QuarkusApiClient onlineClient() {
    return newClient(URI.create(wireMockServer.baseUrl()));
  }

  private QuarkusApiClient offlineClient() {
    return newClient(URI.create("http://127.0.0.1:1"));
  }

  private static QuarkusApiClient newClient(URI baseUri) {
    return new QuarkusApiClient(
        HttpClient.newHttpClient(),
        ObjectMapperProvider.shared(),
        baseUri,
        new RetryPolicy(1, Duration.ofMillis(200), Duration.ofMillis(1), 0.0d),
        delay -> CompletableFuture.completedFuture(null),
        Clock.fixed(Instant.parse("2026-02-22T00:00:00Z"), ZoneOffset.UTC),
        () -> 0.5d);
  }
}
