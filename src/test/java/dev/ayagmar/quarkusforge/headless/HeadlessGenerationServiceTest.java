package dev.ayagmar.quarkusforge.headless;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.SystemPropertyExtension;
import dev.ayagmar.quarkusforge.api.ApiClientException;
import dev.ayagmar.quarkusforge.api.CatalogData;
import dev.ayagmar.quarkusforge.api.CatalogSource;
import dev.ayagmar.quarkusforge.api.ExtensionDto;
import dev.ayagmar.quarkusforge.api.GenerationRequest;
import dev.ayagmar.quarkusforge.api.JsonSupport;
import dev.ayagmar.quarkusforge.api.MetadataDto;
import dev.ayagmar.quarkusforge.api.PlatformStream;
import dev.ayagmar.quarkusforge.cli.ExitCodes;
import dev.ayagmar.quarkusforge.cli.GenerateCommand;
import dev.ayagmar.quarkusforge.domain.CliPrefill;
import dev.ayagmar.quarkusforge.forge.Forgefile;
import dev.ayagmar.quarkusforge.forge.ForgefileStore;
import dev.ayagmar.quarkusforge.persistence.ExtensionFavoritesStore;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class HeadlessGenerationServiceTest {
  private static final String HEADLESS_GENERATION_TIMEOUT_PROPERTY =
      "quarkus.forge.headless.generation-timeout-ms";

  @RegisterExtension final SystemPropertyExtension systemProperties = new SystemPropertyExtension();

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
          List.of(
              new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest"),
              new ExtensionDto("io.quarkus:quarkus-arc", "CDI", "arc")),
          CatalogSource.LIVE,
          false,
          "");

  @Test
  void dryRunSkipsGenerationExecution() {
    StubCatalogOperations client = new StubCatalogOperations();
    HeadlessGenerationService service = serviceWith(client);

    GenerateCommand command = commandWithOutput();
    int exitCode = service.run(command, true, false);

    assertThat(exitCode).isEqualTo(ExitCodes.OK);
    assertThat(client.startGenerationCalls).isZero();
  }

  @Test
  void generationTimeoutCancelsRunningFuture() {
    StubCatalogOperations client = new StubCatalogOperations();
    client.generationFuture = new CompletableFuture<>();
    HeadlessGenerationService service = serviceWith(client);

    GenerateCommand command = commandWithOutput();
    systemProperties.set(HEADLESS_GENERATION_TIMEOUT_PROPERTY, 1L);

    int exitCode = service.run(command, false, false);

    assertThat(exitCode).isEqualTo(ExitCodes.NETWORK);
    assertThat(client.startGenerationCalls).isEqualTo(1);
    assertThat(client.generationFuture.isCancelled()).isTrue();
  }

  @Test
  void catalogTimeoutReturnsNetworkExitCodeBeforeValidation() {
    StubCatalogOperations client = new StubCatalogOperations();
    client.catalogLoadTimeout = new TimeoutException("timeout");
    HeadlessGenerationService service = serviceWith(client);

    GenerateCommand command = commandWithOutput();
    int exitCode = service.run(command, false, false);

    assertThat(exitCode).isEqualTo(ExitCodes.NETWORK);
    assertThat(client.startGenerationCalls).isZero();
  }

  @Test
  void catalogCancellationReturnsCancelledExitCode() {
    StubCatalogOperations client = new StubCatalogOperations();
    client.catalogLoadCancellation = new java.util.concurrent.CancellationException("cancelled");
    HeadlessGenerationService service = serviceWith(client);

    GenerateCommand command = commandWithOutput();
    int exitCode = service.run(command, false, false);

    assertThat(exitCode).isEqualTo(ExitCodes.CANCELLED);
  }

  @Test
  void validationFailureReturnsValidationExitCode() {
    StubCatalogOperations client = new StubCatalogOperations();
    HeadlessGenerationService service = serviceWith(client);

    GenerateCommand command = commandWithOutput();
    command.setCliPrefill(
        new CliPrefill(
            "com.example",
            "demo-app",
            "1.0.0-SNAPSHOT",
            null,
            tempDir.toString(),
            "io.quarkus.platform:3.31",
            "ant",
            "21"));
    int exitCode = service.run(command, false, false);

    assertThat(exitCode).isEqualTo(ExitCodes.VALIDATION);
  }

  @Test
  void unknownExtensionReturnsValidationExitCode() {
    StubCatalogOperations client = new StubCatalogOperations();
    HeadlessGenerationService service = serviceWith(client);

    GenerateCommand command = commandWithOutput();
    command.extensions().add("io.quarkus:nonexistent");
    int exitCode = service.run(command, false, false);

    assertThat(exitCode).isEqualTo(ExitCodes.VALIDATION);
  }

  @Test
  void generationExecutionFailureReturnsCorrectExitCode() {
    StubCatalogOperations client = new StubCatalogOperations();
    client.generationFuture =
        CompletableFuture.failedFuture(new ApiClientException("connection refused", null));
    HeadlessGenerationService service = serviceWith(client);

    GenerateCommand command = commandWithOutput();
    int exitCode = service.run(command, false, false);

    assertThat(exitCode).isEqualTo(ExitCodes.NETWORK);
  }

  @Test
  void verboseModeEmitsDiagnostics() {
    StubCatalogOperations client = new StubCatalogOperations();
    HeadlessGenerationService service = serviceWith(client);

    GenerateCommand command = commandWithOutput();
    // verbose mode should not change exit code
    int exitCode = service.run(command, true, true);

    assertThat(exitCode).isEqualTo(ExitCodes.OK);
  }

  @Test
  void blankExtensionInputReturnsValidationExitCode() {
    StubCatalogOperations client = new StubCatalogOperations();
    HeadlessGenerationService service = serviceWith(client);

    GenerateCommand command = commandWithOutput();
    command.extensions().add("   ");
    int exitCode = service.run(command, true, false);

    assertThat(exitCode).isEqualTo(ExitCodes.VALIDATION);
  }

  @Test
  void favoritesPresetResolvesFromStore() throws Exception {
    Path favoritesFile = tempDir.resolve("favorites.json");
    java.nio.file.Files.createDirectories(favoritesFile.getParent());
    java.nio.file.Files.writeString(
        favoritesFile,
        """
        {"schemaVersion":1,"favoriteExtensionIds":["io.quarkus:quarkus-rest"]}
        """);

    StubCatalogOperations client = new StubCatalogOperations();
    HeadlessGenerationService service =
        serviceWith(client, ExtensionFavoritesStore.fileBacked(favoritesFile));

    GenerateCommand command = commandWithOutput();
    command.presets().add("favorites");
    int exitCode = service.run(command, true, false);

    assertThat(exitCode).isEqualTo(ExitCodes.OK);
  }

  @Test
  void favoritesPresetIgnoresRemovedFavoriteIds() throws Exception {
    Path favoritesFile = tempDir.resolve("favorites.json");
    java.nio.file.Files.createDirectories(favoritesFile.getParent());
    java.nio.file.Files.writeString(
        favoritesFile,
        """
        {"schemaVersion":1,"favoriteExtensionIds":["io.quarkus:quarkus-rest","io.quarkus:removed"]}
        """);

    StubCatalogOperations client = new StubCatalogOperations();
    HeadlessGenerationService service =
        serviceWith(client, ExtensionFavoritesStore.fileBacked(favoritesFile));

    GenerateCommand command = commandWithOutput();
    command.presets().add("favorites");
    int exitCode = service.run(command, true, false);

    assertThat(exitCode).isEqualTo(ExitCodes.OK);
  }

  @Test
  void generationResolvesTildeOutputDirectoryAgainstUserHome() {
    systemProperties.set("user.home", tempDir.resolve("home").toString());
    StubCatalogOperations client = new StubCatalogOperations();
    HeadlessGenerationService service = serviceWith(client);

    GenerateCommand command = commandWithOutput();
    command.setCliPrefill(
        new CliPrefill(
            "com.example",
            "demo-app",
            "1.0.0-SNAPSHOT",
            null,
            "~/Projects/Quarkus",
            "io.quarkus.platform:3.31",
            "maven",
            "21"));

    int exitCode = service.run(command, false, false);

    assertThat(exitCode).isEqualTo(ExitCodes.OK);
    assertThat(client.lastOutputPath)
        .isEqualTo(tempDir.resolve("home/Projects/Quarkus/demo-app").normalize());
  }

  @Test
  void builtInPresetResolvesExtensions() {
    StubCatalogOperations client = new StubCatalogOperations();
    HeadlessGenerationService service = serviceWith(client);

    GenerateCommand command = commandWithOutput();
    command.presets().add("web");
    int exitCode = service.run(command, true, false);

    assertThat(exitCode).isEqualTo(ExitCodes.OK);
    // "web" preset resolves to quarkus-rest which is in the catalog
  }

  @Test
  void presetNamesAreTrimmedAndMatchedCaseInsensitively() {
    StubCatalogOperations client = new StubCatalogOperations();
    HeadlessGenerationService service = serviceWith(client);

    GenerateCommand command = commandWithOutput();
    command.presets().add("  Web  ");
    int exitCode = service.run(command, true, false);

    assertThat(exitCode).isEqualTo(ExitCodes.OK);
  }

  @Test
  void unknownPresetReturnsValidationExitCode() {
    StubCatalogOperations client = new StubCatalogOperations();
    HeadlessGenerationService service = serviceWith(client);

    GenerateCommand command = commandWithOutput();
    command.presets().add("nonexistent");
    int exitCode = service.run(command, true, false);

    assertThat(exitCode).isEqualTo(ExitCodes.VALIDATION);
  }

  @Test
  void blankPresetInputIsSkipped() {
    StubCatalogOperations client = new StubCatalogOperations();
    HeadlessGenerationService service = serviceWith(client);

    GenerateCommand command = commandWithOutput();
    command.presets().add("   ");
    int exitCode = service.run(command, true, false);

    assertThat(exitCode).isEqualTo(ExitCodes.OK);
  }

  @Test
  void presetLoadFailureReturnsNetworkExitCode() {
    StubCatalogOperations client = new StubCatalogOperations();
    client.presetLoadTimeout = new TimeoutException("presets timeout");
    HeadlessGenerationService service = serviceWith(client);

    GenerateCommand command = commandWithOutput();
    command.presets().add("web"); // non-favorites preset triggers loadBuiltInPresets
    int exitCode = service.run(command, false, false);

    assertThat(exitCode).isEqualTo(ExitCodes.NETWORK);
    assertThat(client.lastPresetPlatformStream).isEqualTo("io.quarkus.platform:3.31");
  }

  @Test
  void synchronousGenerationStartFailureReturnsInternalExitCode() {
    StubCatalogOperations client = new StubCatalogOperations();
    client.startGenerationFailure = new IllegalStateException("boom");
    HeadlessGenerationService service = serviceWith(client);

    int exitCode = service.run(commandWithOutput(), false, false);

    assertThat(exitCode).isEqualTo(ExitCodes.INTERNAL);
  }

  @Test
  void lockWithoutFromOrSaveAsReturnsValidation() {
    StubCatalogOperations client = new StubCatalogOperations();
    HeadlessGenerationService service = serviceWith(client);

    GenerateCommand command = commandWithOutput();
    command.setLock(true);
    // No --from or --save-as
    int exitCode = service.run(command, false, false);

    assertThat(exitCode).isEqualTo(ExitCodes.VALIDATION);
  }

  @Test
  void lockCheckWithoutFromReturnsValidation() {
    StubCatalogOperations client = new StubCatalogOperations();
    HeadlessGenerationService service = serviceWith(client);

    GenerateCommand command = commandWithOutput();
    command.setLockCheck(true);
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

    StubCatalogOperations client = new StubCatalogOperations();
    HeadlessGenerationService service = serviceWith(client);

    GenerateCommand command = commandWithOutput();
    command.setFromFile(forgefilePath.toString());
    int exitCode = service.run(command, true, false);

    assertThat(exitCode).isEqualTo(ExitCodes.OK);
  }

  @Test
  void lockCheckIgnoresExtensionOrderingWhenSelectionsAreEquivalent() throws Exception {
    Path forgefilePath = tempDir.resolve("equivalent-lock-order.json");
    java.nio.file.Files.writeString(
        forgefilePath,
        """
        {
          "groupId": "com.team",
          "artifactId": "ordered-app",
          "buildTool": "maven",
          "javaVersion": "21",
          "presets": [],
          "extensions": ["io.quarkus:quarkus-arc", "io.quarkus:quarkus-rest"],
          "locked": {
            "platformStream": "io.quarkus.platform:3.31",
            "buildTool": "maven",
            "javaVersion": "21",
            "presets": [],
            "extensions": ["io.quarkus:quarkus-rest", "io.quarkus:quarkus-arc"]
          }
        }
        """);

    StubCatalogOperations client = new StubCatalogOperations();
    HeadlessGenerationService service = serviceWith(client);

    GenerateCommand command = commandWithOutput();
    command.setFromFile(forgefilePath.toString());
    command.setLockCheck(true);
    int exitCode = service.run(command, true, false);

    assertThat(exitCode).isEqualTo(ExitCodes.OK);
  }

  @Test
  void lockCheckIgnoresPresetOrderingWhenSelectionsAreEquivalent() throws Exception {
    Path favoritesFile = tempDir.resolve("favorites.json");
    java.nio.file.Files.createDirectories(favoritesFile.getParent());
    java.nio.file.Files.writeString(
        favoritesFile,
        """
        {"schemaVersion":1,"favoriteExtensionIds":["io.quarkus:quarkus-rest"]}
        """);
    Path forgefilePath = tempDir.resolve("equivalent-preset-lock-order.json");
    java.nio.file.Files.writeString(
        forgefilePath,
        """
        {
          "groupId": "com.team",
          "artifactId": "preset-app",
          "buildTool": "maven",
          "javaVersion": "21",
          "presets": ["favorites", "web"],
          "extensions": [],
          "locked": {
            "platformStream": "io.quarkus.platform:3.31",
            "buildTool": "maven",
            "javaVersion": "21",
            "presets": ["web", "favorites"],
            "extensions": ["io.quarkus:quarkus-rest"]
          }
        }
        """);

    StubCatalogOperations client = new StubCatalogOperations();
    HeadlessGenerationService service =
        serviceWith(client, ExtensionFavoritesStore.fileBacked(favoritesFile));

    GenerateCommand command = commandWithOutput();
    command.setFromFile(forgefilePath.toString());
    command.setLockCheck(true);
    int exitCode = service.run(command, true, false);

    assertThat(exitCode).isEqualTo(ExitCodes.OK);
  }

  @Test
  void saveAsWritesForgefileOnDryRun() throws Exception {
    java.nio.file.Path saveAsPath = tempDir.resolve("saved.forgefile.json");

    StubCatalogOperations client = new StubCatalogOperations();
    HeadlessGenerationService service = serviceWith(client);

    GenerateCommand command = commandWithOutput();
    command.setSaveAsFile(saveAsPath.toString());
    command.extensions().add("io.quarkus:quarkus-rest");
    int exitCode = service.run(command, true, false);

    assertThat(exitCode).isEqualTo(ExitCodes.OK);
    assertThat(java.nio.file.Files.exists(saveAsPath)).isTrue();
  }

  @Test
  void saveAsFromExistingForgefilePreservesOmittedFieldsAndExistingLock() throws Exception {
    Path sourcePath = tempDir.resolve("source.forgefile.json");
    Path saveAsPath = tempDir.resolve("copy.forgefile.json");
    java.nio.file.Files.writeString(
        sourcePath,
        """
        {
          "artifactId": "team-svc",
          "javaVersion": "21",
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

    StubCatalogOperations client = new StubCatalogOperations();
    HeadlessGenerationService service = serviceWith(client);

    GenerateCommand command = new GenerateCommand();
    command.setFromFile(sourcePath.toString());
    command.setSaveAsFile(saveAsPath.toString());

    int exitCode = service.run(command, true, false);

    Forgefile saved = ForgefileStore.load(saveAsPath);
    Map<String, Object> root = JsonSupport.parseObject(java.nio.file.Files.readString(saveAsPath));
    assertThat(exitCode).isEqualTo(ExitCodes.OK);
    assertThat(saved.groupId()).isNull();
    assertThat(saved.version()).isNull();
    assertThat(saved.outputDirectory()).isNull();
    assertThat(saved.buildTool()).isNull();
    assertThat(saved.locked()).isNotNull();
    assertThat(saved.locked().buildTool()).isEqualTo("maven");
    assertThat(root).doesNotContainKeys("groupId", "version", "outputDirectory", "buildTool");
  }

  @Test
  void lockUpdateOnExistingForgefilePreservesOmittedTopLevelFields() throws Exception {
    Path sourcePath = tempDir.resolve("locked-source.forgefile.json");
    java.nio.file.Files.writeString(
        sourcePath,
        """
        {
          "artifactId": "team-svc",
          "javaVersion": "21",
          "extensions": []
        }
        """);

    StubCatalogOperations client = new StubCatalogOperations();
    HeadlessGenerationService service = serviceWith(client);

    GenerateCommand command = new GenerateCommand();
    command.setFromFile(sourcePath.toString());
    command.setLock(true);

    int exitCode = service.run(command, true, false);

    Forgefile saved = ForgefileStore.load(sourcePath);
    Map<String, Object> root = JsonSupport.parseObject(java.nio.file.Files.readString(sourcePath));
    assertThat(exitCode).isEqualTo(ExitCodes.OK);
    assertThat(saved.groupId()).isNull();
    assertThat(saved.version()).isNull();
    assertThat(saved.outputDirectory()).isNull();
    assertThat(saved.buildTool()).isNull();
    assertThat(saved.locked()).isNotNull();
    assertThat(saved.locked().buildTool()).isEqualTo("maven");
    assertThat(saved.locked().javaVersion()).isEqualTo("21");
    assertThat(root).doesNotContainKeys("groupId", "version", "outputDirectory", "buildTool");
  }

  @Test
  void saveAsFailureReturnsInternalExitCode() {
    Path directoryTarget = tempDir.resolve("forgefile-dir");
    directoryTarget.toFile().mkdirs();

    StubCatalogOperations client = new StubCatalogOperations();
    HeadlessGenerationService service = serviceWith(client);

    GenerateCommand command = commandWithOutput();
    command.setSaveAsFile(directoryTarget.toString());

    int exitCode = service.run(command, true, false);

    assertThat(exitCode).isEqualTo(ExitCodes.INTERNAL);
  }

  private GenerateCommand commandWithOutput() {
    GenerateCommand cmd = new GenerateCommand();
    cmd.setCliPrefill(
        new CliPrefill(
            "com.example",
            "demo-app",
            "1.0.0-SNAPSHOT",
            null,
            tempDir.toString(),
            "io.quarkus.platform:3.31",
            "maven",
            "21"));
    return cmd;
  }

  private static HeadlessGenerationService serviceWith(StubCatalogOperations client) {
    return serviceWith(client, ExtensionFavoritesStore.inMemory());
  }

  private static HeadlessGenerationService serviceWith(
      StubCatalogOperations client, ExtensionFavoritesStore favoritesStore) {
    return new HeadlessGenerationService(client, client, favoritesStore);
  }

  private static final class StubCatalogOperations
      implements HeadlessCatalogLoader, HeadlessProjectGenerator {
    TimeoutException catalogLoadTimeout;
    java.util.concurrent.CancellationException catalogLoadCancellation;
    TimeoutException presetLoadTimeout;
    CompletableFuture<Path> generationFuture =
        CompletableFuture.completedFuture(Path.of("output/demo-app"));
    RuntimeException startGenerationFailure;
    int startGenerationCalls;
    String lastPresetPlatformStream;
    Path lastOutputPath;

    @Override
    public CatalogData loadCatalogData(Duration timeout)
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
    public Map<String, List<String>> loadBuiltInPresets(String platformStream, Duration timeout)
        throws ExecutionException, InterruptedException, TimeoutException {
      lastPresetPlatformStream = platformStream;
      if (presetLoadTimeout != null) {
        throw presetLoadTimeout;
      }
      return Map.of("web", List.of("io.quarkus:quarkus-rest"));
    }

    @Override
    public CompletableFuture<Path> startGeneration(
        GenerationRequest generationRequest,
        Path outputPath,
        Consumer<String> progressLineConsumer) {
      if (startGenerationFailure != null) {
        throw startGenerationFailure;
      }
      startGenerationCalls++;
      lastOutputPath = outputPath;
      return generationFuture;
    }

    @Override
    public void close() {}
  }
}
