package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.api.CatalogData;
import dev.ayagmar.quarkusforge.api.CatalogSource;
import dev.ayagmar.quarkusforge.api.ExtensionDto;
import dev.ayagmar.quarkusforge.api.GenerationRequest;
import dev.ayagmar.quarkusforge.api.MetadataDto;
import dev.ayagmar.quarkusforge.api.PlatformStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HeadlessGenerationServiceTest {
  @TempDir Path tempDir;

  private static final MetadataDto METADATA =
      new MetadataDto(
          List.of("21"),
          List.of("maven"),
          Map.of("maven", List.of("21")),
          List.of(new PlatformStream("io.quarkus.platform:3.31", "3.31", true, List.of("21"))));
  private static final CatalogData CATALOG_DATA =
      new CatalogData(
          METADATA,
          List.of(new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest")),
          CatalogSource.LIVE,
          false,
          "");

  @Test
  void dryRunSkipsGenerationExecution() {
    StubCatalogClient client = new StubCatalogClient();
    HeadlessGenerationService service = new HeadlessGenerationService(client, stubRuntimeConfig());

    GenerateCommand command = commandWithOutput();
    int exitCode = service.run(command, true, false);

    assertThat(exitCode).isEqualTo(ExitCodes.OK);
    assertThat(client.startGenerationCalls).isZero();
  }

  @Test
  void generationTimeoutCancelsRunningFuture() {
    StubCatalogClient client = new StubCatalogClient();
    client.generationFuture = new CompletableFuture<>();
    HeadlessGenerationService service = new HeadlessGenerationService(client, stubRuntimeConfig());

    GenerateCommand command = commandWithOutput();
    System.setProperty("quarkus.forge.headless.generation-timeout-ms", "1");
    try {
      int exitCode = service.run(command, false, false);
      assertThat(exitCode).isEqualTo(ExitCodes.NETWORK);
      assertThat(client.startGenerationCalls).isEqualTo(1);
      assertThat(client.generationFuture.isCancelled()).isTrue();
    } finally {
      System.clearProperty("quarkus.forge.headless.generation-timeout-ms");
    }
  }

  @Test
  void catalogTimeoutReturnsNetworkExitCodeBeforeValidation() {
    StubCatalogClient client = new StubCatalogClient();
    client.catalogLoadTimeout = new TimeoutException("timeout");
    HeadlessGenerationService service = new HeadlessGenerationService(client, stubRuntimeConfig());

    GenerateCommand command = commandWithOutput();
    int exitCode = service.run(command, false, false);

    assertThat(exitCode).isEqualTo(ExitCodes.NETWORK);
    assertThat(client.startGenerationCalls).isZero();
  }

  private GenerateCommand commandWithOutput() {
    GenerateCommand cmd = new GenerateCommand();
    cmd.requestOptions = new RequestOptions();
    cmd.requestOptions.groupId = "com.example";
    cmd.requestOptions.artifactId = "demo-app";
    cmd.requestOptions.version = "1.0.0-SNAPSHOT";
    cmd.requestOptions.outputDirectory = tempDir.toString();
    cmd.requestOptions.platformStream = "io.quarkus.platform:3.31";
    cmd.requestOptions.buildTool = "maven";
    cmd.requestOptions.javaVersion = "21";
    return cmd;
  }

  private RuntimeConfig stubRuntimeConfig() {
    return new RuntimeConfig(
        java.net.URI.create("https://code.quarkus.io"),
        tempDir.resolve("cache.json"),
        tempDir.resolve("preferences.json"),
        tempDir.resolve("favorites.json"));
  }

  private static final class StubCatalogClient extends HeadlessCatalogClient {
    TimeoutException catalogLoadTimeout;
    CompletableFuture<Path> generationFuture =
        CompletableFuture.completedFuture(Path.of("output/demo-app"));
    int startGenerationCalls;

    StubCatalogClient() {
      // Use the no-arg constructor: no real HttpClient is created.
    }

    @Override
    public void close() {
      // No resources to release in this stub.
    }

    @Override
    CatalogData loadCatalogData(Duration timeout)
        throws ExecutionException, InterruptedException, TimeoutException {
      if (catalogLoadTimeout != null) {
        throw catalogLoadTimeout;
      }
      return CATALOG_DATA;
    }

    @Override
    Map<String, List<String>> loadBuiltInPresets(String platformStream, Duration timeout) {
      return Map.of("web", List.of("io.quarkus:quarkus-rest"));
    }

    @Override
    CompletableFuture<Path> startGeneration(
        GenerationRequest generationRequest,
        Path outputPath,
        Consumer<String> progressLineConsumer) {
      startGenerationCalls++;
      return generationFuture;
    }
  }
}
