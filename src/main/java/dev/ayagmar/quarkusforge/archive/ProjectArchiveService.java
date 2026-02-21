package dev.ayagmar.quarkusforge.archive;

import dev.ayagmar.quarkusforge.api.GenerationRequest;
import dev.ayagmar.quarkusforge.api.QuarkusApiClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

public final class ProjectArchiveService {
  private final QuarkusApiClient apiClient;
  private final SafeZipExtractor zipExtractor;
  private final TempFileProvider tempFileProvider;

  public ProjectArchiveService(QuarkusApiClient apiClient, SafeZipExtractor zipExtractor) {
    this(apiClient, zipExtractor, () -> Files.createTempFile("quarkus-forge-", ".zip"));
  }

  ProjectArchiveService(
      QuarkusApiClient apiClient,
      SafeZipExtractor zipExtractor,
      TempFileProvider tempFileProvider) {
    this.apiClient = Objects.requireNonNull(apiClient);
    this.zipExtractor = Objects.requireNonNull(zipExtractor);
    this.tempFileProvider = Objects.requireNonNull(tempFileProvider);
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
    Objects.requireNonNull(request);
    Objects.requireNonNull(outputDirectory);
    Objects.requireNonNull(overwritePolicy);
    Objects.requireNonNull(cancelled);

    final Path tempZip;
    try {
      tempZip = tempFileProvider.create();
    } catch (IOException ioException) {
      return CompletableFuture.failedFuture(
          new ArchiveException("Failed to allocate temporary archive file", ioException));
    }

    return apiClient
        .downloadProjectZipToFile(request, tempZip)
        .thenApply(
            archivePath -> {
              if (cancelled.getAsBoolean()) {
                throw new CancellationException("Generation cancelled before extraction");
              }
              SafeZipExtractor.ExtractionResult result =
                  zipExtractor.extract(archivePath, outputDirectory, overwritePolicy);
              return result.extractedRoot();
            })
        .whenComplete((ignored, throwable) -> SafeZipExtractor.deleteRecursivelyQuietly(tempZip));
  }

  @FunctionalInterface
  interface TempFileProvider {
    Path create() throws IOException;
  }
}
