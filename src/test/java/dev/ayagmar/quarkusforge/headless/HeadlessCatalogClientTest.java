package dev.ayagmar.quarkusforge.headless;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.ayagmar.quarkusforge.api.CatalogData;
import dev.ayagmar.quarkusforge.api.CatalogSource;
import dev.ayagmar.quarkusforge.api.GenerationRequest;
import dev.ayagmar.quarkusforge.cli.CliCommandTestSupport;
import dev.ayagmar.quarkusforge.runtime.RuntimeConfig;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HeadlessCatalogClientTest {
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

  @Test
  void loadCatalogDataReturnsLiveCatalogData() throws Exception {
    stubCatalogEndpoints();

    try (HeadlessCatalogClient client = new HeadlessCatalogClient(runtimeConfig())) {
      CatalogData catalogData = client.loadCatalogData(Duration.ofSeconds(1));

      assertThat(catalogData.source()).isEqualTo(CatalogSource.LIVE);
      assertThat(catalogData.extensions())
          .extracting(dev.ayagmar.quarkusforge.api.ExtensionDto::id)
          .containsExactly("io.quarkus:quarkus-rest");
      assertThat(catalogData.metadata().buildTools()).contains("maven");
    }
  }

  @Test
  void loadCatalogDataTimesOutWithContextualMessage() {
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/extensions"))
            .willReturn(aResponse().withFixedDelay(500).withStatus(200).withBody("[]")));
    CliCommandTestSupport.stubLiveMetadataWithAllBuildTools(wireMockServer);

    try (HeadlessCatalogClient client = new HeadlessCatalogClient(runtimeConfig())) {
      assertThatThrownBy(() -> client.loadCatalogData(Duration.ofMillis(50)))
          .isInstanceOf(TimeoutException.class)
          .hasMessage("catalog load timed out after 50ms");
    }
  }

  @Test
  void loadBuiltInPresetsReturnsStreamSpecificPresets() throws Exception {
    stubCatalogEndpoints();

    try (HeadlessCatalogClient client = new HeadlessCatalogClient(runtimeConfig())) {
      Map<String, List<String>> presets =
          client.loadBuiltInPresets("io.quarkus.platform:3.31", Duration.ofSeconds(1));

      assertThat(presets)
          .containsEntry("web", List.of("io.quarkus:quarkus-rest", "io.quarkus:quarkus-arc"));
      wireMockServer.verify(
          getRequestedFor(urlPathEqualTo("/api/presets/stream/io.quarkus.platform%3A3.31")));
    }
  }

  @Test
  void loadBuiltInPresetsTimesOutWithContextualMessage() {
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/presets/stream/io.quarkus.platform%3A3.31"))
            .willReturn(aResponse().withFixedDelay(500).withStatus(200).withBody("[]")));

    try (HeadlessCatalogClient client = new HeadlessCatalogClient(runtimeConfig())) {
      assertThatThrownBy(
              () -> client.loadBuiltInPresets("io.quarkus.platform:3.31", Duration.ofMillis(50)))
          .isInstanceOf(TimeoutException.class)
          .hasMessage("preset load timed out after 50ms");
    }
  }

  @Test
  void startGenerationDownloadsExtractsAndReportsProgress() throws Exception {
    stubCatalogEndpoints();
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/download"))
            .willReturn(aResponse().withStatus(200).withBody(generatedZipPayload("demo-app"))));
    List<String> progressLines = new ArrayList<>();
    GenerationRequest request =
        new GenerationRequest(
            "com.example",
            "demo-app",
            "1.0.0-SNAPSHOT",
            "io.quarkus.platform:3.31",
            "maven",
            "25",
            List.of("io.quarkus:quarkus-rest"));
    Path outputPath = tempDir.resolve("generated-project");

    try (HeadlessCatalogClient client = new HeadlessCatalogClient(runtimeConfig())) {
      Path generatedRoot = client.startGeneration(request, outputPath, progressLines::add).join();

      assertThat(generatedRoot).isEqualTo(outputPath);
      assertThat(Files.readString(generatedRoot.resolve("pom.xml"))).isEqualTo("<project/>");
      assertThat(progressLines)
          .containsExactly(
              "requesting project archive from Quarkus API...", "extracting project archive...");
    }
  }

  private RuntimeConfig runtimeConfig() {
    return CliCommandTestSupport.runtimeConfig(tempDir, URI.create(wireMockServer.baseUrl()));
  }

  private void stubCatalogEndpoints() {
    CliCommandTestSupport.stubSingleRestExtensionCatalog(wireMockServer);
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

  private static byte[] generatedZipPayload(String artifactId) throws Exception {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
      zipOutputStream.putNextEntry(new ZipEntry(artifactId + "/pom.xml"));
      zipOutputStream.write("<project/>".getBytes(StandardCharsets.UTF_8));
      zipOutputStream.closeEntry();

      zipOutputStream.putNextEntry(new ZipEntry(artifactId + "/README.md"));
      zipOutputStream.write("# generated".getBytes(StandardCharsets.UTF_8));
      zipOutputStream.closeEntry();
    }
    return outputStream.toByteArray();
  }
}
