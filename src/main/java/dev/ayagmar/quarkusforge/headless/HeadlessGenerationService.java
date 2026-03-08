package dev.ayagmar.quarkusforge.headless;

import static dev.ayagmar.quarkusforge.diagnostics.DiagnosticField.of;

import dev.ayagmar.quarkusforge.api.CatalogData;
import dev.ayagmar.quarkusforge.api.CatalogDataService;
import dev.ayagmar.quarkusforge.api.QuarkusApiClient;
import dev.ayagmar.quarkusforge.application.InputResolutionService;
import dev.ayagmar.quarkusforge.archive.ProjectArchiveService;
import dev.ayagmar.quarkusforge.cli.ExitCodes;
import dev.ayagmar.quarkusforge.cli.GenerateCommand;
import dev.ayagmar.quarkusforge.diagnostics.DiagnosticLogger;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import dev.ayagmar.quarkusforge.persistence.ExtensionFavoritesStore;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public final class HeadlessGenerationService implements AutoCloseable {
  private final HeadlessCatalogLoader catalogLoader;
  private final HeadlessExtensionResolutionService extensionResolutionService;
  private final HeadlessForgefilePersistenceService forgefilePersistenceService;
  private final HeadlessGenerationExecutionService generationExecutionService;
  private final AutoCloseable closeOwner;

  static HeadlessGenerationService create(
      HeadlessCatalogOperations catalogOperations, ExtensionFavoritesStore favoritesStore) {
    return create(catalogOperations, favoritesStore, catalogOperations);
  }

  public static HeadlessGenerationService create(
      QuarkusApiClient apiClient,
      CatalogDataService catalogDataService,
      ProjectArchiveService projectArchiveService,
      ExtensionFavoritesStore favoritesStore,
      AutoCloseable closeOwner) {
    HeadlessCatalogClient catalogClient =
        new HeadlessCatalogClient(apiClient, catalogDataService, projectArchiveService);
    return create(catalogClient, favoritesStore, closeOwner == null ? catalogClient : closeOwner);
  }

  static HeadlessGenerationService create(
      HeadlessCatalogOperations catalogOperations,
      ExtensionFavoritesStore favoritesStore,
      AutoCloseable closeOwner) {
    return new HeadlessGenerationService(
        catalogOperations,
        new HeadlessExtensionResolutionService(catalogOperations, favoritesStore),
        new HeadlessForgefilePersistenceService(),
        new HeadlessGenerationExecutionService(catalogOperations),
        closeOwner);
  }

  private HeadlessGenerationService(
      HeadlessCatalogLoader catalogLoader,
      HeadlessExtensionResolutionService extensionResolutionService,
      HeadlessForgefilePersistenceService forgefilePersistenceService,
      HeadlessGenerationExecutionService generationExecutionService,
      AutoCloseable closeOwner) {
    this.catalogLoader = Objects.requireNonNull(catalogLoader);
    this.extensionResolutionService = Objects.requireNonNull(extensionResolutionService);
    this.forgefilePersistenceService = Objects.requireNonNull(forgefilePersistenceService);
    this.generationExecutionService = Objects.requireNonNull(generationExecutionService);
    this.closeOwner = Objects.requireNonNull(closeOwner);
  }

  @Override
  public void close() {
    try {
      closeOwner.close();
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to close headless generation resources", exception);
    }
  }

  public int run(GenerateCommand command, boolean globalDryRun, boolean verbose) {
    DiagnosticLogger diagnostics = DiagnosticLogger.create(verbose);
    diagnostics.info(
        "generate.start", of("mode", command.dryRun() || globalDryRun ? "dry-run" : "apply"));

    HeadlessGenerationInputs inputs;
    try {
      inputs = HeadlessGenerationInputs.fromCommand(command);
    } catch (IllegalArgumentException illegalArgumentException) {
      diagnostics.error(
          "generate.inputs.failed", of("message", illegalArgumentException.getMessage()));
      System.err.println(illegalArgumentException.getMessage());
      return ExitCodes.VALIDATION;
    }

    Duration catalogTimeout = HeadlessTimeouts.catalogTimeout();
    CatalogData catalogData;
    try {
      catalogData = loadCatalogData(catalogTimeout, diagnostics);
    } catch (ExecutionException | InterruptedException | TimeoutException exception) {
      return AsyncFailureHandler.handleFailure(
          exception,
          catalogTimeout,
          "catalog.load",
          "Failed to load extension catalog",
          diagnostics);
    } catch (RuntimeException exception) {
      return AsyncFailureHandler.handleFailure(
          exception,
          catalogTimeout,
          "catalog.load",
          "Failed to load extension catalog",
          diagnostics);
    }

    ForgeUiState validatedState = resolveValidatedState(inputs, catalogData);
    if (!validatedState.canSubmit()) {
      return validationFailure(
          validatedState.validation(),
          catalogData.sourceLabel(),
          catalogData.detailMessage(),
          diagnostics);
    }

    List<String> extensionIds;
    try {
      extensionIds =
          extensionResolutionService.resolveExtensionIds(
              validatedState.request().platformStream(),
              inputs.extensionInputs(),
              inputs.presetInputs(),
              HeadlessExtensionResolutionService.knownExtensionIds(catalogData),
              catalogTimeout);
      if (inputs.lockCheck()) {
        forgefilePersistenceService.validateLockDrift(
            inputs, validatedState.request(), extensionIds);
      }
    } catch (ValidationException validationException) {
      return extensionValidationFailure(validationException, catalogData, diagnostics);
    } catch (Exception exception) {
      return AsyncFailureHandler.handleFailure(
          exception, catalogTimeout, "preset.load", "Failed to load presets", diagnostics);
    }

    if (command.dryRun() || globalDryRun) {
      return handleDryRun(inputs, validatedState.request(), extensionIds, catalogData, diagnostics);
    }
    return executeGeneration(inputs, validatedState.request(), extensionIds, diagnostics);
  }

  private CatalogData loadCatalogData(Duration catalogTimeout, DiagnosticLogger diagnostics)
      throws ExecutionException, InterruptedException, TimeoutException {
    CatalogData catalogData = catalogLoader.loadCatalogData(catalogTimeout);
    diagnostics.info(
        "catalog.load.success",
        of("source", catalogData.sourceLabel()),
        of("stale", catalogData.stale()),
        of("detail", catalogData.detailMessage()));
    return catalogData;
  }

  private static ForgeUiState resolveValidatedState(
      HeadlessGenerationInputs inputs, CatalogData catalogData) {
    MetadataCompatibilityContext metadataCompatibility =
        MetadataCompatibilityContext.success(catalogData.metadata());
    return InputResolutionService.resolveInitialState(inputs.template(), metadataCompatibility);
  }

  private static int validationFailure(
      ValidationReport validation,
      String sourceLabel,
      String detailMessage,
      DiagnosticLogger diagnostics) {
    diagnostics.error("generate.validation.failed", of("errorCount", validation.errors().size()));
    HeadlessOutputPrinter.printValidationErrors(validation, sourceLabel, detailMessage);
    return ExitCodes.VALIDATION;
  }

  private static int extensionValidationFailure(
      ValidationException validationException,
      CatalogData catalogData,
      DiagnosticLogger diagnostics) {
    diagnostics.error(
        "generate.extension-validation.failed",
        of("errorCount", validationException.errors().size()));
    HeadlessOutputPrinter.printValidationErrors(
        new ValidationReport(validationException.errors()),
        catalogData.sourceLabel(),
        catalogData.detailMessage());
    return ExitCodes.VALIDATION;
  }

  private int handleDryRun(
      HeadlessGenerationInputs inputs,
      ProjectRequest request,
      List<String> extensionIds,
      CatalogData catalogData,
      DiagnosticLogger diagnostics) {
    diagnostics.info(
        "generate.dry-run.validated",
        of("extensionCount", extensionIds.size()),
        of("catalogSource", catalogData.sourceLabel()),
        of("stale", catalogData.stale()));
    HeadlessOutputPrinter.printDryRunSummary(request, extensionIds, catalogData.sourceLabel());
    return persistForgefileAndReturn(inputs, request, extensionIds, diagnostics, ExitCodes.OK);
  }

  private int executeGeneration(
      HeadlessGenerationInputs inputs,
      ProjectRequest request,
      List<String> extensionIds,
      DiagnosticLogger diagnostics) {
    Duration generationTimeout = HeadlessTimeouts.generationTimeout();
    try {
      Path generatedProjectRoot =
          generationExecutionService.execute(request, extensionIds, generationTimeout, diagnostics);
      int persistExitCode =
          persistForgefileAndReturn(inputs, request, extensionIds, diagnostics, ExitCodes.OK);
      if (persistExitCode != ExitCodes.OK) {
        return persistExitCode;
      }
      System.out.println(
          "Generation succeeded: " + generatedProjectRoot.toAbsolutePath().normalize());
      return ExitCodes.OK;
    } catch (Exception exception) {
      return AsyncFailureHandler.handleFailure(
          exception, generationTimeout, "generate.execute", "Generation failed", diagnostics);
    }
  }

  private int persistForgefileAndReturn(
      HeadlessGenerationInputs inputs,
      ProjectRequest request,
      List<String> extensionIds,
      DiagnosticLogger diagnostics,
      int successExitCode) {
    try {
      forgefilePersistenceService.persist(inputs, request, extensionIds);
      return successExitCode;
    } catch (IllegalArgumentException illegalArgumentException) {
      diagnostics.error(
          "generate.forgefile.save.failed", of("message", illegalArgumentException.getMessage()));
      System.err.println("Failed to save Forgefile: " + illegalArgumentException.getMessage());
      return ExitCodes.INTERNAL;
    }
  }
}
