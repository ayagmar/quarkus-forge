package dev.ayagmar.quarkusforge.headless;

import static dev.ayagmar.quarkusforge.diagnostics.DiagnosticField.of;

import dev.ayagmar.quarkusforge.api.CatalogData;
import dev.ayagmar.quarkusforge.api.CatalogDataService;
import dev.ayagmar.quarkusforge.api.ForgeDataPaths;
import dev.ayagmar.quarkusforge.api.GenerationRequest;
import dev.ayagmar.quarkusforge.api.QuarkusApiClient;
import dev.ayagmar.quarkusforge.application.InputResolutionService;
import dev.ayagmar.quarkusforge.archive.ProjectArchiveService;
import dev.ayagmar.quarkusforge.cli.ExitCodes;
import dev.ayagmar.quarkusforge.cli.GenerateCommand;
import dev.ayagmar.quarkusforge.diagnostics.DiagnosticLogger;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ValidationError;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import dev.ayagmar.quarkusforge.forge.Forgefile;
import dev.ayagmar.quarkusforge.forge.ForgefileLock;
import dev.ayagmar.quarkusforge.forge.ForgefileStore;
import dev.ayagmar.quarkusforge.persistence.ExtensionFavoritesStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class HeadlessGenerationService implements AutoCloseable {
  private final HeadlessCatalogLoader catalogLoader;
  private final HeadlessProjectGenerator projectGenerator;
  private final HeadlessExtensionResolutionService extensionResolutionService;
  private final AutoCloseable closeOwner;

  HeadlessGenerationService(
      HeadlessCatalogLoader catalogLoader,
      HeadlessProjectGenerator projectGenerator,
      ExtensionFavoritesStore favoritesStore) {
    this(
        catalogLoader,
        projectGenerator,
        new HeadlessExtensionResolutionService(catalogLoader, favoritesStore),
        catalogLoader);
  }

  HeadlessGenerationService(
      HeadlessCatalogLoader catalogLoader,
      HeadlessProjectGenerator projectGenerator,
      ExtensionFavoritesStore favoritesStore,
      AutoCloseable closeOwner) {
    this(
        catalogLoader,
        projectGenerator,
        new HeadlessExtensionResolutionService(catalogLoader, favoritesStore),
        closeOwner);
  }

  HeadlessGenerationService(
      HeadlessCatalogLoader catalogLoader,
      HeadlessProjectGenerator projectGenerator,
      HeadlessExtensionResolutionService extensionResolutionService,
      AutoCloseable closeOwner) {
    this.catalogLoader = Objects.requireNonNull(catalogLoader);
    this.projectGenerator = Objects.requireNonNull(projectGenerator);
    this.extensionResolutionService = Objects.requireNonNull(extensionResolutionService);
    this.closeOwner = Objects.requireNonNull(closeOwner);
  }

  public static HeadlessGenerationService create(
      QuarkusApiClient apiClient,
      CatalogDataService catalogDataService,
      ProjectArchiveService projectArchiveService,
      ExtensionFavoritesStore favoritesStore) {
    return create(apiClient, catalogDataService, projectArchiveService, favoritesStore, null);
  }

  public static HeadlessGenerationService create(
      QuarkusApiClient apiClient,
      CatalogDataService catalogDataService,
      ProjectArchiveService projectArchiveService,
      ExtensionFavoritesStore favoritesStore,
      AutoCloseable closeOwner) {
    HeadlessCatalogClient client =
        new HeadlessCatalogClient(apiClient, catalogDataService, projectArchiveService);
    return new HeadlessGenerationService(
        client, client, favoritesStore, closeOwner == null ? client : closeOwner);
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

    EffectiveInputs inputs;
    try {
      inputs = loadEffectiveInputs(command);
    } catch (IllegalArgumentException illegalArgumentException) {
      diagnostics.error(
          "generate.inputs.failed", of("message", illegalArgumentException.getMessage()));
      System.err.println(illegalArgumentException.getMessage());
      return ExitCodes.VALIDATION;
    }

    Duration catalogTimeout = HeadlessTimeouts.catalogTimeout();
    CatalogData catalogData;
    try {
      catalogData = catalogLoader.loadCatalogData(catalogTimeout);
      diagnostics.info(
          "catalog.load.success",
          of("source", catalogData.sourceLabel()),
          of("stale", catalogData.stale()),
          of("detail", catalogData.detailMessage()));
    } catch (Exception e) {
      return AsyncFailureHandler.handleFailure(
          e, catalogTimeout, "catalog.load", "Failed to load extension catalog", diagnostics);
    }

    MetadataCompatibilityContext metadataCompatibility =
        MetadataCompatibilityContext.success(catalogData.metadata());
    ForgeUiState validatedState =
        InputResolutionService.resolveInitialState(inputs.template(), metadataCompatibility);
    if (!validatedState.canSubmit()) {
      diagnostics.error(
          "generate.validation.failed",
          of("errorCount", validatedState.validation().errors().size()),
          of("catalogSource", catalogData.sourceLabel()),
          of("stale", catalogData.stale()));
      HeadlessOutputPrinter.printValidationErrors(
          validatedState.validation(), catalogData.sourceLabel(), catalogData.detailMessage());
      return ExitCodes.VALIDATION;
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
        validateLockDrift(inputs.forgefile(), validatedState.request(), inputs, extensionIds);
      }
    } catch (ValidationException validationException) {
      diagnostics.error(
          "generate.extension-validation.failed",
          of("errorCount", validationException.errors().size()));
      HeadlessOutputPrinter.printValidationErrors(
          new ValidationReport(validationException.errors()),
          catalogData.sourceLabel(),
          catalogData.detailMessage());
      return ExitCodes.VALIDATION;
    } catch (Exception e) {
      return AsyncFailureHandler.handleFailure(
          e, catalogTimeout, "preset.load", "Failed to load presets", diagnostics);
    }

    boolean dryRunRequested = command.dryRun() || globalDryRun;
    if (dryRunRequested) {
      diagnostics.info(
          "generate.dry-run.validated",
          of("extensionCount", extensionIds.size()),
          of("catalogSource", catalogData.sourceLabel()),
          of("stale", catalogData.stale()));
      HeadlessOutputPrinter.printDryRunSummary(
          validatedState.request(), extensionIds, catalogData.sourceLabel());
      return persistForgefileAndReturn(
          inputs, validatedState.request(), extensionIds, diagnostics, ExitCodes.OK);
    }

    Path outputPath = HeadlessOutputPrinter.resolveProjectDirectory(validatedState.request());
    GenerationRequest generationRequest =
        new GenerationRequest(
            validatedState.request().groupId(),
            validatedState.request().artifactId(),
            validatedState.request().version(),
            validatedState.request().platformStream(),
            validatedState.request().buildTool(),
            validatedState.request().javaVersion(),
            extensionIds);

    CompletableFuture<Path> generationFuture = null;
    Duration generationTimeout = HeadlessTimeouts.generationTimeout();
    diagnostics.info(
        "generate.execute.start",
        of("outputPath", outputPath.toString()),
        of("extensionCount", extensionIds.size()));
    try {
      generationFuture =
          projectGenerator.startGeneration(generationRequest, outputPath, System.out::println);
      Path generatedProjectRoot =
          generationFuture.get(generationTimeout.toMillis(), TimeUnit.MILLISECONDS);
      diagnostics.info(
          "generate.execute.success", of("projectRoot", generatedProjectRoot.toString()));
      int persistExitCode =
          persistForgefileAndReturn(
              inputs, validatedState.request(), extensionIds, diagnostics, ExitCodes.OK);
      if (persistExitCode != ExitCodes.OK) {
        return persistExitCode;
      }
      System.out.println(
          "Generation succeeded: " + generatedProjectRoot.toAbsolutePath().normalize());
      return ExitCodes.OK;
    } catch (Exception e) {
      if (e instanceof TimeoutException && generationFuture != null) {
        generationFuture.cancel(true);
      }
      return AsyncFailureHandler.handleFailure(
          e, generationTimeout, "generate.execute", "Generation failed", diagnostics);
    }
  }

  private static EffectiveInputs loadEffectiveInputs(GenerateCommand command) {
    Forgefile template = command.explicitTemplate();
    List<String> presetInputs = new ArrayList<>(command.presets());
    List<String> extensionInputs = new ArrayList<>(command.extensions());
    Forgefile forgefile = null;
    Path forgefilePath = null;
    boolean hasFromFile = command.fromFile() != null && !command.fromFile().isBlank();
    Path saveAsPath = saveAsPathOrNull(command.saveAsFile());

    if (command.lock() && !hasFromFile && saveAsPath == null) {
      throw new IllegalArgumentException("--lock requires --from or --save-as");
    }
    if (command.lockCheck() && !hasFromFile) {
      throw new IllegalArgumentException(
          "--lock-check requires --from pointing to a Forgefile with a locked section");
    }

    if (hasFromFile) {
      forgefilePath = resolveForgefileReadPath(command.fromFile());
      forgefile = ForgefileStore.load(forgefilePath);
      template = forgefile.withOverrides(template);
      presetInputs = new ArrayList<>(new LinkedHashSet<>(safeSelections(forgefile.presets())));
      presetInputs.addAll(command.presets());
      extensionInputs =
          new ArrayList<>(new LinkedHashSet<>(safeSelections(forgefile.extensions())));
      extensionInputs.addAll(command.extensions());
    }

    if (command.lockCheck() && (forgefile == null || forgefile.locked() == null)) {
      throw new IllegalArgumentException(
          "--lock-check requires --from pointing to a Forgefile with a locked section");
    }

    return new EffectiveInputs(
        template,
        List.copyOf(presetInputs),
        List.copyOf(extensionInputs),
        forgefile,
        forgefilePath,
        command.lock(),
        command.lockCheck(),
        saveAsPath);
  }

  private static void validateLockDrift(
      Forgefile forgefile,
      ProjectRequest request,
      EffectiveInputs inputs,
      List<String> extensionIds) {
    if (forgefile == null || forgefile.locked() == null) {
      return;
    }
    ForgefileLock lock = forgefile.locked();

    List<ValidationError> errors = new ArrayList<>();
    checkDrift(errors, "platformStream", lock.platformStream(), request.platformStream());
    checkDrift(errors, "buildTool", lock.buildTool(), request.buildTool());
    checkDrift(errors, "javaVersion", lock.javaVersion(), request.javaVersion());
    List<String> lockedExtensions = lock.extensions() == null ? List.of() : lock.extensions();
    if (!HeadlessExtensionResolutionService.normalizedExtensionIdsForComparison(lockedExtensions)
        .equals(
            HeadlessExtensionResolutionService.normalizedExtensionIdsForComparison(extensionIds))) {
      errors.add(
          new ValidationError(
              "locked",
              "extensions drift: locked=" + lockedExtensions + ", request=" + extensionIds));
    }
    List<String> normalizedPresets =
        HeadlessExtensionResolutionService.normalizePresets(inputs.presetInputs());
    List<String> lockedPresets = lock.presets() == null ? List.of() : lock.presets();
    if (!HeadlessExtensionResolutionService.normalizedPresetIdsForComparison(lockedPresets)
        .equals(
            HeadlessExtensionResolutionService.normalizedPresetIdsForComparison(
                normalizedPresets))) {
      errors.add(
          new ValidationError(
              "locked",
              "presets drift: locked=" + lockedPresets + ", request=" + normalizedPresets));
    }

    if (!errors.isEmpty()) {
      errors.add(
          new ValidationError(
              "locked", "rerun with --lock to accept and update the locked section"));
      throw new ValidationException(errors);
    }
  }

  private static void checkDrift(
      List<ValidationError> errors, String field, String locked, String actual) {
    if (!Objects.equals(locked, actual)) {
      errors.add(
          new ValidationError(
              "locked", field + " drift: locked='" + locked + "', request='" + actual + "'"));
    }
  }

  private static void saveForgefileIfRequested(
      EffectiveInputs inputs, ProjectRequest request, List<String> extensionIds) {
    List<String> normalizedPresets =
        HeadlessExtensionResolutionService.normalizePresets(inputs.presetInputs());
    Forgefile forgefile =
        inputs.template().withSelections(normalizedPresets, inputs.extensionInputs());
    if (inputs.writeLock()) {
      ForgefileLock lock =
          ForgefileLock.of(
              request.platformStream(),
              request.buildTool(),
              request.javaVersion(),
              normalizedPresets,
              extensionIds);
      forgefile = forgefile.withLock(lock);
    }

    Path writePath = inputs.saveAsFile();
    if (writePath == null && inputs.writeLock() && inputs.forgefilePath() != null) {
      writePath = inputs.forgefilePath();
    }
    if (writePath == null) {
      return;
    }
    ForgefileStore.save(writePath, forgefile);
  }

  private static int persistForgefileAndReturn(
      EffectiveInputs inputs,
      ProjectRequest request,
      List<String> extensionIds,
      DiagnosticLogger diagnostics,
      int successExitCode) {
    try {
      saveForgefileIfRequested(inputs, request, extensionIds);
      return successExitCode;
    } catch (IllegalArgumentException illegalArgumentException) {
      diagnostics.error(
          "generate.forgefile.save.failed", of("message", illegalArgumentException.getMessage()));
      System.err.println("Failed to save Forgefile: " + illegalArgumentException.getMessage());
      return ExitCodes.INTERNAL;
    }
  }

  private static List<String> safeSelections(List<String> values) {
    return values == null ? List.of() : values;
  }

  private static Path resolveForgefileReadPath(String reference) {
    Path requestedPath = Path.of(reference);
    Path localPath = requestedPath.toAbsolutePath().normalize();
    if (requestedPath.isAbsolute() || Files.exists(localPath)) {
      return localPath;
    }
    return ForgeDataPaths.recipesRoot().resolve(requestedPath).toAbsolutePath().normalize();
  }

  private static Path saveAsPathOrNull(String pathValue) {
    if (pathValue == null || pathValue.isBlank()) {
      return null;
    }
    Path requestedPath = Path.of(pathValue);
    if (requestedPath.isAbsolute() || requestedPath.getParent() != null) {
      return requestedPath.toAbsolutePath().normalize();
    }
    return ForgeDataPaths.recipesRoot().resolve(requestedPath).toAbsolutePath().normalize();
  }

  private record EffectiveInputs(
      Forgefile template,
      List<String> presetInputs,
      List<String> extensionInputs,
      Forgefile forgefile,
      Path forgefilePath,
      boolean writeLock,
      boolean lockCheck,
      Path saveAsFile) {}
}
