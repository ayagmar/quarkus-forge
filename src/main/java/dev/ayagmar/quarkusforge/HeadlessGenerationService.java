package dev.ayagmar.quarkusforge;

import dev.ayagmar.quarkusforge.api.CatalogData;
import dev.ayagmar.quarkusforge.api.ExtensionDto;
import dev.ayagmar.quarkusforge.api.ForgeDataPaths;
import dev.ayagmar.quarkusforge.api.GenerationRequest;
import dev.ayagmar.quarkusforge.diagnostics.DiagnosticField;
import dev.ayagmar.quarkusforge.diagnostics.DiagnosticLogger;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ValidationError;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import dev.ayagmar.quarkusforge.ui.ExtensionFavoritesStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import picocli.CommandLine;

final class HeadlessGenerationService {
  private static final String PRESET_FAVORITES = "favorites";

  private final HeadlessCatalogClient catalogClient;
  private final RuntimeConfig runtimeConfig;

  HeadlessGenerationService(HeadlessCatalogClient catalogClient, RuntimeConfig runtimeConfig) {
    this.catalogClient = catalogClient;
    this.runtimeConfig = runtimeConfig;
  }

  int run(GenerateCommand command, boolean globalDryRun, boolean verbose) {
    DiagnosticLogger diagnostics = DiagnosticLogger.create(verbose);
    diagnostics.info(
        "generate.start", df("mode", command.dryRun || globalDryRun ? "dry-run" : "apply"));

    EffectiveGenerateInputs effectiveInputs;
    try {
      effectiveInputs = loadEffectiveInputs(command);
    } catch (IllegalArgumentException illegalArgumentException) {
      diagnostics.error(
          "generate.inputs.failed", df("message", illegalArgumentException.getMessage()));
      System.err.println(illegalArgumentException.getMessage());
      return QuarkusForgeCli.EXIT_CODE_VALIDATION;
    }

    Duration catalogTimeout = HeadlessTimeouts.catalogTimeout();
    CatalogData catalogData;
    try {
      catalogData = catalogClient.loadCatalogData(catalogTimeout);
      diagnostics.info(
          "catalog.load.success",
          df("source", catalogData.source().label()),
          df("stale", catalogData.stale()),
          df("detail", catalogData.detailMessage()));
    } catch (Exception e) {
      return AsyncFailureHandler.handleFailure(
          e,
          catalogTimeout,
          "catalog.load",
          "Failed to load extension catalog",
          diagnostics,
          ProjectRequestFactory::mapFailureToExitCode);
    }

    ProjectRequest request = ProjectRequestFactory.fromOptions(effectiveInputs.requestOptions());
    MetadataCompatibilityContext metadataCompatibility =
        MetadataCompatibilityContext.success(catalogData.metadata());
    ProjectRequest requestWithResolvedStream =
        ProjectRequestFactory.applyRecommendedPlatformStream(request, metadataCompatibility);
    ForgeUiState validatedState =
        ProjectRequestFactory.buildInitialState(requestWithResolvedStream, metadataCompatibility);
    if (!validatedState.canSubmit()) {
      diagnostics.error(
          "generate.validation.failed",
          df("errorCount", validatedState.validation().errors().size()),
          df("catalogSource", catalogData.source().label()));
      HeadlessOutputPrinter.printValidationErrors(
          validatedState.validation(),
          catalogData.source().label() + (catalogData.stale() ? " [stale]" : ""),
          catalogData.detailMessage());
      return QuarkusForgeCli.EXIT_CODE_VALIDATION;
    }

    Set<String> knownExtensionIds = new LinkedHashSet<>();
    for (ExtensionDto extension : catalogData.extensions()) {
      knownExtensionIds.add(extension.id());
    }

    Map<String, List<String>> presetExtensionsByName = Map.of();
    if (requiresBuiltInPresets(effectiveInputs.presetInputs())) {
      try {
        presetExtensionsByName =
            catalogClient.loadBuiltInPresets(
                validatedState.request().platformStream(), catalogTimeout);
      } catch (Exception e) {
        return AsyncFailureHandler.handleFailure(
            e,
            catalogTimeout,
            "preset.load",
            "Failed to load presets",
            diagnostics,
            ProjectRequestFactory::mapFailureToExitCode);
      }
    }

    List<String> extensionIds;
    try {
      extensionIds =
          resolveRequestedExtensions(
              effectiveInputs.extensionInputs(),
              effectiveInputs.presetInputs(),
              knownExtensionIds,
              presetExtensionsByName);
      validateLockDrift(
          effectiveInputs.lock(),
          effectiveInputs.refreshLock(),
          validatedState.request(),
          effectiveInputs.presetInputs(),
          extensionIds);
    } catch (ValidationException validationException) {
      diagnostics.error(
          "generate.extension-validation.failed",
          df("errorCount", validationException.errors().size()));
      HeadlessOutputPrinter.printValidationErrors(
          new ValidationReport(validationException.errors()),
          catalogData.source().label() + (catalogData.stale() ? " [stale]" : ""),
          catalogData.detailMessage());
      return QuarkusForgeCli.EXIT_CODE_VALIDATION;
    }

    boolean dryRunRequested = command.dryRun || globalDryRun;
    if (dryRunRequested) {
      diagnostics.info(
          "generate.dry-run.validated",
          df("extensionCount", extensionIds.size()),
          df("catalogSource", catalogData.source().label()),
          df("stale", catalogData.stale()));
      HeadlessOutputPrinter.printDryRunSummary(
          validatedState.request(),
          extensionIds,
          catalogData.source().label(),
          catalogData.stale());
      writeRecipeAndLockIfRequested(effectiveInputs, validatedState.request(), extensionIds);
      return CommandLine.ExitCode.OK;
    }

    Path outputPath =
        Path.of(validatedState.request().outputDirectory())
            .resolve(validatedState.request().artifactId())
            .normalize();
    GenerationRequest generationRequest =
        new GenerationRequest(
            validatedState.request().groupId(),
            validatedState.request().artifactId(),
            validatedState.request().version(),
            validatedState.request().platformStream(),
            validatedState.request().buildTool(),
            validatedState.request().javaVersion(),
            extensionIds);

    CompletableFuture<Path> generationFuture =
        catalogClient.startGeneration(generationRequest, outputPath, System.out::println);
    Duration generationTimeout = HeadlessTimeouts.generationTimeout();
    diagnostics.info(
        "generate.execute.start",
        df("outputPath", outputPath.toString()),
        df("extensionCount", extensionIds.size()));
    try {
      Path generatedProjectRoot =
          generationFuture.get(generationTimeout.toMillis(), TimeUnit.MILLISECONDS);
      diagnostics.info(
          "generate.execute.success", df("projectRoot", generatedProjectRoot.toString()));
      writeRecipeAndLockIfRequested(effectiveInputs, validatedState.request(), extensionIds);
      System.out.println(
          "Generation succeeded: " + generatedProjectRoot.toAbsolutePath().normalize());
      return CommandLine.ExitCode.OK;
    } catch (Exception e) {
      if (e instanceof java.util.concurrent.TimeoutException) {
        generationFuture.cancel(true);
      }
      return AsyncFailureHandler.handleFailure(
          e,
          generationTimeout,
          "generate.execute",
          "Generation failed",
          diagnostics,
          ProjectRequestFactory::mapFailureToExitCode);
    }
  }

