package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.api.CatalogData;
import dev.ayagmar.quarkusforge.api.CatalogSource;
import dev.ayagmar.quarkusforge.api.ExtensionDto;
import dev.ayagmar.quarkusforge.api.GenerationRequest;
import dev.ayagmar.quarkusforge.api.MetadataDto;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class HeadlessGenerationServiceTest {
  @Test
  void dryRunSkipsGenerationExecution() {
    TestOperations operations = new TestOperations();

    int exitCode =
        new QuarkusForgeCli.HeadlessGenerationService()
            .run(new QuarkusForgeCli.GenerateCommand(), true, false, operations);

    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);
    assertThat(operations.printDryRunSummaryCalls).isEqualTo(1);
    assertThat(operations.startGenerationCalls).isZero();
  }

  @Test
  void generationTimeoutCancelsRunningFuture() {
    TestOperations operations = new TestOperations();
    operations.generationTimeout = Duration.ofMillis(1);
    operations.generationFuture = new CompletableFuture<>();

    int exitCode =
        new QuarkusForgeCli.HeadlessGenerationService()
            .run(new QuarkusForgeCli.GenerateCommand(), false, false, operations);

    assertThat(exitCode).isEqualTo(QuarkusForgeCli.EXIT_CODE_NETWORK);
    assertThat(operations.startGenerationCalls).isEqualTo(1);
    assertThat(operations.generationFuture.isCancelled()).isTrue();
  }

  @Test
  void catalogTimeoutReturnsNetworkExitCodeBeforeValidation() {
    TestOperations operations = new TestOperations();
    operations.catalogLoadTimeout = new TimeoutException("timeout");

    int exitCode =
        new QuarkusForgeCli.HeadlessGenerationService()
            .run(new QuarkusForgeCli.GenerateCommand(), false, false, operations);

    assertThat(exitCode).isEqualTo(QuarkusForgeCli.EXIT_CODE_NETWORK);
    assertThat(operations.startGenerationCalls).isZero();
    assertThat(operations.printValidationErrorsCalls).isZero();
  }

  private static final class TestOperations
      implements QuarkusForgeCli.HeadlessGenerationService.Operations {
    private final MetadataDto metadata =
        new MetadataDto(
            List.of("21"),
            List.of("maven"),
            Map.of(),
            List.of(
                new MetadataDto.PlatformStream(
                    "io.quarkus.platform:3.31", "3.31", true, List.of("21"))));
    private final CatalogData catalogData =
        new CatalogData(
            metadata,
            List.of(new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest")),
            CatalogSource.LIVE,
            false,
            "");
    private final ProjectRequest request =
        new ProjectRequest(
            "com.example",
            "demo-app",
            "1.0.0-SNAPSHOT",
            "com.example.demo",
            "output",
            "io.quarkus.platform:3.31",
            "maven",
            "21");
    private final ForgeUiState validatedState =
        new ForgeUiState(
            request,
            new ValidationReport(List.of()),
            MetadataCompatibilityContext.success(metadata));

    private TimeoutException catalogLoadTimeout;
    private Duration generationTimeout = Duration.ofSeconds(1);
    private CompletableFuture<Path> generationFuture =
        CompletableFuture.completedFuture(Path.of("output/demo-app"));
    private int startGenerationCalls;
    private int printDryRunSummaryCalls;
    private int printValidationErrorsCalls;

    @Override
    public CatalogData loadCatalogData()
        throws ExecutionException, InterruptedException, TimeoutException {
      if (catalogLoadTimeout != null) {
        throw catalogLoadTimeout;
      }
      return catalogData;
    }

    @Override
    public ProjectRequest toProjectRequest(QuarkusForgeCli.RequestOptions options) {
      return request;
    }

    @Override
    public ProjectRequest applyRecommendedPlatformStream(
        ProjectRequest request, MetadataCompatibilityContext metadataCompatibility) {
      return request;
    }

    @Override
    public ForgeUiState buildInitialState(
        ProjectRequest request, MetadataCompatibilityContext metadataCompatibility) {
      return validatedState;
    }

    @Override
    public List<String> resolveRequestedExtensions(
        List<String> extensionInputs, List<String> presetInputs, Set<String> knownExtensionIds) {
      return List.of("io.quarkus:quarkus-rest");
    }

    @Override
    public void printValidationErrors(
        ValidationReport validation, String sourceLabel, String sourceDetail) {
      printValidationErrorsCalls++;
    }

    @Override
    public void printDryRunSummary(
        ProjectRequest request, List<String> extensionIds, String sourceLabel, boolean stale) {
      printDryRunSummaryCalls++;
    }

    @Override
    public Duration headlessCatalogTimeout() {
      return Duration.ofSeconds(20);
    }

    @Override
    public Duration headlessGenerationTimeout() {
      return generationTimeout;
    }

    @Override
    public int mapHeadlessFailureToExitCode(Throwable throwable) {
      return QuarkusForgeCli.EXIT_CODE_ARCHIVE;
    }

    @Override
    public CompletableFuture<Path> startGeneration(
        GenerationRequest generationRequest,
        Path outputPath,
        Consumer<String> progressLineConsumer) {
      startGenerationCalls++;
      return generationFuture;
    }
  }
}
