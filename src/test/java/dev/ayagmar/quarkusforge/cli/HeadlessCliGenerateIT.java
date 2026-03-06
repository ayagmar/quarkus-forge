package dev.ayagmar.quarkusforge.cli;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.ayagmar.quarkusforge.SystemPropertyExtension;
import dev.ayagmar.quarkusforge.api.JsonSupport;
import dev.ayagmar.quarkusforge.runtime.RuntimeConfig;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests exercising the {@link HeadlessCli} entry point end-to-end. These complement
 * {@link QuarkusForgeGenerateCommandTest} (which routes through {@link QuarkusForgeCli}) by
 * verifying the headless-only jar surface: generate, dry-run, Forgefile loading, lock-check, and
 * verbose diagnostics.
 */
class HeadlessCliGenerateIT {
  private static final String HEADLESS_CATALOG_TIMEOUT_PROPERTY =
      "quarkus.forge.headless.catalog-timeout-ms";
  private static final String HEADLESS_GENERATION_TIMEOUT_PROPERTY =
      "quarkus.forge.headless.generation-timeout-ms";

  @RegisterExtension final SystemPropertyExtension systemProperties = new SystemPropertyExtension();

  @TempDir Path tempDir;

  private WireMockServer wireMockServer;

  @BeforeEach
  void setUp() {
    wireMockServer = new WireMockServer(0);
    wireMockServer.start();
  }

  @AfterEach
  void tearDown() {
    if (wireMockServer != null) {
      wireMockServer.stop();
    }
  }

  // ── generate ──────────────────────────────────────────────────────

  @Test
  void generateCreatesProjectThroughHeadlessCli() throws Exception {
    stubCatalogEndpoints();
    stubDownloadEndpoint("hl-app");
    Path outputDir = tempDir.resolve("output");

    CliCommandTestSupport.CommandResult result =
        runHeadless(
            "generate",
            "--group-id",
            "com.example",
            "--artifact-id",
            "hl-app",
            "--output-dir",
            outputDir.toString(),
            "--extension",
            "io.quarkus:quarkus-rest");

    assertThat(result.exitCode()).isZero();
    assertThat(outputDir.resolve("hl-app/pom.xml")).exists();
    assertThat(result.standardOut()).contains("Generation succeeded");
    wireMockServer.verify(
        getRequestedFor(urlPathEqualTo("/api/download")).withQueryParam("a", equalTo("hl-app")));
  }

  // ── dry-run ───────────────────────────────────────────────────────

  @Test
  void dryRunValidatesWithoutWritingFiles() {
    stubCatalogEndpoints();
    Path outputDir = tempDir.resolve("output");

    CliCommandTestSupport.CommandResult result =
        runHeadless(
            "generate",
            "--dry-run",
            "--group-id",
            "com.example",
            "--artifact-id",
            "dry-app",
            "--output-dir",
            outputDir.toString(),
            "--extension",
            "io.quarkus:quarkus-rest");

    assertThat(result.exitCode()).isZero();
    assertThat(outputDir).doesNotExist();
    wireMockServer.verify(0, getRequestedFor(urlPathEqualTo("/api/download")));
  }

  @Test
  void rootLevelDryRunFlagAlsoPreventGeneration() {
    stubCatalogEndpoints();
    Path outputDir = tempDir.resolve("output");

    CliCommandTestSupport.CommandResult result =
        CliCommandTestSupport.runHeadlessCommand(
            runtimeConfig(),
            "--dry-run",
            "generate",
            "--group-id",
            "com.example",
            "--artifact-id",
            "dry-app",
            "--output-dir",
            outputDir.toString(),
            "--extension",
            "io.quarkus:quarkus-rest");

    assertThat(result.exitCode()).isZero();
    assertThat(outputDir).doesNotExist();
  }

  // ── verbose diagnostics ───────────────────────────────────────────

  @Test
  void verboseFlagEmitsStructuredDiagnosticsToStderr() {
    stubCatalogEndpoints();

    CliCommandTestSupport.CommandResult result =
        CliCommandTestSupport.runHeadlessCommand(
            runtimeConfig(),
            "--verbose",
            "generate",
            "--dry-run",
            "--group-id",
            "com.example",
            "--artifact-id",
            "diag-app",
            "--extension",
            "io.quarkus:quarkus-rest");

    assertThat(result.exitCode()).isZero();
    assertThat(result.standardError())
        .contains("\"event\":\"generate.start\"")
        .contains("\"event\":\"catalog.load.success\"")
        .contains("\"event\":\"generate.dry-run.validated\"");
  }