  List<String> resolveRequestedExtensions(
      List<String> extensionInputs,
      List<String> presetInputs,
      Set<String> knownExtensionIds,
      Map<String, List<String>> presetExtensionsByName) {
    List<ValidationError> errors = new ArrayList<>();
    LinkedHashSet<String> resolved = new LinkedHashSet<>();

    for (String presetInput : presetInputs) {
      String preset = ProjectRequestFactory.normalizePresetName(presetInput);
      if (preset.isBlank()) {
        continue;
      }
      if (PRESET_FAVORITES.equals(preset)) {
        resolved.addAll(
            ExtensionFavoritesStore.fileBacked(runtimeConfig.favoritesFile())
                .loadFavoriteExtensionIds());
        continue;
      }
      List<String> presetExtensions = presetExtensionsByName.get(preset);
      if (presetExtensions == null) {
        String allowedPresets = String.join(", ", presetExtensionsByName.keySet());
        if (!allowedPresets.isBlank()) {
          allowedPresets = allowedPresets + ", " + PRESET_FAVORITES;
        } else {
          allowedPresets = PRESET_FAVORITES;
        }
        errors.add(
            new ValidationError(
                "preset", "unknown preset '" + presetInput + "'. Allowed: " + allowedPresets));
        continue;
      }
      resolved.addAll(presetExtensions);
    }

    for (String extensionInput : extensionInputs) {
      if (extensionInput == null || extensionInput.isBlank()) {
        errors.add(new ValidationError("extension", "must not be blank"));
        continue;
      }
      resolved.add(extensionInput.trim());
    }

    for (String extensionId : resolved) {
      if (!knownExtensionIds.contains(extensionId)) {
        errors.add(new ValidationError("extension", "unknown extension id '" + extensionId + "'"));
      }
    }

    if (!errors.isEmpty()) {
      throw new ValidationException(errors);
    }
    return List.copyOf(resolved);
  }

