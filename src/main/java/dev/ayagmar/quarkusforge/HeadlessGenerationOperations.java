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

interface HeadlessGenerationOperations {
  CatalogData loadCatalogData() throws ExecutionException, InterruptedException, TimeoutException;

  ProjectRequest toProjectRequest(RequestOptions options);

  ProjectRequest applyRecommendedPlatformStream(
      ProjectRequest request, MetadataCompatibilityContext metadataCompatibility);

  ForgeUiState buildInitialState(
      ProjectRequest request, MetadataCompatibilityContext metadataCompatibility);

  List<String> resolveRequestedExtensions(
      List<String> extensionInputs, List<String> presetInputs, Set<String> knownExtensionIds);

  void printValidationErrors(ValidationReport validation, String sourceLabel, String sourceDetail);

  void printDryRunSummary(
      ProjectRequest request, List<String> extensionIds, String sourceLabel, boolean stale);

  Duration headlessCatalogTimeout();

  Duration headlessGenerationTimeout();

  int mapHeadlessFailureToExitCode(Throwable throwable);

  CompletableFuture<Path> startGeneration(
      GenerationRequest generationRequest, Path outputPath, Consumer<String> progressLineConsumer);
}
