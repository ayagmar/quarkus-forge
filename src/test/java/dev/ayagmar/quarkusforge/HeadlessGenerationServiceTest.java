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

  @Test
  void catalogCancellationReturnsCancelledExitCode() {
    StubCatalogClient client = new StubCatalogClient();
    client.catalogLoadCancellation = new java.util.concurrent.CancellationException("cancelled");
    HeadlessGenerationService service = new HeadlessGenerationService(client, stubRuntimeConfig());

    GenerateCommand command = commandWithOutput();
    int exitCode = service.run(command, false, false);

    assertThat(exitCode).isEqualTo(ExitCodes.CANCELLED);
  }

  @Test
  void validationFailureReturnsValidationExitCode() {
    StubCatalogClient client = new StubCatalogClient();
    HeadlessGenerationService service = new HeadlessGenerationService(client, stubRuntimeConfig());

    GenerateCommand command = commandWithOutput();
    command.requestOptions.buildTool = "ant"; // unsupported build tool
    int exitCode = service.run(command, false, false);

    assertThat(exitCode).isEqualTo(ExitCodes.VALIDATION);
  }

  @Test
  void unknownExtensionReturnsValidationExitCode() {
    StubCatalogClient client = new StubCatalogClient();
    HeadlessGenerationService service = new HeadlessGenerationService(client, stubRuntimeConfig());

    GenerateCommand command = commandWithOutput();
    command.extensions.add("io.quarkus:nonexistent");
    int exitCode = service.run(command, false, false);

    assertThat(exitCode).isEqualTo(ExitCodes.VALIDATION);
  }

  @Test
  void generationExecutionFailureReturnsCorrectExitCode() {
    StubCatalogClient client = new StubCatalogClient();
    client.generationFuture =
        CompletableFuture.failedFuture(
            new dev.ayagmar.quarkusforge.api.ApiClientException("connection refused", null));
    HeadlessGenerationService service = new HeadlessGenerationService(client, stubRuntimeConfig());

    GenerateCommand command = commandWithOutput();
    int exitCode = service.run(command, false, false);

    assertThat(exitCode).isEqualTo(ExitCodes.NETWORK);
  }

  @Test
  void verboseModeEmitsDiagnostics() {
    StubCatalogClient client = new StubCatalogClient();
    HeadlessGenerationService service = new HeadlessGenerationService(client, stubRuntimeConfig());

    GenerateCommand command = commandWithOutput();
    // verbose mode should not change exit code
    int exitCode = service.run(command, true, true);

    assertThat(exitCode).isEqualTo(ExitCodes.OK);
  }

  @Test
  void blankExtensionInputReturnsValidationExitCode() {
    StubCatalogClient client = new StubCatalogClient();
    HeadlessGenerationService service = new HeadlessGenerationService(client, stubRuntimeConfig());

    GenerateCommand command = commandWithOutput();
    command.extensions.add("   ");
    int exitCode = service.run(command, true, false);

    assertThat(exitCode).isEqualTo(ExitCodes.VALIDATION);
  }

  @Test
  void favoritesPresetResolvesFromStore() throws Exception {
    RuntimeConfig config = stubRuntimeConfig();
    // Write a favorites file
    java.nio.file.Files.createDirectories(config.favoritesFile().getParent());
    java.nio.file.Files.writeString(
        config.favoritesFile(),
        """
        {"extensions":["io.quarkus:quarkus-rest"]}
        """);

    StubCatalogClient client = new StubCatalogClient();
    HeadlessGenerationService service = new HeadlessGenerationService(client, config);

    GenerateCommand command = commandWithOutput();
    command.presets.add("favorites");
    int exitCode = service.run(command, true, false);

    assertThat(exitCode).isEqualTo(ExitCodes.OK);
  }

  @Test
  void builtInPresetResolvesExtensions() {
    StubCatalogClient client = new StubCatalogClient();
    HeadlessGenerationService service = new HeadlessGenerationService(client, stubRuntimeConfig());

    GenerateCommand command = commandWithOutput();
    command.presets.add("web");
    int exitCode = service.run(command, true, false);

    assertThat(exitCode).isEqualTo(ExitCodes.OK);
    // "web" preset resolves to quarkus-rest which is in the catalog
  }

  @Test
  void unknownPresetReturnsValidationExitCode() {
    StubCatalogClient client = new StubCatalogClient();
    HeadlessGenerationService service = new HeadlessGenerationService(client, stubRuntimeConfig());

    GenerateCommand command = commandWithOutput();
    command.presets.add("nonexistent");
    int exitCode = service.run(command, true, false);

    assertThat(exitCode).isEqualTo(ExitCodes.VALIDATION);
  }

  @Test
  void blankPresetInputIsSkipped() {
    StubCatalogClient client = new StubCatalogClient();
    HeadlessGenerationService service = new HeadlessGenerationService(client, stubRuntimeConfig());

    GenerateCommand command = commandWithOutput();
    command.presets.add("   ");
    int exitCode = service.run(command, true, false);

    assertThat(exitCode).isEqualTo(ExitCodes.OK);
  }

  @Test
  void presetLoadFailureReturnsNetworkExitCode() {
    StubCatalogClient client = new StubCatalogClient();
    client.presetLoadTimeout = new TimeoutException("presets timeout");
    HeadlessGenerationService service = new HeadlessGenerationService(client, stubRuntimeConfig());

    GenerateCommand command = commandWithOutput();
    command.presets.add("web"); // non-favorites preset triggers loadBuiltInPresets
    int exitCode = service.run(command, false, false);

    assertThat(exitCode).isEqualTo(ExitCodes.NETWORK);
  }

  @Test
  void lockWithoutFromOrSaveAsReturnsValidation() {
    StubCatalogClient client = new StubCatalogClient();
    HeadlessGenerationService service = new HeadlessGenerationService(client, stubRuntimeConfig());

    GenerateCommand command = commandWithOutput();
    command.lock = true;
    // No --from or --save-as
    int exitCode = service.run(command, false, false);

    assertThat(exitCode).isEqualTo(ExitCodes.VALIDATION);
  }

  @Test
  void lockCheckWithoutFromReturnsValidation() {
    StubCatalogClient client = new StubCatalogClient();
    HeadlessGenerationService service = new HeadlessGenerationService(client, stubRuntimeConfig());

    GenerateCommand command = commandWithOutput();
    command.lockCheck = true;
    // No --from
    int exitCode = service.run(command, false, false);

    assertThat(exitCode).isEqualTo(ExitCodes.VALIDATION);
  }

  @Test
  void forgefileFromFileLoadsAndApplies() throws Exception {
    // Create a forgefile
    java.nio.file.Path forgefilePath = tempDir.resolve("test.forgefile.json");
    java.nio.file.Files.writeString(
        forgefilePath,
        """
        {
          "groupId": "com.team",
          "artifactId": "team-svc",
          "version": "2.0.0",
          "buildTool": "maven",
          "javaVersion": "21",
          "presets": [],
          "extensions": ["io.quarkus:quarkus-rest"]
        }
        """);

    StubCatalogClient client = new StubCatalogClient();
    HeadlessGenerationService service = new HeadlessGenerationService(client, stubRuntimeConfig());

    GenerateCommand command = commandWithOutput();
    command.fromFile = forgefilePath.toString();
    int exitCode = service.run(command, true, false);

    assertThat(exitCode).isEqualTo(ExitCodes.OK);
  }

  @Test
  void saveAsWritesForgefileOnDryRun() throws Exception {
    java.nio.file.Path saveAsPath = tempDir.resolve("saved.forgefile.json");

    StubCatalogClient client = new StubCatalogClient();
    HeadlessGenerationService service = new HeadlessGenerationService(client, stubRuntimeConfig());

    GenerateCommand command = commandWithOutput();
    command.saveAsFile = saveAsPath.toString();
    command.extensions.add("io.quarkus:quarkus-rest");
    int exitCode = service.run(command, true, false);

    assertThat(exitCode).isEqualTo(ExitCodes.OK);
    assertThat(java.nio.file.Files.exists(saveAsPath)).isTrue();
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
    java.util.concurrent.CancellationException catalogLoadCancellation;
    TimeoutException presetLoadTimeout;
    CompletableFuture<Path> generationFuture =
        CompletableFuture.completedFuture(Path.of("output/demo-app"));
    int startGenerationCalls;

    StubCatalogClient() {
      // Use the no-arg constructor: no real HttpClient is created.
    }

    @Override
    CatalogData loadCatalogData(Duration timeout)
        throws ExecutionException, InterruptedException, TimeoutException {
      if (catalogLoadTimeout != null) {
        throw catalogLoadTimeout;
      }
      if (catalogLoadCancellation != null) {
        throw catalogLoadCancellation;
      }
      return CATALOG_DATA;
    }

    @Override
    Map<String, List<String>> loadBuiltInPresets(String platformStream, Duration timeout)
        throws ExecutionException, InterruptedException, TimeoutException {
      if (presetLoadTimeout != null) {
        throw presetLoadTimeout;
      }
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