  // ── validation errors ─────────────────────────────────────────────

  @Test
  void unknownExtensionReturnsValidationExitCode() {
    stubCatalogEndpoints();

    CliCommandTestSupport.CommandResult result =
        runHeadless("generate", "--dry-run", "--extension", "io.quarkus:nonexistent-extension");

    assertThat(result.exitCode()).isEqualTo(ExitCodes.VALIDATION);
  }

  @Test
  void invalidBuildToolReturnsValidationExitCode() {
    stubCatalogEndpoints();

    CliCommandTestSupport.CommandResult result =
        runHeadless("generate", "--dry-run", "--build-tool", "ant");

    assertThat(result.exitCode()).isEqualTo(ExitCodes.VALIDATION);
  }

  // ── network failures ──────────────────────────────────────────────

  @Test
  void catalogLoadTimeoutReturnsNetworkExitCode() {
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/streams"))
            .willReturn(aResponse().withFixedDelay(5000).withStatus(200).withBody("[]")));
    wireMockServer.stubFor(
        get(urlPathEqualTo("/q/openapi")).willReturn(aResponse().withStatus(200).withBody("{}")));
    stubSingleRestExtensionCatalog();

    systemProperties.set(HEADLESS_CATALOG_TIMEOUT_PROPERTY, 50L);

    CliCommandTestSupport.CommandResult result =
        runHeadless("generate", "--dry-run", "--extension", "io.quarkus:quarkus-rest");

