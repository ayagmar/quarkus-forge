package dev.ayagmar.quarkusforge.application;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.ayagmar.quarkusforge.cli.CliCommandTestSupport;
import dev.ayagmar.quarkusforge.diagnostics.DiagnosticLogger;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LiveStartupMetadataLoaderTest {
  private WireMockServer wireMockServer;

  @AfterEach
  void tearDown() {
    if (wireMockServer != null) {
      wireMockServer.stop();
    }
  }

  private WireMockServer startWireMockServer() {
    wireMockServer = new WireMockServer(0);
    wireMockServer.start();
    return wireMockServer;
  }

  @Test
  void loadReturnsLiveMetadataWhenRefreshSucceeds() {
    WireMockServer wireMockServer = startWireMockServer();
    CliCommandTestSupport.stubLiveMetadataWithMavenOnly(wireMockServer);
    LiveStartupMetadataLoader loader =
        new LiveStartupMetadataLoader(
            URI.create(wireMockServer.baseUrl()),
            dev.ayagmar.quarkusforge.api.QuarkusApiClient::new,
            Duration.ofSeconds(2),
            DiagnosticLogger.create(false));

    StartupMetadataSelection selection = loader.load();

    assertThat(selection.sourceLabel()).isEqualTo("live");
    assertThat(selection.detailMessage()).isEmpty();
    assertThat(selection.metadataCompatibility().loadError()).isNull();
    assertThat(selection.metadataCompatibility().metadataSnapshot().recommendedPlatformStreamKey())
        .isEqualTo("io.quarkus.platform:3.31");
  }

  @Test
  void loadFallsBackToSnapshotWhenRefreshTimesOut() {
    WireMockServer wireMockServer = startWireMockServer();
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/streams"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withFixedDelay(2_500)
                    .withBody(
                        """
                        [
                          {
                            "key":"io.quarkus.platform:3.31",
                            "javaCompatibility":{"versions":[21,25],"recommended":25},
                            "recommended":true,
                            "status":"FINAL"
                          }
                        ]
                        """)));
    wireMockServer.stubFor(
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
                              "get": {"parameters": [{"name":"b","schema":{"enum":["MAVEN"]}}]}
                            }
                          }
                        }
                        """)));
    LiveStartupMetadataLoader loader =
        new LiveStartupMetadataLoader(
            URI.create(wireMockServer.baseUrl()),
            dev.ayagmar.quarkusforge.api.QuarkusApiClient::new,
            Duration.ofSeconds(2),
            DiagnosticLogger.create(false));

    StartupMetadataSelection selection = loader.load();

    assertThat(selection.sourceLabel()).isEqualTo("snapshot fallback");
    assertThat(selection.detailMessage()).contains("timed out after 2000ms");
  }

  @Test
  void fallbackSelectionInterruptsThreadForInterruptedFailure() {
    LiveStartupMetadataLoader loader =
        new LiveStartupMetadataLoader(
            URI.create("http://localhost:8080"),
            dev.ayagmar.quarkusforge.api.QuarkusApiClient::new,
            DiagnosticLogger.create(false));

    try {
      StartupMetadataSelection selection =
          loader.fallbackSelection(new InterruptedException("stop"));

      assertThat(selection.sourceLabel()).isEqualTo("snapshot fallback");
      assertThat(selection.detailMessage()).contains("Live metadata refresh interrupted");
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void fallbackSelectionUsesCancelledReasonForCancellation() {
    LiveStartupMetadataLoader loader =
        new LiveStartupMetadataLoader(
            URI.create("http://localhost:8080"),
            dev.ayagmar.quarkusforge.api.QuarkusApiClient::new,
            DiagnosticLogger.create(false));

    StartupMetadataSelection selection =
        loader.fallbackSelection(new CancellationException("cancelled"));

    assertThat(selection.sourceLabel()).isEqualTo("snapshot fallback");
    assertThat(selection.detailMessage()).contains("Live metadata refresh cancelled");
  }
}
