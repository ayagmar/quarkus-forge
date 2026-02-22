package dev.ayagmar.quarkusforge.archive;

import dev.ayagmar.quarkusforge.api.GenerationRequest;
import dev.ayagmar.quarkusforge.api.QuarkusApiClient;
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
        () -> Files.createTempFile("quarkus-forge-", ".zip"),
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

    final Path tempZip;
    try {
      tempZip = tempFileProvider.create();
    } catch (IOException ioException) {
      return CompletableFuture.failedFuture(
          new ArchiveException("Failed to allocate temporary archive file", ioException));
    }

    progressListener.accept(ProgressStep.DOWNLOADING_ARCHIVE);
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
                    SafeZipExtractor.ExtractionResult result =
                        zipExtractor.extract(archivePath, outputDirectory, overwritePolicy);
                    return result.extractedRoot();
                  },
                  extractionExecutor);
            })
        .whenComplete((ignored, throwable) -> SafeZipExtractor.deleteRecursivelyQuietly(tempZip));
  }

  public enum ProgressStep {
    DOWNLOADING_ARCHIVE,
    EXTRACTING_ARCHIVE
  }

  @FunctionalInterface
  interface TempFileProvider {
    Path create() throws IOException;
  }
}
