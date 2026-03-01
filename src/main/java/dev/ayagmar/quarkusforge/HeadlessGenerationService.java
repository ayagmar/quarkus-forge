package dev.ayagmar.quarkusforge;

import dev.ayagmar.quarkusforge.api.CatalogData;
import dev.ayagmar.quarkusforge.api.ErrorMessageMapper;
import dev.ayagmar.quarkusforge.api.ExtensionDto;
import dev.ayagmar.quarkusforge.api.ForgeDataPaths;
import dev.ayagmar.quarkusforge.api.GenerationRequest;
import dev.ayagmar.quarkusforge.api.ThrowableUnwrapper;
import dev.ayagmar.quarkusforge.diagnostics.DiagnosticField;
import dev.ayagmar.quarkusforge.diagnostics.DiagnosticLogger;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ValidationError;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import picocli.CommandLine;

final class HeadlessGenerationService {
  int run(
      GenerateCommand command,
      boolean globalDryRun,
      boolean verbose,
      HeadlessGenerationOperations operations) {
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

    CatalogData catalogData;
    try {
      catalogData = operations.loadCatalogData();
      diagnostics.info(
          "catalog.load.success",
          df("source", catalogData.source().label()),
          df("stale", catalogData.stale()),
          df("detail", catalogData.detailMessage()));
    } catch (CancellationException cancellationException) {
      diagnostics.error("catalog.load.cancelled", df("phase", "before-start"));
      System.err.println("Generation cancelled before start.");
      return QuarkusForgeCli.EXIT_CODE_CANCELLED;
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      diagnostics.error("catalog.load.cancelled", df("phase", "interrupted"));
      System.err.println("Generation cancelled before start.");
      return QuarkusForgeCli.EXIT_CODE_CANCELLED;
    } catch (TimeoutException timeoutException) {
      Duration timeout = operations.headlessCatalogTimeout();
      diagnostics.error("catalog.load.timeout", df("timeoutMs", timeout.toMillis()));
      System.err.println(
          "Failed to load extension catalog: request timed out after " + timeout.toMillis() + "ms");
      return QuarkusForgeCli.EXIT_CODE_NETWORK;
    } catch (ExecutionException executionException) {
      Throwable cause = ThrowableUnwrapper.unwrapAsyncFailure(executionException);
      diagnostics.error(
          "catalog.load.failure",
          df("causeType", cause.getClass().getSimpleName()),
          df("message", ErrorMessageMapper.userFriendlyError(cause)));
      System.err.println(
          "Failed to load extension catalog: " + ErrorMessageMapper.userFriendlyError(cause));
      return operations.mapHeadlessFailureToExitCode(cause);
    }

    ProjectRequest request = operations.toProjectRequest(effectiveInputs.requestOptions());
    MetadataCompatibilityContext metadataCompatibility =
        MetadataCompatibilityContext.success(catalogData.metadata());
    ProjectRequest requestWithResolvedStream =
        operations.applyRecommendedPlatformStream(request, metadataCompatibility);
    ForgeUiState validatedState =
        operations.buildInitialState(requestWithResolvedStream, metadataCompatibility);
    if (!validatedState.canSubmit()) {
      diagnostics.error(
          "generate.validation.failed",
          df("errorCount", validatedState.validation().errors().size()),
          df("catalogSource", catalogData.source().label()));
      operations.printValidationErrors(
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
            operations.loadBuiltInPresets(validatedState.request().platformStream());
      } catch (CancellationException cancellationException) {
        diagnostics.error("preset.load.cancelled", df("phase", "before-resolution"));
        System.err.println("Failed to load presets: request cancelled.");
        return QuarkusForgeCli.EXIT_CODE_CANCELLED;
      } catch (InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
        diagnostics.error("preset.load.cancelled", df("phase", "interrupted"));
        System.err.println("Failed to load presets: request cancelled.");
        return QuarkusForgeCli.EXIT_CODE_CANCELLED;
      } catch (TimeoutException timeoutException) {
        Duration timeout = operations.headlessCatalogTimeout();
        diagnostics.error("preset.load.timeout", df("timeoutMs", timeout.toMillis()));
        System.err.println(
            "Failed to load presets: request timed out after " + timeout.toMillis() + "ms");
        return QuarkusForgeCli.EXIT_CODE_NETWORK;
      } catch (ExecutionException executionException) {
        Throwable cause = ThrowableUnwrapper.unwrapAsyncFailure(executionException);
        diagnostics.error(
            "preset.load.failure",
            df("causeType", cause.getClass().getSimpleName()),
            df("message", ErrorMessageMapper.userFriendlyError(cause)));
        System.err.println(
            "Failed to load presets: " + ErrorMessageMapper.userFriendlyError(cause));
        return operations.mapHeadlessFailureToExitCode(cause);
      }
    }

    List<String> extensionIds;
    try {
      extensionIds =
          operations.resolveRequestedExtensions(
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
      operations.printValidationErrors(
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
      operations.printDryRunSummary(
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
        operations.startGeneration(generationRequest, outputPath, System.out::println);
    try {
      Duration generationTimeout = operations.headlessGenerationTimeout();
      diagnostics.info(
          "generate.execute.start",
          df("outputPath", outputPath.toString()),
          df("extensionCount", extensionIds.size()));
      Path generatedProjectRoot =
          generationFuture.get(generationTimeout.toMillis(), TimeUnit.MILLISECONDS);
      diagnostics.info(
          "generate.execute.success", df("projectRoot", generatedProjectRoot.toString()));
      writeRecipeAndLockIfRequested(effectiveInputs, validatedState.request(), extensionIds);
      System.out.println(
          "Generation succeeded: " + generatedProjectRoot.toAbsolutePath().normalize());
      return CommandLine.ExitCode.OK;
    } catch (CancellationException cancellationException) {
      diagnostics.error("generate.execute.cancelled", df("phase", "execution"));
      System.err.println("Generation cancelled.");
      return QuarkusForgeCli.EXIT_CODE_CANCELLED;
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      diagnostics.error("generate.execute.cancelled", df("phase", "interrupted"));
      System.err.println("Generation cancelled.");
      return QuarkusForgeCli.EXIT_CODE_CANCELLED;
    } catch (TimeoutException timeoutException) {
      generationFuture.cancel(true);
      Duration timeout = operations.headlessGenerationTimeout();
      diagnostics.error("generate.execute.timeout", df("timeoutMs", timeout.toMillis()));
      System.err.println("Generation failed: request timed out after " + timeout.toMillis() + "ms");
      return QuarkusForgeCli.EXIT_CODE_NETWORK;
    } catch (ExecutionException executionException) {
      Throwable cause = ThrowableUnwrapper.unwrapAsyncFailure(executionException);
      int exitCode = operations.mapHeadlessFailureToExitCode(cause);
      diagnostics.error(
          "generate.execute.failure",
          df("causeType", cause.getClass().getSimpleName()),
          df("message", ErrorMessageMapper.userFriendlyError(cause)),
          df("exitCode", exitCode));
      System.err.println("Generation failed: " + ErrorMessageMapper.userFriendlyError(cause));
      return exitCode;
    }
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
      String preset = QuarkusForgeCli.normalizePresetName(presetInput);
      if (!preset.isBlank() && !"favorites".equals(preset)) {
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
        presetInputs.stream().map(QuarkusForgeCli::normalizePresetName).toList();
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
        inputs.presetInputs().stream().map(QuarkusForgeCli::normalizePresetName).toList();

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
