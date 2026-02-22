package dev.ayagmar.quarkusforge.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QuarkusApiClientTest {
  @TempDir java.nio.file.Path tempDir;
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
  void fetchExtensionsReturnsParsedDtoList() {
    stubFor(
        get(urlEqualTo("/api/extensions"))
            .willReturn(
                okJson(
                    """
                    [
                      {"id":"io.quarkus:quarkus-rest","name":"REST","shortName":"rest"}
                    ]
                    """)));

    QuarkusApiClient client = newClient(RetryPolicy.defaults(), new RecordingSleeper());

    List<ExtensionDto> extensions = client.fetchExtensions().join();

    assertThat(extensions)
        .containsExactly(new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest"));
  }

  @Test
  void fetchExtensionsUsesNameWhenShortNameIsBlank() {
    stubFor(
        get(urlEqualTo("/api/extensions"))
            .willReturn(
                okJson(
                    """
                    [
                      {
                        "id":"io.quarkus:quarkus-rest",
                        "name":"REST",
                        "shortName":" ",
                        "shortId":"resteasy-reactive"
                      }
                    ]
                    """)));

    QuarkusApiClient client = newClient(RetryPolicy.defaults(), new RecordingSleeper());

    List<ExtensionDto> extensions = client.fetchExtensions().join();

    assertThat(extensions)
        .containsExactly(new ExtensionDto("io.quarkus:quarkus-rest", "REST", "REST"));
  }

  @Test
  void fetchExtensionsUsesNameWhenShortNameAndShortIdAreMissing() {
    stubFor(
        get(urlEqualTo("/api/extensions"))
            .willReturn(
                okJson(
                    """
                    [
                      {
                        "id":"io.quarkus:quarkus-rest",
                        "name":"REST"
                      }
                    ]
                    """)));

    QuarkusApiClient client = newClient(RetryPolicy.defaults(), new RecordingSleeper());

    List<ExtensionDto> extensions = client.fetchExtensions().join();

    assertThat(extensions)
        .containsExactly(new ExtensionDto("io.quarkus:quarkus-rest", "REST", "REST"));
  }

  @Test
  void fetchExtensionsIgnoresShortIdWhenShortNameIsBlank() {
    stubFor(
        get(urlEqualTo("/api/extensions"))
            .willReturn(
                okJson(
                    """
                    [
                      {
                        "id":"io.quarkus:quarkus-agroal",
                        "name":"Agroal - DB connection pool",
                        "shortName":" ",
                        "shortId":"ignored"
                      }
                    ]
                    """)));

    QuarkusApiClient client = newClient(RetryPolicy.defaults(), new RecordingSleeper());

    List<ExtensionDto> extensions = client.fetchExtensions().join();

    assertThat(extensions)
        .containsExactly(
            new ExtensionDto(
                "io.quarkus:quarkus-agroal",
                "Agroal - DB connection pool",
                "Agroal - DB connection pool"));
  }

  @Test
  void fetchExtensionsRetries500ThenSucceeds() {
    stubFor(
        get(urlEqualTo("/api/extensions"))
            .inScenario("retry-on-500")
            .whenScenarioStateIs(Scenario.STARTED)
            .willSetStateTo("second")
            .willReturn(aResponse().withStatus(500).withBody("temporary")));

    stubFor(
        get(urlEqualTo("/api/extensions"))
            .inScenario("retry-on-500")
            .whenScenarioStateIs("second")
            .willReturn(
                okJson(
                    """
                    [{"id":"io.quarkus:quarkus-rest","name":"REST","shortName":"rest"}]
                    """)));

    RecordingSleeper sleeper = new RecordingSleeper();
    RetryPolicy retryPolicy = new RetryPolicy(3, Duration.ofMillis(300), Duration.ofMillis(20), 0d);
    QuarkusApiClient client = newClient(retryPolicy, sleeper);

    List<ExtensionDto> extensions = client.fetchExtensions().join();

    assertThat(extensions).hasSize(1);
    verify(2, getRequestedFor(urlEqualTo("/api/extensions")));
    assertThat(sleeper.delays).containsExactly(Duration.ofMillis(20));
  }

  @Test
  void fetchExtensionsRespectsRetryAfterHeaderOn429() {
    stubFor(
        get(urlEqualTo("/api/extensions"))
            .inScenario("retry-after")
            .whenScenarioStateIs(Scenario.STARTED)
            .willSetStateTo("second")
            .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "2")));

    stubFor(
        get(urlEqualTo("/api/extensions"))
            .inScenario("retry-after")
            .whenScenarioStateIs("second")
            .willReturn(
                okJson(
                    """
                    [{"id":"io.quarkus:quarkus-rest","name":"REST","shortName":"rest"}]
                    """)));

    RecordingSleeper sleeper = new RecordingSleeper();
    RetryPolicy retryPolicy = new RetryPolicy(3, Duration.ofMillis(300), Duration.ofMillis(20), 0d);
    QuarkusApiClient client = newClient(retryPolicy, sleeper);

    client.fetchExtensions().join();

    verify(2, getRequestedFor(urlEqualTo("/api/extensions")));
    assertThat(sleeper.delays).containsExactly(Duration.ofSeconds(2));
  }

  @Test
  void fetchExtensionsRespectsHttpDateRetryAfterHeaderOn429() {
    String retryAfter =
        DateTimeFormatter.RFC_1123_DATE_TIME.format(
            ZonedDateTime.ofInstant(Instant.parse("2026-02-21T00:00:03Z"), ZoneOffset.UTC));
    stubFor(
        get(urlEqualTo("/api/extensions"))
            .inScenario("retry-after-http-date")
            .whenScenarioStateIs(Scenario.STARTED)
            .willSetStateTo("second")
            .willReturn(aResponse().withStatus(429).withHeader("Retry-After", retryAfter)));

    stubFor(
        get(urlEqualTo("/api/extensions"))
            .inScenario("retry-after-http-date")
            .whenScenarioStateIs("second")
            .willReturn(
                okJson(
                    """
                    [{"id":"io.quarkus:quarkus-rest","name":"REST","shortName":"rest"}]
                    """)));

    RecordingSleeper sleeper = new RecordingSleeper();
    RetryPolicy retryPolicy = new RetryPolicy(3, Duration.ofMillis(300), Duration.ofMillis(20), 0d);
    QuarkusApiClient client = newClient(retryPolicy, sleeper);

    client.fetchExtensions().join();

    verify(2, getRequestedFor(urlEqualTo("/api/extensions")));
    assertThat(sleeper.delays).containsExactly(Duration.ofSeconds(3));
  }

  @Test
  void fetchExtensionsFailsOnMalformedJson() {
    stubFor(get(urlEqualTo("/api/extensions")).willReturn(okJson("{not-json")));

    QuarkusApiClient client = newClient(RetryPolicy.defaults(), new RecordingSleeper());

    assertThatThrownBy(() -> client.fetchExtensions().join())
        .isInstanceOf(java.util.concurrent.CompletionException.class)
        .hasCauseInstanceOf(ApiContractException.class)
        .cause()
        .hasMessage("Malformed JSON payload");
  }

  @Test
  void fetchExtensionsFailsOnMissingRequiredContractField() {
    stubFor(
        get(urlEqualTo("/api/extensions"))
            .willReturn(okJson("[{\"name\":\"REST\",\"shortName\":\"rest\"}]")));

    QuarkusApiClient client = newClient(RetryPolicy.defaults(), new RecordingSleeper());

    assertThatThrownBy(() -> client.fetchExtensions().join())
        .hasRootCauseInstanceOf(ApiContractException.class)
        .hasRootCauseMessage("Missing required contract field 'id'");
  }

  @Test
  void fetchExtensionsRetriesTimeoutThenFailsAfterExhaustion() {
    stubFor(
        get(urlEqualTo("/api/extensions"))
            .willReturn(aResponse().withStatus(200).withBody("[]").withFixedDelay(250)));

    RecordingSleeper sleeper = new RecordingSleeper();
    RetryPolicy retryPolicy = new RetryPolicy(2, Duration.ofMillis(50), Duration.ofMillis(10), 0d);
    QuarkusApiClient client = newClient(retryPolicy, sleeper);

    Throwable thrown = catchThrowable(() -> client.fetchExtensions().join());
    assertThat(thrown)
        .isInstanceOf(java.util.concurrent.CompletionException.class)
        .hasCauseInstanceOf(ApiClientException.class);
    assertThat(thrown.getCause()).hasMessageContaining("Request failed after 2 attempt(s)");
    assertThat(org.assertj.core.util.Throwables.getRootCause(thrown))
        .isInstanceOfAny(
            java.net.http.HttpTimeoutException.class, java.util.concurrent.TimeoutException.class);

    verify(2, getRequestedFor(urlEqualTo("/api/extensions")));
    assertThat(sleeper.delays).containsExactly(Duration.ofMillis(10));
  }

  @Test
  void fetchMetadataFailsOnNon2xxStatus() {
    stubFor(
        get(urlEqualTo("/api/streams"))
            .willReturn(aResponse().withStatus(400).withBody("bad input")));
    stubOpenApiBuildTools();

    QuarkusApiClient client =
        newClient(
            new RetryPolicy(1, Duration.ofMillis(300), Duration.ofMillis(20), 0d),
            new RecordingSleeper());

    assertThatThrownBy(() -> client.fetchMetadata().join())
        .hasRootCauseInstanceOf(ApiHttpException.class)
        .rootCause()
        .hasMessageContaining("Unexpected HTTP status 400");
  }

  @Test
  void fetchMetadataReturnsParsedDtoFromStreamsAndOpenApi() {
    stubStreamsPayload(List.of(17, 21, 25));
    stubOpenApiBuildTools();

    QuarkusApiClient client = newClient(RetryPolicy.defaults(), new RecordingSleeper());

    MetadataDto metadata = client.fetchMetadata().join();

    assertThat(metadata)
        .isEqualTo(
            new MetadataDto(
                List.of("17", "21", "25"),
                List.of("maven", "gradle", "gradle-kotlin-dsl"),
                Map.of(
                    "maven",
                    List.of("17", "21", "25"),
                    "gradle",
                    List.of("17", "21", "25"),
                    "gradle-kotlin-dsl",
                    List.of("17", "21", "25"))));
  }

  @Test
  void fetchMetadataFallsBackToDefaultBuildToolsWhenOpenApiIsUnavailable() {
    stubStreamsPayload(List.of(17, 21, 25));
    stubFor(get(urlEqualTo("/q/openapi")).willReturn(aResponse().withStatus(404)));

    QuarkusApiClient client = newClient(RetryPolicy.defaults(), new RecordingSleeper());

    MetadataDto metadata = client.fetchMetadata().join();

    assertThat(metadata.buildTools()).containsExactly("maven", "gradle", "gradle-kotlin-dsl");
    assertThat(metadata.compatibility().get("gradle-kotlin-dsl")).containsExactly("17", "21", "25");
  }

  @Test
  void generateProjectZipRequestsZipContentType() {
    stubFor(
        get(urlPathEqualTo("/api/download"))
            .willReturn(aResponse().withStatus(200).withBody("zip-data")));

    QuarkusApiClient client = newClient(RetryPolicy.defaults(), new RecordingSleeper());
    GenerationRequest request =
        new GenerationRequest("com.example", "demo", "1.0.0", "maven", "25", List.of());

    byte[] payload = client.generateProjectZip(request).join();

    assertThat(payload).isEqualTo("zip-data".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    verify(
        getRequestedFor(urlPathEqualTo("/api/download"))
            .withHeader("Accept", equalTo("application/zip, application/octet-stream")));
  }

  @Test
  void downloadProjectZipToFileStreamsPayloadToDestinationPath() throws Exception {
    stubFor(
        get(urlPathEqualTo("/api/download"))
            .willReturn(aResponse().withStatus(200).withBody("zip-data")));

    QuarkusApiClient client = newClient(RetryPolicy.defaults(), new RecordingSleeper());
    GenerationRequest request =
        new GenerationRequest("com.example", "demo", "1.0.0", "maven", "25", List.of());
    java.nio.file.Path destination = tempDir.resolve("download.zip");

    java.nio.file.Path downloaded = client.downloadProjectZipToFile(request, destination).join();

    assertThat(downloaded).isEqualTo(destination);
    assertThat(java.nio.file.Files.readString(destination)).isEqualTo("zip-data");
  }

  @Test
  void downloadProjectZipToFileRetries500ThenSucceedsAndOverwritesDestination() throws Exception {
    stubFor(
        get(urlPathEqualTo("/api/download"))
            .inScenario("download-retry-500")
            .whenScenarioStateIs(Scenario.STARTED)
            .willSetStateTo("second")
            .willReturn(aResponse().withStatus(500).withBody("temporary-error")));

    stubFor(
        get(urlPathEqualTo("/api/download"))
            .inScenario("download-retry-500")
            .whenScenarioStateIs("second")
            .willReturn(aResponse().withStatus(200).withBody("zip-data")));

    RecordingSleeper sleeper = new RecordingSleeper();
    RetryPolicy retryPolicy = new RetryPolicy(3, Duration.ofMillis(300), Duration.ofMillis(20), 0d);
    QuarkusApiClient client = newClient(retryPolicy, sleeper);
    GenerationRequest request =
        new GenerationRequest("com.example", "demo", "1.0.0", "maven", "25", List.of());
    java.nio.file.Path destination = tempDir.resolve("download-retry.zip");

    java.nio.file.Path downloaded = client.downloadProjectZipToFile(request, destination).join();

    assertThat(downloaded).isEqualTo(destination);
    assertThat(java.nio.file.Files.readString(destination)).isEqualTo("zip-data");
    verify(2, getRequestedFor(urlPathEqualTo("/api/download")));
    assertThat(sleeper.delays).containsExactly(Duration.ofMillis(20));
  }

  @Test
  void downloadProjectZipToFileRetriesThenSurfacesHttpErrorWithFinalBodyPersisted()
      throws Exception {
    stubFor(
        get(urlPathEqualTo("/api/download"))
            .inScenario("download-retry-fail")
            .whenScenarioStateIs(Scenario.STARTED)
            .willSetStateTo("second")
            .willReturn(aResponse().withStatus(503).withBody("first-failure")));

    stubFor(
        get(urlPathEqualTo("/api/download"))
            .inScenario("download-retry-fail")
            .whenScenarioStateIs("second")
            .willReturn(aResponse().withStatus(503).withBody("second-failure")));

    RecordingSleeper sleeper = new RecordingSleeper();
    RetryPolicy retryPolicy = new RetryPolicy(2, Duration.ofMillis(300), Duration.ofMillis(20), 0d);
    QuarkusApiClient client = newClient(retryPolicy, sleeper);
    GenerationRequest request =
        new GenerationRequest("com.example", "demo", "1.0.0", "maven", "25", List.of());
    java.nio.file.Path destination = tempDir.resolve("download-retry-fail.zip");

    assertThatThrownBy(() -> client.downloadProjectZipToFile(request, destination).join())
        .hasRootCauseInstanceOf(ApiHttpException.class)
        .rootCause()
        .hasMessageContaining("Unexpected HTTP status 503");
    assertThat(java.nio.file.Files.readString(destination)).isEqualTo("second-failure");
    verify(2, getRequestedFor(urlPathEqualTo("/api/download")));
    assertThat(sleeper.delays).containsExactly(Duration.ofMillis(20));
  }

  private QuarkusApiClient newClient(RetryPolicy retryPolicy, RecordingSleeper sleeper) {
    return new QuarkusApiClient(
        HttpClient.newHttpClient(),
        ObjectMapperProvider.shared(),
        URI.create(wireMockServer.baseUrl()),
        retryPolicy,
        sleeper,
        Clock.fixed(Instant.parse("2026-02-21T00:00:00Z"), ZoneOffset.UTC),
        () -> 0.5d);
  }

  private void stubStreamsPayload(List<Integer> javaVersions) {
    String javaVersionsJson =
        javaVersions.stream()
            .map(String::valueOf)
            .collect(java.util.stream.Collectors.joining(","));
    stubFor(
        get(urlEqualTo("/api/streams"))
            .willReturn(
                okJson(
                    """
                    [
                      {
                        "key": "io.quarkus.platform:3.31",
                        "javaCompatibility": {
                          "versions": [%s],
                          "recommended": %s
                        },
                        "recommended": true,
                        "status": "FINAL"
                      }
                    ]
                    """
                        .formatted(javaVersionsJson, javaVersions.get(javaVersions.size() - 1)))));
  }

  private void stubOpenApiBuildTools() {
    stubFor(
        get(urlEqualTo("/q/openapi"))
            .willReturn(
                okJson(
                    """
                    {
                      "paths": {
                        "/api/download": {
                          "get": {
                            "parameters": [
                              {"name":"g","schema":{"type":"string"}},
                              {"name":"b","schema":{"enum":["MAVEN","GRADLE","GRADLE_KOTLIN_DSL"]}}
                            ]
                          }
                        }
                      }
                    }
                    """)));
  }

  private static final class RecordingSleeper implements AsyncSleeper {
    private final List<Duration> delays = new ArrayList<>();

    @Override
    public CompletableFuture<Void> sleep(Duration delay) {
      delays.add(delay);
      return CompletableFuture.completedFuture(null);
    }
  }
}