    assertThat(result.exitCode()).isEqualTo(ExitCodes.NETWORK);
    assertThat(result.standardError()).contains("timed out");
  }

  @Test
  void generationTimeoutReturnsNetworkExitCode() throws Exception {
    stubCatalogEndpoints();
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/download"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withFixedDelay(500)
                    .withHeader("Content-Type", "application/zip")
                    .withBody(generatedZipPayload("hl-app"))));
    systemProperties.set(HEADLESS_GENERATION_TIMEOUT_PROPERTY, 50L);

    CliCommandTestSupport.CommandResult result =
        runHeadless(
            "generate",
            "--group-id",
            "com.example",
            "--artifact-id",
            "hl-app",
            "--output-dir",
            tempDir.resolve("output").toString(),
            "--extension",
            "io.quarkus:quarkus-rest");

    assertThat(result.exitCode()).isEqualTo(ExitCodes.NETWORK);
    assertThat(result.standardError()).contains("timed out");
  }

  @Test
  void presetLoadTimeoutReturnsNetworkExitCode() {
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/extensions"))
            .willReturn(
                okJson(
                    """
                    [
                      {"id":"io.quarkus:quarkus-rest","name":"REST","shortName":"rest"},
                      {"id":"io.quarkus:quarkus-arc","name":"CDI","shortName":"cdi"}
                    ]
                    """)));
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/presets/stream/io.quarkus.platform%3A3.31"))
            .willReturn(aResponse().withFixedDelay(500).withStatus(200).withBody("[]")));
    CliCommandTestSupport.stubLiveMetadataWithAllBuildTools(wireMockServer);
    systemProperties.set(HEADLESS_CATALOG_TIMEOUT_PROPERTY, 50L);

    CliCommandTestSupport.CommandResult result =
        runHeadless("generate", "--dry-run", "--preset", "web");

    assertThat(result.exitCode()).isEqualTo(ExitCodes.NETWORK);
    assertThat(result.standardError()).contains("timed out");
  }

  // ── Forgefile round-trip ──────────────────────────────────────────

  @Test
  void dryRunLoadsForgefileAndSavesWithLock() throws Exception {
    stubCatalogEndpoints();
    Path forgefilePath = tempDir.resolve("Forgefile.json");
    Files.writeString(
        forgefilePath,
        """
        {
          "groupId": "com.team",
          "artifactId": "team-service",
          "buildTool": "maven",
          "javaVersion": "25",
          "presets": [],
          "extensions": ["io.quarkus:quarkus-rest"]
        }
        """);

    CliCommandTestSupport.CommandResult result =
        runHeadless("generate", "--dry-run", "--from", forgefilePath.toString(), "--lock");

    assertThat(result.exitCode()).isZero();
    String updatedContent = Files.readString(forgefilePath);
    assertThat(updatedContent).contains("\"locked\"");
    assertThat(updatedContent).contains("\"platformStream\"");
    assertThat(updatedContent).contains("io.quarkus:quarkus-rest");
  }

  @Test
  void lockCheckDetectsDrift() throws Exception {
    stubCatalogEndpoints();
    Path forgefilePath = tempDir.resolve("Forgefile.json");
    Files.writeString(
        forgefilePath,
        """
        {
          "groupId": "com.team",
          "artifactId": "drift-app",
          "buildTool": "maven",
          "javaVersion": "25",
          "presets": [],
          "extensions": ["io.quarkus:quarkus-rest"],
          "locked": {
            "platformStream": "io.quarkus.platform:3.31",
            "buildTool": "maven",
            "javaVersion": "21",
            "presets": [],
            "extensions": ["io.quarkus:quarkus-rest"]
          }
        }
        """);

    CliCommandTestSupport.CommandResult result =
        runHeadless("generate", "--dry-run", "--from", forgefilePath.toString(), "--lock-check");

    assertThat(result.exitCode()).isEqualTo(ExitCodes.VALIDATION);
    assertThat(result.standardError()).contains("javaVersion drift");
  }

  @Test
  void saveAsWritesForgefileToSpecifiedPath() throws Exception {
    stubCatalogEndpoints();
    Path saveAsPath = tempDir.resolve("my-recipe.json");

    CliCommandTestSupport.CommandResult result =
        runHeadless(
            "generate",
            "--dry-run",
            "--group-id",
            "com.save",
            "--artifact-id",
            "save-app",
            "--extension",
            "io.quarkus:quarkus-rest",
            "--save-as",
            saveAsPath.toString());

    assertThat(result.exitCode()).isZero();
    assertThat(saveAsPath).exists();
    var payload = JsonSupport.parseObject(Files.readString(saveAsPath));
    assertThat(payload).containsEntry("groupId", "com.save");
    assertThat(payload).containsKey("extensions");
    @SuppressWarnings("unchecked")
    java.util.List<Object> extensions = (java.util.List<Object>) payload.get("extensions");
    assertThat(extensions).contains("io.quarkus:quarkus-rest");
  }

  // ── preset resolution ─────────────────────────────────────────────

  @Test
  void builtInPresetResolvesExtensions() {
    stubCatalogEndpoints();

    CliCommandTestSupport.CommandResult result =
        runHeadless("generate", "--dry-run", "--preset", "web");

    assertThat(result.exitCode()).isZero();
    // The dry-run output should mention the resolved extensions from the "web" preset
    assertThat(result.standardOut()).contains("quarkus-rest");
  }

  @Test
  void unknownPresetReturnsValidationExitCode() {
    stubCatalogEndpoints();

    CliCommandTestSupport.CommandResult result =
        runHeadless("generate", "--dry-run", "--preset", "nonexistent-preset");

    assertThat(result.exitCode()).isEqualTo(ExitCodes.VALIDATION);
  }

  // ── helpers ───────────────────────────────────────────────────────

  private RuntimeConfig runtimeConfig() {
    return CliCommandTestSupport.runtimeConfig(tempDir, URI.create(wireMockServer.baseUrl()));
  }

  private CliCommandTestSupport.CommandResult runHeadless(String... args) {
    return CliCommandTestSupport.runHeadlessCommand(runtimeConfig(), args);
  }

  private void stubCatalogEndpoints() {
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/extensions"))
            .willReturn(
                okJson(
                    """
                    [
                      {"id":"io.quarkus:quarkus-rest","name":"REST","shortName":"rest"},
                      {"id":"io.quarkus:quarkus-arc","name":"CDI","shortName":"cdi"}
                    ]
                    """)));
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/presets"))
            .willReturn(
                okJson(
                    """
                    [
                      {"key":"web","extensions":["io.quarkus:quarkus-rest","io.quarkus:quarkus-arc"]}
                    ]
                    """)));
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/presets/stream/io.quarkus.platform%3A3.31"))
            .willReturn(
                okJson(
                    """
                    [
                      {"key":"web","extensions":["io.quarkus:quarkus-rest","io.quarkus:quarkus-arc"]}
                    ]
                    """)));
    CliCommandTestSupport.stubLiveMetadataWithAllBuildTools(wireMockServer);
  }

  private void stubDownloadEndpoint(String artifactId) throws Exception {
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/download"))
            .willReturn(aResponse().withStatus(200).withBody(generatedZipPayload(artifactId))));
  }

  private void stubSingleRestExtensionCatalog() {
    CliCommandTestSupport.stubSingleRestExtensionCatalog(wireMockServer);
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
}
