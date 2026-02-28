package dev.ayagmar.quarkusforge;

import dev.ayagmar.quarkusforge.api.CatalogData;
import dev.ayagmar.quarkusforge.api.GenerationRequest;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

final class CliHeadlessOperations implements HeadlessGenerationOperations {
  private final QuarkusForgeCli cli;

  CliHeadlessOperations(QuarkusForgeCli cli) {
    this.cli = cli;
  }

  @Override
  public CatalogData loadCatalogData()
      throws ExecutionException, InterruptedException, TimeoutException {
    return cli.loadCatalogData();
  }

  @Override
  public ProjectRequest toProjectRequest(RequestOptions options) {
    return QuarkusForgeCli.toProjectRequest(options);
  }

  @Override
  public ProjectRequest applyRecommendedPlatformStream(
      ProjectRequest request, MetadataCompatibilityContext metadataCompatibility) {
    return QuarkusForgeCli.applyRecommendedPlatformStream(request, metadataCompatibility);
  }

  @Override
  public ForgeUiState buildInitialState(
      ProjectRequest request, MetadataCompatibilityContext metadataCompatibility) {
    return QuarkusForgeCli.buildInitialState(request, metadataCompatibility);
  }

  @Override
  public List<String> resolveRequestedExtensions(
      List<String> extensionInputs, List<String> presetInputs, Set<String> knownExtensionIds) {
    return cli.resolveRequestedExtensions(extensionInputs, presetInputs, knownExtensionIds);
  }

  @Override
  public void printValidationErrors(
      ValidationReport validation, String sourceLabel, String sourceDetail) {
    QuarkusForgeCli.printValidationErrors(validation, sourceLabel, sourceDetail);
  }

  @Override
  public void printDryRunSummary(
      ProjectRequest request, List<String> extensionIds, String sourceLabel, boolean stale) {
    QuarkusForgeCli.printDryRunSummary(request, extensionIds, sourceLabel, stale);
  }

  @Override
  public Duration headlessCatalogTimeout() {
    return QuarkusForgeCli.headlessCatalogTimeout();
  }

  @Override
  public Duration headlessGenerationTimeout() {
    return QuarkusForgeCli.headlessGenerationTimeout();
  }

  @Override
  public int mapHeadlessFailureToExitCode(Throwable throwable) {
    return QuarkusForgeCli.mapHeadlessFailureToExitCode(throwable);
  }

  @Override
  public CompletableFuture<Path> startGeneration(
      GenerationRequest generationRequest, Path outputPath, Consumer<String> progressLineConsumer) {
    return cli.startHeadlessGeneration(generationRequest, outputPath, progressLineConsumer);
  }
}
