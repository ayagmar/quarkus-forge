package dev.ayagmar.quarkusforge;

import dev.ayagmar.quarkusforge.api.CatalogData;
import dev.ayagmar.quarkusforge.api.CatalogDataService;
import dev.ayagmar.quarkusforge.api.CatalogSnapshotCache;
import dev.ayagmar.quarkusforge.api.GenerationRequest;
import dev.ayagmar.quarkusforge.api.QuarkusApiClient;
import dev.ayagmar.quarkusforge.archive.OverwritePolicy;
import dev.ayagmar.quarkusforge.archive.ProjectArchiveService;
import dev.ayagmar.quarkusforge.archive.SafeZipExtractor;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Manages catalog loading, preset loading, and project generation for headless mode. Each call
 * opens a short-lived {@link QuarkusApiClient} so resources are cleaned up immediately.
 */
class HeadlessCatalogClient {
  private final RuntimeConfig runtimeConfig;

  HeadlessCatalogClient(RuntimeConfig runtimeConfig) {
    this.runtimeConfig = Objects.requireNonNull(runtimeConfig);
  }

  CatalogData loadCatalogData(Duration timeout)
      throws ExecutionException, InterruptedException, TimeoutException {
    try (QuarkusApiClient apiClient = new QuarkusApiClient(runtimeConfig.apiBaseUri())) {
      CatalogDataService catalogDataService =
          new CatalogDataService(
              apiClient, new CatalogSnapshotCache(runtimeConfig.catalogCacheFile()));
      CompletableFuture<CatalogData> loadFuture = catalogDataService.load();
      try {
        return loadFuture.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      } catch (TimeoutException timeoutException) {
        loadFuture.cancel(true);
        TimeoutException wrapped =
            new TimeoutException("catalog load timed out after " + timeout.toMillis() + "ms");
        wrapped.initCause(timeoutException);
        throw wrapped;
      }
    }
  }

  Map<String, List<String>> loadBuiltInPresets(String platformStream, Duration timeout)
      throws ExecutionException, InterruptedException, TimeoutException {
    try (QuarkusApiClient apiClient = new QuarkusApiClient(runtimeConfig.apiBaseUri())) {
      CompletableFuture<Map<String, List<String>>> presetsFuture =
          apiClient.fetchPresets(platformStream);
      try {
        return presetsFuture.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      } catch (TimeoutException timeoutException) {
        presetsFuture.cancel(true);
        TimeoutException wrapped =
            new TimeoutException("preset load timed out after " + timeout.toMillis() + "ms");
        wrapped.initCause(timeoutException);
        throw wrapped;
      }
    }
  }

  CompletableFuture<Path> startGeneration(
      GenerationRequest generationRequest, Path outputPath, Consumer<String> progressLineConsumer) {
    QuarkusApiClient apiClient = new QuarkusApiClient(runtimeConfig.apiBaseUri());
    ProjectArchiveService archiveService =
        new ProjectArchiveService(apiClient, new SafeZipExtractor());
    try {
      return archiveService
          .downloadAndExtract(
              generationRequest,
              outputPath,
              OverwritePolicy.FAIL_IF_EXISTS,
              () -> Thread.currentThread().isInterrupted(),
              progress ->
                  progressLineConsumer.accept(
                      switch (progress) {
                        case REQUESTING_ARCHIVE -> "requesting project archive from Quarkus API...";
                        case EXTRACTING_ARCHIVE -> "extracting project archive...";
                      }))
          .whenComplete((ignored, throwable) -> apiClient.close());
    } catch (RuntimeException e) {
      apiClient.close();
      throw e;
    }
  }
}
