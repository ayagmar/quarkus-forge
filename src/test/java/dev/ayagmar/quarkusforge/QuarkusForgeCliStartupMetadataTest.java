package dev.ayagmar.quarkusforge;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
    stubLiveMetadataWithMavenOnly();
    QuarkusForgeCli.RuntimeConfig runtimeConfig =
        runtimeConfig(URI.create(wireMockServer.baseUrl()));

    CommandResult result =
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
  void dryRunUsesRecommendedPlatformStreamWhenOptionIsOmitted() {
    stubLiveMetadataWithMavenOnly();
    QuarkusForgeCli.RuntimeConfig runtimeConfig =
        runtimeConfig(URI.create(wireMockServer.baseUrl()));

    CommandResult result =
        runCommand(
            runtimeConfig, "--dry-run", "--group-id", "com.example", "--artifact-id", "forge-app");

    assertThat(result.exitCode()).isZero();
    assertThat(result.standardOut()).contains("metadataSource: live");
    assertThat(result.standardOut()).contains("platformStream: io.quarkus.platform:3.31");
  }

  @Test
  void dryRunFallsBackToSnapshotWhenLiveMetadataIsUnavailable() {
    stubStreamsUnavailable();
    QuarkusForgeCli.RuntimeConfig runtimeConfig =
        runtimeConfig(URI.create(wireMockServer.baseUrl()));

    CommandResult result =
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
    stubStreamsUnavailable();
    QuarkusForgeCli.RuntimeConfig runtimeConfig =
        runtimeConfig(URI.create(wireMockServer.baseUrl()));

    CommandResult result =
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

  private void stubLiveMetadataWithMavenOnly() {
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
                              "versions":[25],
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
  }

  private void stubStreamsUnavailable() {
    stubFor(get(urlPathEqualTo("/api/streams")).willReturn(aResponse().withStatus(404)));
    stubFor(
        get(urlPathEqualTo("/q/openapi")).willReturn(aResponse().withStatus(200).withBody("{}")));
  }

  private record CommandResult(int exitCode, String standardOut, String standardError) {}
}