  private static DiagnosticField df(String name, Object value) {
    return DiagnosticField.of(name, value);
  }

  private static EffectiveGenerateInputs loadEffectiveInputs(GenerateCommand command) {
    RequestOptions requestOptions = command.requestOptions;
    List<String> presetInputs = new ArrayList<>(command.presets);
    List<String> extensionInputs = new ArrayList<>(command.extensions);
    ForgeLock lock = null;

    if (command.recipeFile != null && !command.recipeFile.isBlank()) {
      Path recipePath = resolveRecipeReadPath(command.recipeFile);
      ForgeRecipe recipe = ForgeRecipeLockStore.loadRecipe(recipePath);
      requestOptions = recipe.toRequestOptions();
      presetInputs = new ArrayList<>(recipe.presets());
      presetInputs.addAll(command.presets);
      extensionInputs = new ArrayList<>(recipe.extensions());
      extensionInputs.addAll(command.extensions);
    }

    if (command.lockFile != null && !command.lockFile.isBlank()) {
      lock = ForgeRecipeLockStore.loadLock(Path.of(command.lockFile).toAbsolutePath().normalize());
    }

    if (command.refreshLock
        && (command.lockFile == null || command.lockFile.isBlank())
        && (command.writeLockFile == null || command.writeLockFile.isBlank())) {
      throw new IllegalArgumentException("--refresh-lock requires --lock or --write-lock");
    }

    return new EffectiveGenerateInputs(
        requestOptions,
        List.copyOf(presetInputs),
        List.copyOf(extensionInputs),
        lock,
        command.refreshLock,
        recipeWritePathOrNull(command.writeRecipeFile),
        normalizedPathOrNull(command.lockFile),
        normalizedPathOrNull(command.writeLockFile));
  }

  private static boolean requiresBuiltInPresets(List<String> presetInputs) {
    for (String presetInput : presetInputs) {
      String preset = ProjectRequestFactory.normalizePresetName(presetInput);
      if (!preset.isBlank() && !PRESET_FAVORITES.equals(preset)) {
        return true;
      }
    }
    return false;
  }

