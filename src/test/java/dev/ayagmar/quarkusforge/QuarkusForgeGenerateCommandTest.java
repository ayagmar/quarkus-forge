package dev.ayagmar.quarkusforge;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.ayagmar.quarkusforge.ui.ExtensionFavoritesStore;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QuarkusForgeGenerateCommandTest {
  private static final String HEADLESS_CATALOG_TIMEOUT_PROPERTY =
      "quarkus.forge.headless.catalog-timeout-ms";
  private static final String HEADLESS_GENERATION_TIMEOUT_PROPERTY =
      "quarkus.forge.headless.generation-timeout-ms";

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
  void headlessGenerateCreatesProjectWithMultipleExtensions() throws Exception {
    stubCatalogEndpoints();
    stubDownloadEndpoint("headless-app");
    RuntimeConfig runtimeConfig = runtimeConfig(URI.create(wireMockServer.baseUrl()));
    Path outputDir = tempDir.resolve("output");

    CliCommandTestSupport.CommandResult result =
        runCommand(
            runtimeConfig,
            "generate",
            "--group-id",
            "com.example",
            "--artifact-id",
            "headless-app",
            "--output-dir",
            outputDir.toString(),
            "--extension",
            "io.quarkus:quarkus-rest",
            "--extension",
            "io.quarkus:quarkus-arc");

    assertThat(result.exitCode()).isZero();
    Path generatedProject = outputDir.resolve("headless-app");
    assertThat(generatedProject.resolve("pom.xml")).exists();
    assertThat(result.standardOut()).contains("Generation succeeded");
    verifyGenerateRequestFor(
        "headless-app", List.of("io.quarkus:quarkus-rest", "io.quarkus:quarkus-arc"));
  }

  @Test
  void headlessGeneratePassesPlatformStreamWhenProvided() throws Exception {
    stubCatalogEndpoints();
    stubDownloadEndpoint("headless-app");
    RuntimeConfig runtimeConfig = runtimeConfig(URI.create(wireMockServer.baseUrl()));
    Path outputDir = tempDir.resolve("output-platform");

    CliCommandTestSupport.CommandResult result =
        runCommand(
            runtimeConfig,
            "generate",
            "--group-id",
            "com.example",
            "--artifact-id",
            "headless-app",
            "--output-dir",
            outputDir.toString(),
            "--platform-stream",
            "io.quarkus.platform:3.31");

    assertThat(result.exitCode()).isZero();
    wireMockServer.verify(
        getRequestedFor(urlPathEqualTo("/api/download"))
            .withQueryParam("S", equalTo("io.quarkus.platform:3.31")));
  }

  @Test
  void headlessGenerateUsesRecommendedPlatformStreamWhenOptionIsOmitted() throws Exception {
    stubCatalogEndpoints();
    stubDownloadEndpoint("headless-app");
    RuntimeConfig runtimeConfig = runtimeConfig(URI.create(wireMockServer.baseUrl()));
    Path outputDir = tempDir.resolve("output-default-stream");

    CliCommandTestSupport.CommandResult result =
        runCommand(
            runtimeConfig,
            "generate",
            "--group-id",
            "com.example",
            "--artifact-id",
            "headless-app",
            "--output-dir",
            outputDir.toString());

    assertThat(result.exitCode()).isZero();
    wireMockServer.verify(
        getRequestedFor(urlPathEqualTo("/api/download"))
            .withQueryParam("S", equalTo("io.quarkus.platform:3.31")));
  }

  @Test
  void invalidPlatformStreamBlocksBeforeDownloadRequest() {
    stubCatalogEndpoints();
    RuntimeConfig runtimeConfig = runtimeConfig(URI.create(wireMockServer.baseUrl()));

    CliCommandTestSupport.CommandResult result =
        runCommand(
            runtimeConfig,
            "generate",
            "--group-id",
            "com.example",
            "--artifact-id",
            "headless-app",
            "--platform-stream",
            "io.quarkus.platform:does-not-exist");

    assertThat(result.exitCode()).isEqualTo(QuarkusForgeCli.EXIT_CODE_VALIDATION);
    assertThat(result.standardError()).contains("unsupported platform stream");
    assertThatCode(() -> wireMockServer.verify(0, getRequestedFor(urlPathEqualTo("/api/download"))))
        .doesNotThrowAnyException();
  }

  @Test
  void invalidExtensionIdReturnsValidationExitCode() {
    stubCatalogEndpoints();
    RuntimeConfig runtimeConfig = runtimeConfig(URI.create(wireMockServer.baseUrl()));

    CliCommandTestSupport.CommandResult result =
        runCommand(
            runtimeConfig,
            "generate",
            "--dry-run",
            "--group-id",
            "com.example",
            "--artifact-id",
            "headless-app",
            "--extension",
            "io.quarkus:quarkus-not-real");

    assertThat(result.exitCode()).isEqualTo(QuarkusForgeCli.EXIT_CODE_VALIDATION);
    assertThat(result.standardError()).contains("unknown extension id");
    assertThatCode(() -> wireMockServer.verify(0, getRequestedFor(urlPathEqualTo("/api/download"))))
        .doesNotThrowAnyException();
  }

  @Test
  void networkFailureReturnsNetworkExitCode() {
    RuntimeConfig runtimeConfig = runtimeConfig(URI.create("http://127.0.0.1:1"));

    CliCommandTestSupport.CommandResult result =
        runCommand(
            runtimeConfig,
            "generate",
            "--dry-run",
            "--group-id",
            "com.example",
            "--artifact-id",
            "headless-app");

    assertThat(result.exitCode()).isEqualTo(QuarkusForgeCli.EXIT_CODE_NETWORK);
    assertThat(result.standardError()).contains("Failed to load extension catalog");
  }

  @Test
  void verboseDryRunEmitsGenerateLifecycleDiagnostics() {
    stubCatalogEndpoints();
    RuntimeConfig runtimeConfig = runtimeConfig(URI.create(wireMockServer.baseUrl()));

    CliCommandTestSupport.CommandResult result =
        runCommand(
            runtimeConfig,
            "--verbose",
            "generate",
            "--dry-run",
            "--group-id",
            "com.example",
            "--artifact-id",
            "headless-app");

    assertThat(result.exitCode()).isZero();
    assertThat(result.standardError()).contains("\"event\":\"generate.start\"");
    assertThat(result.standardError()).contains("\"event\":\"catalog.load.success\"");
    assertThat(result.standardError()).contains("\"event\":\"generate.dry-run.validated\"");
  }

  @Test
  void verboseNetworkFailureEmitsCatalogLoadFailureDiagnostics() {
    RuntimeConfig runtimeConfig = runtimeConfig(URI.create("http://127.0.0.1:1"));

    CliCommandTestSupport.CommandResult result =
        runCommand(
            runtimeConfig,
            "--verbose",
            "generate",
            "--dry-run",
            "--group-id",
            "com.example",
            "--artifact-id",
            "headless-app");

    assertThat(result.exitCode()).isEqualTo(QuarkusForgeCli.EXIT_CODE_NETWORK);
    assertThat(result.standardError()).contains("\"event\":\"catalog.load.failure\"");
    assertThat(result.standardError()).contains("\"causeType\":\"ApiClientException\"");
  }

  @Test
  void outputConflictReturnsArchiveExitCode() throws Exception {
    stubCatalogEndpoints();
    stubDownloadEndpoint("headless-app");
    RuntimeConfig runtimeConfig = runtimeConfig(URI.create(wireMockServer.baseUrl()));
    Path outputDir = tempDir.resolve("output");
    Files.createDirectories(outputDir.resolve("headless-app"));

    CliCommandTestSupport.CommandResult result =
        runCommand(
            runtimeConfig,
            "generate",
            "--group-id",
            "com.example",
            "--artifact-id",
            "headless-app",
            "--output-dir",
            outputDir.toString());

    assertThat(result.exitCode()).isEqualTo(QuarkusForgeCli.EXIT_CODE_ARCHIVE);
    assertThat(result.standardError()).contains("Output directory already exists");
  }

  @Test
  void downloadHttpFailureReturnsNetworkExitCode() {
    stubCatalogEndpoints();
    stubFor(get(urlPathEqualTo("/api/download")).willReturn(aResponse().withStatus(503)));
    RuntimeConfig runtimeConfig = runtimeConfig(URI.create(wireMockServer.baseUrl()));

    CliCommandTestSupport.CommandResult result =
        runCommand(
            runtimeConfig,
            "generate",
            "--group-id",
            "com.example",
            "--artifact-id",
            "headless-app");

    assertThat(result.exitCode()).isEqualTo(QuarkusForgeCli.EXIT_CODE_NETWORK);
    assertThat(result.standardError()).contains("Quarkus API request failed (HTTP 503)");
  }

  @Test
  void catalogLoadTimeoutReturnsNetworkExitCode() {
    stubFor(
        get(urlPathEqualTo("/api/extensions"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withFixedDelay(500)
                    .withHeader("Content-Type", "application/json")
                    .withBody("[]")));
    stubFor(
        get(urlPathEqualTo("/api/streams"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        [
                          {
                            "key":"io.quarkus.platform:3.31",
                            "javaCompatibility": {
                              "versions":[17,21,25],
                              "recommended":25
                            },
                            "recommended":true,
                            "status":"FINAL"
                          }
                        ]
                        """)));
    stubFor(
        get(urlPathEqualTo("/q/openapi"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "paths": {
                            "/api/download": {
                              "get": {
                                "parameters": [
                                  {"name":"b","schema":{"enum":["MAVEN"]}}
                                ]
                              }
                            }
                          }
                        }
                        """)));
    RuntimeConfig runtimeConfig = runtimeConfig(URI.create(wireMockServer.baseUrl()));

    CliCommandTestSupport.CommandResult result =
        withSystemProperty(
            HEADLESS_CATALOG_TIMEOUT_PROPERTY,
            "50",
            () ->
                runCommand(
                    runtimeConfig,
                    "generate",
                    "--dry-run",
                    "--group-id",
                    "com.example",
                    "--artifact-id",
                    "headless-app"));

    assertThat(result.exitCode()).isEqualTo(QuarkusForgeCli.EXIT_CODE_NETWORK);
    assertThat(result.standardError()).contains("timed out");
  }

  @Test
  void generationTimeoutReturnsNetworkExitCode() {
    stubCatalogEndpoints();
    stubFor(
        get(urlPathEqualTo("/api/download"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withFixedDelay(500)
                    .withHeader("Content-Type", "application/zip")
                    .withBody(new byte[] {80, 75, 3, 4})));
    RuntimeConfig runtimeConfig = runtimeConfig(URI.create(wireMockServer.baseUrl()));

    CliCommandTestSupport.CommandResult result =
        withSystemProperty(
            HEADLESS_GENERATION_TIMEOUT_PROPERTY,
            "50",
            () ->
                runCommand(
                    runtimeConfig,
                    "generate",
                    "--group-id",
                    "com.example",
                    "--artifact-id",
                    "headless-app"));

    assertThat(result.exitCode()).isEqualTo(QuarkusForgeCli.EXIT_CODE_NETWORK);
    assertThat(result.standardError()).contains("timed out");
  }

  @Test
  void dryRunSupportsBuiltInAndFavoritesPresetsWithoutGeneratingFiles() throws Exception {
    stubCatalogEndpoints();
    RuntimeConfig runtimeConfig = runtimeConfig(URI.create(wireMockServer.baseUrl()));
    ExtensionFavoritesStore.fileBacked(runtimeConfig.favoritesFile())
        .saveFavoriteExtensionIds(Set.of("io.quarkus:quarkus-jdbc-postgresql"));
    Path outputDir = tempDir.resolve("dry-run-output");

    CliCommandTestSupport.CommandResult result =
        runCommand(
            runtimeConfig,
            "generate",
            "--dry-run",
            "--group-id",
            "com.example",
            "--artifact-id",
            "headless-app",
            "--output-dir",
            outputDir.toString(),
            "--preset",
            "web",
            "--preset",
            "favorites",
            "--extension",
            "io.quarkus:quarkus-hibernate-orm-panache");

    assertThat(result.exitCode()).isZero();
    assertThat(result.standardOut()).contains("Dry-run validated successfully");
    assertThat(result.standardOut())
        .contains("io.quarkus:quarkus-rest")
        .contains("io.quarkus:quarkus-arc")
        .contains("io.quarkus:quarkus-jdbc-postgresql")
        .contains("io.quarkus:quarkus-hibernate-orm-panache");
    assertThat(outputDir.resolve("headless-app")).doesNotExist();
    assertThatCode(() -> wireMockServer.verify(0, getRequestedFor(urlPathEqualTo("/api/download"))))
        .doesNotThrowAnyException();
  }

  @Test
  void dryRunAndGenerateShareExtensionResolutionOrder() throws Exception {
    stubCatalogEndpoints();
    stubDownloadEndpoint("headless-app");
    RuntimeConfig runtimeConfig = runtimeConfig(URI.create(wireMockServer.baseUrl()));
    ExtensionFavoritesStore.fileBacked(runtimeConfig.favoritesFile())
        .saveFavoriteExtensionIds(Set.of("io.quarkus:quarkus-jdbc-postgresql"));
    Path outputDir = tempDir.resolve("parity-output");

    CliCommandTestSupport.CommandResult dryRunResult =
        runCommand(
            runtimeConfig,
            "generate",
            "--dry-run",
            "--group-id",
            "com.example",
            "--artifact-id",
            "headless-app",
            "--output-dir",
            outputDir.toString(),
            "--preset",
            "web",
            "--preset",
            "favorites",
            "--extension",
            "io.quarkus:quarkus-hibernate-orm-panache");

    assertThat(dryRunResult.exitCode()).isZero();
    assertThat(dryRunResult.standardOut())
        .contains(
            "[io.quarkus:quarkus-rest, io.quarkus:quarkus-arc,"
                + " io.quarkus:quarkus-jdbc-postgresql,"
                + " io.quarkus:quarkus-hibernate-orm-panache]");

    CliCommandTestSupport.CommandResult generateResult =
        runCommand(
            runtimeConfig,
            "generate",
            "--group-id",
            "com.example",
            "--artifact-id",
            "headless-app",
            "--output-dir",
            outputDir.toString(),
            "--preset",
            "web",
            "--preset",
            "favorites",
            "--extension",
            "io.quarkus:quarkus-hibernate-orm-panache");

    assertThat(generateResult.exitCode()).isZero();
    verifyGenerateRequestFor(
        "headless-app",
        List.of(
            "io.quarkus:quarkus-rest",
            "io.quarkus:quarkus-arc",
            "io.quarkus:quarkus-jdbc-postgresql",
            "io.quarkus:quarkus-hibernate-orm-panache"));
  }

  @Test
  void rootDryRunFlagBeforeGenerateSubcommandStillPreventsProjectGeneration() throws Exception {
    stubCatalogEndpoints();
    RuntimeConfig runtimeConfig = runtimeConfig(URI.create(wireMockServer.baseUrl()));
    Path outputDir = tempDir.resolve("root-dry-run-output");

    CliCommandTestSupport.CommandResult result =
        runCommand(
            runtimeConfig,
            "--dry-run",
            "generate",
            "--group-id",
            "com.example",
            "--artifact-id",
            "headless-app",
            "--output-dir",
            outputDir.toString(),
            "--extension",
            "io.quarkus:quarkus-rest");

    assertThat(result.exitCode()).isZero();
    assertThat(result.standardOut()).contains("Dry-run validated successfully");
    assertThat(outputDir.resolve("headless-app")).doesNotExist();
    assertThatCode(() -> wireMockServer.verify(0, getRequestedFor(urlPathEqualTo("/api/download"))))
        .doesNotThrowAnyException();
  }

  @Test
  void dryRunCanLoadRecipeAndWriteRecipeAndLock() throws Exception {
    stubCatalogEndpoints();
    RuntimeConfig runtimeConfig = runtimeConfig(URI.create(wireMockServer.baseUrl()));
    Path recipePath = tempDir.resolve("Forgefile");
    Path writtenRecipe = tempDir.resolve("written-forgefile.json");
    Path writtenLock = tempDir.resolve("forge.lock");
    Files.writeString(
        recipePath,
        """
        {
          "groupId": "com.example",
          "artifactId": "recipe-app",
          "version": "1.0.0-SNAPSHOT",
          "outputDirectory": ".",
          "buildTool": "maven",
          "javaVersion": "25",
          "presets": ["web"],
          "extensions": ["io.quarkus:quarkus-hibernate-orm-panache"]
        }
        """);

    CliCommandTestSupport.CommandResult result =
        runCommand(
            runtimeConfig,
            "generate",
            "--dry-run",
            "--recipe",
            recipePath.toString(),
            "--write-recipe",
            writtenRecipe.toString(),
            "--write-lock",
            writtenLock.toString());

    assertThat(result.exitCode()).isZero();
    assertThat(Files.readString(writtenRecipe))
        .contains("\"artifactId\" : \"recipe-app\"")
        .contains("\"presets\" : [ \"web\" ]");
    assertThat(Files.readString(writtenLock))
        .contains("\"javaVersion\" : \"25\"")
        .contains("io.quarkus:quarkus-rest")
        .contains("io.quarkus:quarkus-hibernate-orm-panache");
  }

  @Test
  void lockDriftBlocksUntilRefreshLockIsProvided() throws Exception {
    stubCatalogEndpoints();
    RuntimeConfig runtimeConfig = runtimeConfig(URI.create(wireMockServer.baseUrl()));
    Path lockPath = tempDir.resolve("forge.lock");
    Files.writeString(
        lockPath,
        """
        {
          "platformStream": "io.quarkus.platform:3.31",
          "buildTool": "maven",
          "javaVersion": "21",
          "presets": ["web"],
          "extensions": ["io.quarkus:quarkus-rest", "io.quarkus:quarkus-arc"]
        }
        """);

    CliCommandTestSupport.CommandResult driftResult =
        runCommand(
            runtimeConfig,
            "generate",
            "--dry-run",
            "--group-id",
            "com.example",
            "--artifact-id",
            "headless-app",
            "--preset",
            "web",
            "--lock",
            lockPath.toString());

    assertThat(driftResult.exitCode()).isEqualTo(QuarkusForgeCli.EXIT_CODE_VALIDATION);
    assertThat(driftResult.standardError()).contains("javaVersion drift");

    CliCommandTestSupport.CommandResult refreshResult =
        runCommand(
            runtimeConfig,
            "generate",
            "--dry-run",
            "--group-id",
            "com.example",
            "--artifact-id",
            "headless-app",
            "--preset",
            "web",
            "--lock",
            lockPath.toString(),
            "--refresh-lock");

    assertThat(refreshResult.exitCode()).isZero();
    assertThat(Files.readString(lockPath)).contains("\"javaVersion\" : \"25\"");
  }

  private RuntimeConfig runtimeConfig(URI baseUri) {
    return CliCommandTestSupport.runtimeConfig(tempDir, baseUri);
  }

  private CliCommandTestSupport.CommandResult runCommand(
      RuntimeConfig runtimeConfig, String... args) {
    return CliCommandTestSupport.runCommand(runtimeConfig, args);
  }

  private void stubCatalogEndpoints() {
    stubFor(
        get(urlPathEqualTo("/api/extensions"))
            .willReturn(
                okJson(
                    """
                    [
                      {"id":"io.quarkus:quarkus-rest","name":"REST","shortName":"rest"},
                      {"id":"io.quarkus:quarkus-arc","name":"CDI","shortName":"cdi"},
                      {"id":"io.quarkus:quarkus-jdbc-postgresql","name":"JDBC PostgreSQL","shortName":"jdbc-postgresql"},
                      {"id":"io.quarkus:quarkus-hibernate-orm-panache","name":"Hibernate ORM Panache","shortName":"hibernate-orm-panache"},
                      {"id":"io.quarkus:quarkus-messaging","name":"Messaging","shortName":"messaging"},
                      {"id":"io.quarkus:quarkus-smallrye-health","name":"SmallRye Health","shortName":"health"}
                    ]
                    """)));
    stubFor(
        get(urlPathEqualTo("/api/presets"))
            .willReturn(
                okJson(
                    """
                    [
                      {"key":"web","extensions":["io.quarkus:quarkus-rest","io.quarkus:quarkus-arc"]},
                      {"key":"data","extensions":["io.quarkus:quarkus-hibernate-orm-panache","io.quarkus:quarkus-jdbc-postgresql"]},
                      {"key":"messaging","extensions":["io.quarkus:quarkus-messaging","io.quarkus:quarkus-smallrye-health"]}
                    ]
                    """)));
    stubFor(
        get(urlPathEqualTo("/api/presets/stream/io.quarkus.platform%3A3.31"))
            .willReturn(
                okJson(
                    """
                    [
                      {"key":"web","extensions":["io.quarkus:quarkus-rest","io.quarkus:quarkus-arc"]},
                      {"key":"data","extensions":["io.quarkus:quarkus-hibernate-orm-panache","io.quarkus:quarkus-jdbc-postgresql"]},
                      {"key":"messaging","extensions":["io.quarkus:quarkus-messaging","io.quarkus:quarkus-smallrye-health"]}
                    ]
                    """)));
    CliCommandTestSupport.stubLiveMetadataWithAllBuildTools();
  }

  private void stubDownloadEndpoint(String artifactId) throws Exception {
    stubFor(
        get(urlPathEqualTo("/api/download"))
            .willReturn(aResponse().withStatus(200).withBody(generatedZipPayload(artifactId))));
  }

  private void verifyGenerateRequestFor(String artifactId, List<String> expectedExtensions) {
    wireMockServer.verify(
        getRequestedFor(urlPathEqualTo("/api/download"))
            .withQueryParam("g", equalTo("com.example"))
            .withQueryParam("a", equalTo(artifactId))
            .withQueryParam("v", equalTo("1.0.0-SNAPSHOT"))
            .withQueryParam("b", equalTo("MAVEN"))
            .withQueryParam("j", equalTo("25")));

    List<String> requestedExtensions =
        wireMockServer.findAll(getRequestedFor(urlPathEqualTo("/api/download"))).stream()
            .filter(request -> "/api/download".equals(request.getUrl().split("\\?")[0]))
            .findFirst()
            .orElseThrow()
            .queryParameter("e")
            .values();
    assertThat(requestedExtensions).containsExactlyElementsOf(expectedExtensions);
  }

  private static byte[] generatedZipPayload(String artifactId) throws Exception {
    Map<String, byte[]> entries =
        Map.of(
            artifactId + "/pom.xml", "<project/>".getBytes(StandardCharsets.UTF_8),
            artifactId + "/README.md", "# generated".getBytes(StandardCharsets.UTF_8));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
      for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
        zipOutputStream.putNextEntry(new ZipEntry(entry.getKey()));
        zipOutputStream.write(entry.getValue());
        zipOutputStream.closeEntry();
      }
    }
    return outputStream.toByteArray();
  }

  private static CliCommandTestSupport.CommandResult withSystemProperty(
      String key,
      String value,
      java.util.function.Supplier<CliCommandTestSupport.CommandResult> supplier) {
    String previous = System.getProperty(key);
    try {
      System.setProperty(key, value);
      return supplier.get();
    } finally {
      if (previous == null) {
        System.clearProperty(key);
      } else {
        System.setProperty(key, previous);
      }
    }
  }
}
