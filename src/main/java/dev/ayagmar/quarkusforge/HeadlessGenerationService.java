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
import dev.ayagmar.quarkusforge.api.ExtensionFavoritesStore;
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

    EffectiveInputs inputs;
    try {
      inputs = loadEffectiveInputs(command);
    } catch (IllegalArgumentException illegalArgumentException) {
      diagnostics.error(
          "generate.inputs.failed", df("message", illegalArgumentException.getMessage()));
      System.err.println(illegalArgumentException.getMessage());
      return ExitCodes.VALIDATION;
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

    ProjectRequest request = ProjectRequestFactory.fromOptions(inputs.requestOptions());
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
      return ExitCodes.VALIDATION;
    }

    Set<String> knownExtensionIds = new LinkedHashSet<>();
    for (ExtensionDto extension : catalogData.extensions()) {
      knownExtensionIds.add(extension.id());
    }

    Map<String, List<String>> presetExtensionsByName = Map.of();
    if (requiresBuiltInPresets(inputs.presetInputs())) {
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
              inputs.extensionInputs(),
              inputs.presetInputs(),
              knownExtensionIds,
              presetExtensionsByName);

      if (inputs.lockCheck()) {
        validateLockDrift(inputs.forgefile(), validatedState.request(), inputs, extensionIds);
      }
    } catch (ValidationException validationException) {
      diagnostics.error(
          "generate.extension-validation.failed",
          df("errorCount", validationException.errors().size()));
      HeadlessOutputPrinter.printValidationErrors(
          new ValidationReport(validationException.errors()),
          catalogData.source().label() + (catalogData.stale() ? " [stale]" : ""),
          catalogData.detailMessage());
      return ExitCodes.VALIDATION;
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
      saveForgefileIfRequested(inputs, validatedState.request(), extensionIds);
      return ExitCodes.OK;
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
      saveForgefileIfRequested(inputs, validatedState.request(), extensionIds);
      System.out.println(
          "Generation succeeded: " + generatedProjectRoot.toAbsolutePath().normalize());
      return ExitCodes.OK;
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

  // --- Private helpers ---

  private static DiagnosticField df(String name, Object value) {
    return DiagnosticField.of(name, value);
  }

  private static EffectiveInputs loadEffectiveInputs(GenerateCommand command) {
    RequestOptions requestOptions = command.requestOptions;
    List<String> presetInputs = new ArrayList<>(command.presets);
    List<String> extensionInputs = new ArrayList<>(command.extensions);
    Forgefile forgefile = null;
    Path forgefilePath = null;

    if (command.fromFile != null && !command.fromFile.isBlank()) {
      forgefilePath = resolveForgefileReadPath(command.fromFile);
      forgefile = ForgefileStore.load(forgefilePath);
      requestOptions = forgefile.toRequestOptions();
      presetInputs = new ArrayList<>(new LinkedHashSet<>(forgefile.presets()));
      presetInputs.addAll(command.presets);
      extensionInputs = new ArrayList<>(new LinkedHashSet<>(forgefile.extensions()));
      extensionInputs.addAll(command.extensions);
    }

    if (command.lock && forgefile == null && command.saveAsFile == null) {
      throw new IllegalArgumentException("--lock requires --from or --save-as");
    }
    if (command.lockCheck && (forgefile == null || forgefile.locked() == null)) {
      throw new IllegalArgumentException(
          "--lock-check requires --from pointing to a Forgefile with a locked section");
    }

    return new EffectiveInputs(
        requestOptions,
        List.copyOf(presetInputs),
        List.copyOf(extensionInputs),
        forgefile,
        forgefilePath,
        command.lock,
        command.lockCheck,
        saveAsPathOrNull(command.saveAsFile));
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
      Forgefile forgefile,
      ProjectRequest request,
      EffectiveInputs inputs,
      List<String> extensionIds) {
    if (forgefile == null || forgefile.locked() == null) {
      return;
    }
    ForgefileLock lock = forgefile.locked();

    List<ValidationError> errors = new ArrayList<>();
    if (!lock.platformStream().equals(request.platformStream())) {
      errors.add(
          new ValidationError(
              "locked",
              "platformStream drift: locked='"
                  + lock.platformStream()
                  + "', request='"
                  + request.platformStream()
                  + "'"));
    }
    if (!lock.buildTool().equals(request.buildTool())) {
      errors.add(
          new ValidationError(
              "locked",
              "buildTool drift: locked='"
                  + lock.buildTool()
                  + "', request='"
                  + request.buildTool()
                  + "'"));
    }
    if (!lock.javaVersion().equals(request.javaVersion())) {
      errors.add(
          new ValidationError(
              "locked",
              "javaVersion drift: locked='"
                  + lock.javaVersion()
                  + "', request='"
                  + request.javaVersion()
                  + "'"));
    }
    if (!lock.extensions().equals(extensionIds)) {
      errors.add(
          new ValidationError(
              "locked",
              "extensions drift: locked=" + lock.extensions() + ", request=" + extensionIds));
    }
    List<String> normalizedPresets =
        inputs.presetInputs().stream()
            .map(ProjectRequestFactory::normalizePresetName)
            .distinct()
            .toList();
    if (!lock.presets().equals(normalizedPresets)) {
      errors.add(
          new ValidationError(
              "locked",
              "presets drift: locked=" + lock.presets() + ", request=" + normalizedPresets));
    }

    if (!errors.isEmpty()) {
      errors.add(
          new ValidationError(
              "locked", "rerun with --lock to accept and update the locked section"));
      throw new ValidationException(errors);
    }
  }

  private static void saveForgefileIfRequested(
      EffectiveInputs inputs, ProjectRequest request, List<String> extensionIds) {
    List<String> normalizedPresets =
        inputs.presetInputs().stream()
            .map(ProjectRequestFactory::normalizePresetName)
            .distinct()
            .toList();

    // Always rebuild from effective inputs so CLI additions are persisted
    Forgefile forgefile =
        Forgefile.from(inputs.requestOptions(), normalizedPresets, inputs.extensionInputs());

    // Add locked section if --lock was requested
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

    // Write to --save-as path, or update --from path in-place
    Path writePath = inputs.saveAsFile();
    if (writePath == null && inputs.writeLock() && inputs.forgefilePath() != null) {
      writePath = inputs.forgefilePath();
    }
    if (writePath == null) {
      return;
    }
    ForgefileStore.save(writePath, forgefile);
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
      RequestOptions requestOptions,
      List<String> presetInputs,
      List<String> extensionInputs,
      Forgefile forgefile,
      Path forgefilePath,
      boolean writeLock,
      boolean lockCheck,
      Path saveAsFile) {}
}
