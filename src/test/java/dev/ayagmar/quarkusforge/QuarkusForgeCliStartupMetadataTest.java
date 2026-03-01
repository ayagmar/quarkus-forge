package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QuarkusForgeCliStartupMetadataTest {
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
  void dryRunUsesLiveMetadataForInitialValidationWhenAvailable() {
    CliCommandTestSupport.stubLiveMetadataWithMavenOnly();
    RuntimeConfig runtimeConfig = runtimeConfig(URI.create(wireMockServer.baseUrl()));

    CliCommandTestSupport.CommandResult result =
        runCommand(
            runtimeConfig,
            "--dry-run",
            "--group-id",
            "com.example",
            "--artifact-id",
            "forge-app",
            "--build-tool",
            "gradle");

    assertThat(result.exitCode()).isEqualTo(QuarkusForgeCli.EXIT_CODE_VALIDATION);
    assertThat(result.standardError()).contains("metadataSource: live");
    assertThat(result.standardError()).contains("unsupported build tool 'gradle'");
  }

  @Test
  void verboseDryRunEmitsMetadataLoadDiagnostics() {
    CliCommandTestSupport.stubLiveMetadataWithMavenOnly();
    RuntimeConfig runtimeConfig = runtimeConfig(URI.create(wireMockServer.baseUrl()));

    CliCommandTestSupport.CommandResult result =
        runCommand(
            runtimeConfig,
            "--verbose",
            "--dry-run",
            "--group-id",
            "com.example",
            "--artifact-id",
            "forge-app");

    assertThat(result.exitCode()).isZero();
    assertThat(result.standardError()).contains("\"event\":\"metadata.load.success\"");
    assertThat(result.standardError()).contains("\"traceId\":\"");
  }

  @Test
  void verboseSmokeModeEmitsTuiCatalogDiagnostics() {
    CliCommandTestSupport.stubLiveMetadataWithMavenOnly();
    CliCommandTestSupport.stubSingleRestExtensionCatalog();
    RuntimeConfig runtimeConfig = runtimeConfig(URI.create(wireMockServer.baseUrl()));

    CliCommandTestSupport.CommandResult result = runSmoke(runtimeConfig, true);

    assertThat(result.exitCode()).isZero();
    assertThat(result.standardError()).contains("\"event\":\"tui.session.start\"");
    assertThat(result.standardError()).contains("\"event\":\"catalog.load.start\"");
    assertThat(result.standardError())
        .contains("\"event\":\"catalog.load.success\"")
        .contains("\"mode\":\"tui\"");
    assertThat(result.standardError()).contains("\"event\":\"tui.session.exit\"");
  }

  @Test
  void smokeModeWithoutInteractiveConsoleAvoidsTerminalEscapeOutput() {
    CliCommandTestSupport.stubLiveMetadataWithMavenOnly();
    CliCommandTestSupport.stubSingleRestExtensionCatalog();
    RuntimeConfig runtimeConfig = runtimeConfig(URI.create(wireMockServer.baseUrl()));

    CliCommandTestSupport.CommandResult result = runSmoke(runtimeConfig, true);

    assertThat(result.exitCode()).isZero();
    assertThat(result.standardError()).contains("\"mode\":\"headless-smoke\"");
    assertThat(result.standardOut()).doesNotContain("\u001B");
  }

  @Test
  void dryRunUsesRecommendedPlatformStreamWhenOptionIsOmitted() {
    CliCommandTestSupport.stubLiveMetadataWithMavenOnly();
    RuntimeConfig runtimeConfig = runtimeConfig(URI.create(wireMockServer.baseUrl()));

    CliCommandTestSupport.CommandResult result =
        runCommand(
            runtimeConfig, "--dry-run", "--group-id", "com.example", "--artifact-id", "forge-app");

    assertThat(result.exitCode()).isZero();
    assertThat(result.standardOut()).contains("metadataSource: live");
    assertThat(result.standardOut()).contains("platformStream: io.quarkus.platform:3.31");
  }

  @Test
  void dryRunFallsBackToSnapshotWhenLiveMetadataIsUnavailable() {
    CliCommandTestSupport.stubStreamsUnavailable();
    RuntimeConfig runtimeConfig = runtimeConfig(URI.create(wireMockServer.baseUrl()));

    CliCommandTestSupport.CommandResult result =
        runCommand(
            runtimeConfig,
            "--dry-run",
            "--group-id",
            "com.example",
            "--artifact-id",
            "forge-app",
            "--build-tool",
            "gradle-kotlin-dsl",
            "--java-version",
            "25");

    assertThat(result.exitCode()).isZero();
    assertThat(result.standardOut()).contains("metadataSource: snapshot fallback");
    assertThat(result.standardOut()).contains("metadataDetail: Live metadata unavailable");
  }

  @Test
  void fallbackMetadataSelectionKeepsValidationDeterministicAfterLiveFailure() {
    CliCommandTestSupport.stubStreamsUnavailable();
    RuntimeConfig runtimeConfig = runtimeConfig(URI.create(wireMockServer.baseUrl()));

    CliCommandTestSupport.CommandResult result =
        runCommand(
            runtimeConfig,
            "--dry-run",
            "--group-id",
            "com.example",
            "--artifact-id",
            "forge-app",
            "--platform-stream",
            "io.quarkus.platform:3.20",
            "--java-version",
            "25");

    assertThat(result.exitCode()).isEqualTo(QuarkusForgeCli.EXIT_CODE_VALIDATION);
    assertThat(result.standardError()).contains("metadataSource: snapshot fallback");
    assertThat(result.standardError())
        .contains(
            "unsupported combination: platform stream 'io.quarkus.platform:3.20' does not support"
                + " Java 25");
  }

  @Test
  void dryRunIgnoresStoredTuiPreferences() throws Exception {
    CliCommandTestSupport.stubLiveMetadataWithMavenOnly();
    RuntimeConfig runtimeConfig = runtimeConfig(URI.create(wireMockServer.baseUrl()));
    Files.writeString(
        tempDir.resolve("preferences.json"),
        """
        {
          "schemaVersion": 1,
          "groupId": "org.saved",
          "artifactId": "saved-app",
          "version": "9.9.9",
          "packageName": "org.saved.app",
          "outputDirectory": "./saved",
          "platformStream": "",
          "buildTool": "gradle",
          "javaVersion": "25"
        }
        """);

    CliCommandTestSupport.CommandResult result = runCommand(runtimeConfig, "--dry-run");

    assertThat(result.exitCode()).isZero();
    assertThat(result.standardOut()).contains("buildTool: maven");
    assertThat(result.standardError()).doesNotContain("unsupported build tool 'gradle'");
  }

  private RuntimeConfig runtimeConfig(URI baseUri) {
    return CliCommandTestSupport.runtimeConfig(tempDir, baseUri);
  }

  private CliCommandTestSupport.CommandResult runCommand(
      RuntimeConfig runtimeConfig, String... args) {
    return CliCommandTestSupport.runCommand(runtimeConfig, args);
  }

  private CliCommandTestSupport.CommandResult runSmoke(
      RuntimeConfig runtimeConfig, boolean verbose) {
    return CliCommandTestSupport.runSmoke(runtimeConfig, verbose);
  }
}
