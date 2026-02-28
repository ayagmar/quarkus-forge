package dev.ayagmar.quarkusforge;

import dev.ayagmar.quarkusforge.api.CatalogData;
import dev.ayagmar.quarkusforge.api.ErrorMessageMapper;
import dev.ayagmar.quarkusforge.api.ExtensionDto;
import dev.ayagmar.quarkusforge.api.GenerationRequest;
import dev.ayagmar.quarkusforge.api.ThrowableUnwrapper;
import dev.ayagmar.quarkusforge.diagnostics.DiagnosticField;
import dev.ayagmar.quarkusforge.diagnostics.DiagnosticLogger;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
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

    ProjectRequest request = operations.toProjectRequest(command.requestOptions);
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

    List<String> extensionIds;
    try {
      extensionIds =
          operations.resolveRequestedExtensions(
              command.extensions, command.presets, knownExtensionIds);
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
}
