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
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QuarkusForgeGenerateCommandTest {
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
    QuarkusForgeCli.RuntimeConfig runtimeConfig =
        runtimeConfig(URI.create(wireMockServer.baseUrl()));
    Path outputDir = tempDir.resolve("output");

    CommandResult result =
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
    verifyGenerateRequestFor("headless-app", "io.quarkus:quarkus-rest,io.quarkus:quarkus-arc");
  }

  @Test
  void headlessGeneratePassesPlatformStreamWhenProvided() throws Exception {
    stubCatalogEndpoints();
    stubDownloadEndpoint("headless-app");
    QuarkusForgeCli.RuntimeConfig runtimeConfig =
        runtimeConfig(URI.create(wireMockServer.baseUrl()));
    Path outputDir = tempDir.resolve("output-platform");

    CommandResult result =
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
    QuarkusForgeCli.RuntimeConfig runtimeConfig =
        runtimeConfig(URI.create(wireMockServer.baseUrl()));
    Path outputDir = tempDir.resolve("output-default-stream");

    CommandResult result =
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
    QuarkusForgeCli.RuntimeConfig runtimeConfig =
        runtimeConfig(URI.create(wireMockServer.baseUrl()));

    CommandResult result =
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
    QuarkusForgeCli.RuntimeConfig runtimeConfig =
        runtimeConfig(URI.create(wireMockServer.baseUrl()));

    CommandResult result =
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
    QuarkusForgeCli.RuntimeConfig runtimeConfig = runtimeConfig(URI.create("http://127.0.0.1:1"));

    CommandResult result =
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
  void outputConflictReturnsArchiveExitCode() throws Exception {
    stubCatalogEndpoints();
    stubDownloadEndpoint("headless-app");
    QuarkusForgeCli.RuntimeConfig runtimeConfig =
        runtimeConfig(URI.create(wireMockServer.baseUrl()));
    Path outputDir = tempDir.resolve("output");
    Files.createDirectories(outputDir.resolve("headless-app"));

    CommandResult result =
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
    assertThat(result.standardError()).contains("Failed to extract ZIP archive");
  }

  @Test
  void dryRunSupportsBuiltInAndFavoritesPresetsWithoutGeneratingFiles() throws Exception {
    stubCatalogEndpoints();
    QuarkusForgeCli.RuntimeConfig runtimeConfig =
        runtimeConfig(URI.create(wireMockServer.baseUrl()));
    ExtensionFavoritesStore.fileBacked(runtimeConfig.favoritesFile())
        .saveFavoriteExtensionIds(Set.of("io.quarkus:quarkus-jdbc-postgresql"));
    Path outputDir = tempDir.resolve("dry-run-output");

    CommandResult result =
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
    QuarkusForgeCli.RuntimeConfig runtimeConfig =
        runtimeConfig(URI.create(wireMockServer.baseUrl()));
    ExtensionFavoritesStore.fileBacked(runtimeConfig.favoritesFile())
        .saveFavoriteExtensionIds(Set.of("io.quarkus:quarkus-jdbc-postgresql"));
    Path outputDir = tempDir.resolve("parity-output");

    CommandResult dryRunResult =
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

    CommandResult generateResult =
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
        "io.quarkus:quarkus-rest,io.quarkus:quarkus-arc,io.quarkus:quarkus-jdbc-postgresql,io.quarkus:quarkus-hibernate-orm-panache");
  }

  @Test
  void rootDryRunFlagBeforeGenerateSubcommandStillPreventsProjectGeneration() throws Exception {
    stubCatalogEndpoints();
    QuarkusForgeCli.RuntimeConfig runtimeConfig =
        runtimeConfig(URI.create(wireMockServer.baseUrl()));
    Path outputDir = tempDir.resolve("root-dry-run-output");

    CommandResult result =
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

  private QuarkusForgeCli.RuntimeConfig runtimeConfig(URI baseUri) {
    return new QuarkusForgeCli.RuntimeConfig(
        baseUri, tempDir.resolve("catalog-cache.json"), tempDir.resolve("favorites.json"));
  }

  private CommandResult runCommand(QuarkusForgeCli.RuntimeConfig runtimeConfig, String... args) {
    PrintStream originalOut = System.out;
    PrintStream originalErr = System.err;
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    try {
      System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
      System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
      int exitCode = QuarkusForgeCli.runWithArgs(args, runtimeConfig);
      return new CommandResult(
          exitCode,
          stdout.toString(StandardCharsets.UTF_8),
          stderr.toString(StandardCharsets.UTF_8));
    } finally {
      System.setOut(originalOut);
      System.setErr(originalErr);
    }
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
                                  {"name":"b","schema":{"enum":["MAVEN","GRADLE","GRADLE_KOTLIN_DSL"]}}
                                ]
                              }
                            }
                          }
                        }
                        """)));
  }

  private void stubDownloadEndpoint(String artifactId) throws Exception {
    stubFor(
        get(urlPathEqualTo("/api/download"))
            .willReturn(aResponse().withStatus(200).withBody(generatedZipPayload(artifactId))));
  }

  private void verifyGenerateRequestFor(String artifactId, String extensionsParam) {
    wireMockServer.verify(
        getRequestedFor(urlPathEqualTo("/api/download"))
            .withQueryParam("g", equalTo("com.example"))
            .withQueryParam("a", equalTo(artifactId))
            .withQueryParam("v", equalTo("1.0.0-SNAPSHOT"))
            .withQueryParam("b", equalTo("MAVEN"))
            .withQueryParam("j", equalTo("25"))
            .withQueryParam("e", equalTo(extensionsParam)));
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

  private record CommandResult(int exitCode, String standardOut, String standardError) {}
}