  private static void validateLockDrift(
      ForgeLock lock,
      boolean refreshLock,
      ProjectRequest request,
      List<String> presetInputs,
      List<String> extensionIds) {
    if (lock == null || refreshLock) {
      return;
    }

    List<ValidationError> errors = new ArrayList<>();
    if (!lock.platformStream().equals(request.platformStream())) {
      errors.add(
          new ValidationError(
              "lock",
              "platformStream drift: lock='"
                  + lock.platformStream()
                  + "', request='"
                  + request.platformStream()
                  + "'"));
    }
    if (!lock.buildTool().equals(request.buildTool())) {
      errors.add(
          new ValidationError(
              "lock",
              "buildTool drift: lock='"
                  + lock.buildTool()
                  + "', request='"
                  + request.buildTool()
                  + "'"));
    }
    if (!lock.javaVersion().equals(request.javaVersion())) {
      errors.add(
          new ValidationError(
              "lock",
              "javaVersion drift: lock='"
                  + lock.javaVersion()
                  + "', request='"
                  + request.javaVersion()
                  + "'"));
    }
    if (!lock.extensions().equals(extensionIds)) {
      errors.add(
          new ValidationError(
              "lock", "extensions drift: lock=" + lock.extensions() + ", request=" + extensionIds));
    }
    List<String> normalizedPresets =
        presetInputs.stream().map(ProjectRequestFactory::normalizePresetName).toList();
    if (!lock.presets().equals(normalizedPresets)) {
      errors.add(
          new ValidationError(
              "lock", "presets drift: lock=" + lock.presets() + ", request=" + normalizedPresets));
    }

    if (!errors.isEmpty()) {
      errors.add(
          new ValidationError(
              "lock", "rerun with --refresh-lock to accept and rewrite lock contents"));
      throw new ValidationException(errors);
    }
  }

  private static void writeRecipeAndLockIfRequested(
      EffectiveGenerateInputs inputs, ProjectRequest request, List<String> extensionIds) {
    List<String> normalizedPresets =
        inputs.presetInputs().stream().map(ProjectRequestFactory::normalizePresetName).toList();

    if (inputs.writeRecipeFile() != null) {
      ForgeRecipe recipe =
          ForgeRecipe.from(inputs.requestOptions(), normalizedPresets, inputs.extensionInputs());
      ForgeRecipeLockStore.writeRecipe(inputs.writeRecipeFile(), recipe);
    }

    Path lockPath = null;
    if (inputs.writeLockFile() != null) {
      lockPath = inputs.writeLockFile();
    } else if (inputs.refreshLock() && inputs.lockFile() != null) {
      lockPath = inputs.lockFile();
    }
    if (lockPath != null) {
      ForgeLock lock =
          ForgeLock.from(
              request.platformStream(),
              request.buildTool(),
              request.javaVersion(),
              normalizedPresets,
              extensionIds);
      ForgeRecipeLockStore.writeLock(lockPath, lock);
    }
  }

  private static Path normalizedPathOrNull(String pathValue) {
    if (pathValue == null || pathValue.isBlank()) {
      return null;
    }
    return Path.of(pathValue).toAbsolutePath().normalize();
  }

  private static Path resolveRecipeReadPath(String recipeReference) {
    Path requestedPath = Path.of(recipeReference);
    Path localPath = requestedPath.toAbsolutePath().normalize();
    if (requestedPath.isAbsolute() || Files.exists(localPath)) {
      return localPath;
    }
    return ForgeDataPaths.recipesRoot().resolve(requestedPath).toAbsolutePath().normalize();
  }

  private static Path recipeWritePathOrNull(String pathValue) {
    if (pathValue == null || pathValue.isBlank()) {
      return null;
    }
    Path requestedPath = Path.of(pathValue);
    if (requestedPath.isAbsolute() || requestedPath.getParent() != null) {
      return requestedPath.toAbsolutePath().normalize();
    }
    return ForgeDataPaths.recipesRoot().resolve(requestedPath).toAbsolutePath().normalize();
  }

  private record EffectiveGenerateInputs(
      RequestOptions requestOptions,
      List<String> presetInputs,
      List<String> extensionInputs,
      ForgeLock lock,
      boolean refreshLock,
      Path writeRecipeFile,
      Path lockFile,
      Path writeLockFile) {}
}
