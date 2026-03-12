package dev.ayagmar.quarkusforge.archive;

import dev.ayagmar.quarkusforge.api.GenerationRequest;
import dev.ayagmar.quarkusforge.api.QuarkusApiClient;
import dev.ayagmar.quarkusforge.util.FilePermissionSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class ProjectArchiveService {
  private final QuarkusApiClient apiClient;
  private final SafeZipExtractor zipExtractor;
  private final TempFileProvider tempFileProvider;
  private final Executor extractionExecutor;

  public ProjectArchiveService(QuarkusApiClient apiClient, SafeZipExtractor zipExtractor) {
    this(
        apiClient,
        zipExtractor,
        () -> FilePermissionSupport.createOwnerOnlyTempFile("quarkus-forge-", ".zip"),
        ForkJoinPool.commonPool());
  }

  ProjectArchiveService(
      QuarkusApiClient apiClient,
      SafeZipExtractor zipExtractor,
      TempFileProvider tempFileProvider) {
    this(apiClient, zipExtractor, tempFileProvider, ForkJoinPool.commonPool());
  }

  ProjectArchiveService(
      QuarkusApiClient apiClient,
      SafeZipExtractor zipExtractor,
      TempFileProvider tempFileProvider,
      Executor extractionExecutor) {
    this.apiClient = Objects.requireNonNull(apiClient);
    this.zipExtractor = Objects.requireNonNull(zipExtractor);
    this.tempFileProvider = Objects.requireNonNull(tempFileProvider);
    this.extractionExecutor = Objects.requireNonNull(extractionExecutor);
  }

  public CompletableFuture<Path> downloadAndExtract(
      GenerationRequest request, Path outputDirectory, OverwritePolicy overwritePolicy) {
    return downloadAndExtract(request, outputDirectory, overwritePolicy, () -> false);
  }

  public CompletableFuture<Path> downloadAndExtract(
      GenerationRequest request,
      Path outputDirectory,
      OverwritePolicy overwritePolicy,
      BooleanSupplier cancelled) {
    return downloadAndExtract(request, outputDirectory, overwritePolicy, cancelled, progress -> {});
  }

  public CompletableFuture<Path> downloadAndExtract(
      GenerationRequest request,
      Path outputDirectory,
      OverwritePolicy overwritePolicy,
      BooleanSupplier cancelled,
      Consumer<ProgressStep> progressListener) {
    Objects.requireNonNull(request);
    Objects.requireNonNull(outputDirectory);
    Objects.requireNonNull(overwritePolicy);
    Objects.requireNonNull(cancelled);
    Objects.requireNonNull(progressListener);

    if (cancelled.getAsBoolean()) {
      return CompletableFuture.failedFuture(
          new CancellationException("Generation cancelled before download"));
    }

    if (overwritePolicy == OverwritePolicy.FAIL_IF_EXISTS && Files.exists(outputDirectory)) {
      Path normalized = outputDirectory.toAbsolutePath().normalize();
      return CompletableFuture.failedFuture(
          new ArchiveException(
              "Output directory already exists: "
                  + normalized
                  + ". Delete it or change Output/Artifact and retry."));
    }

    final Path tempZip;
    try {
      tempZip = tempFileProvider.create();
    } catch (IOException ioException) {
      return CompletableFuture.failedFuture(
          new ArchiveException("Failed to allocate temporary archive file", ioException));
    }

    try {
      progressListener.accept(ProgressStep.REQUESTING_ARCHIVE);
      return apiClient
          .downloadProjectZipToFile(request, tempZip)
          .thenCompose(
              archivePath -> {
                if (cancelled.getAsBoolean()) {
                  return CompletableFuture.failedFuture(
                      new CancellationException("Generation cancelled before extraction"));
                }
                return CompletableFuture.supplyAsync(
                    () -> {
                      if (cancelled.getAsBoolean()) {
                        throw new CancellationException("Generation cancelled before extraction");
                      }
                      progressListener.accept(ProgressStep.EXTRACTING_ARCHIVE);
                      ExtractionResult result =
                          zipExtractor.extract(archivePath, outputDirectory, overwritePolicy);
                      if (cancelled.getAsBoolean()) {
                        throw new CancellationException("Generation cancelled during extraction");
                      }
                      return result.extractedRoot();
                    },
                    extractionExecutor);
              })
          .whenComplete((ignored, throwable) -> SafeZipExtractor.deleteRecursivelyQuietly(tempZip));
    } catch (RuntimeException runtimeException) {
      SafeZipExtractor.deleteRecursivelyQuietly(tempZip);
      throw runtimeException;
    }
  }

  public enum ProgressStep {
    REQUESTING_ARCHIVE,
    EXTRACTING_ARCHIVE
  }
}
