package dev.ayagmar.quarkusforge;

import dev.ayagmar.quarkusforge.api.CatalogData;
import dev.ayagmar.quarkusforge.api.CatalogDataService;
import dev.ayagmar.quarkusforge.api.CatalogSnapshotCache;
import dev.ayagmar.quarkusforge.api.GenerationRequest;
import dev.ayagmar.quarkusforge.api.QuarkusApiClient;
import dev.ayagmar.quarkusforge.archive.OverwritePolicy;
import dev.ayagmar.quarkusforge.archive.ProjectArchiveService;
import dev.ayagmar.quarkusforge.archive.SafeZipExtractor;
import dev.ayagmar.quarkusforge.runtime.RuntimeConfig;
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
 * Manages catalog loading, preset loading, and project generation for headless mode. Shares a
 * single {@link QuarkusApiClient} across all calls within one generation session. Must be closed
 * after use to release transport resources.
 */
class HeadlessCatalogClient implements HeadlessCatalogOperations {
  private final RuntimeConfig runtimeConfig;
  private final QuarkusApiClient apiClient;

  HeadlessCatalogClient(RuntimeConfig runtimeConfig) {
    this.runtimeConfig = Objects.requireNonNull(runtimeConfig);
    this.apiClient = new QuarkusApiClient(runtimeConfig.apiBaseUri());
  }

  @Override
  public void close() {
    apiClient.close();
  }

  public CatalogData loadCatalogData(Duration timeout)
      throws ExecutionException, InterruptedException, TimeoutException {
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

  public Map<String, List<String>> loadBuiltInPresets(String platformStream, Duration timeout)
      throws ExecutionException, InterruptedException, TimeoutException {
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

  public CompletableFuture<Path> startGeneration(
      GenerationRequest generationRequest, Path outputPath, Consumer<String> progressLineConsumer) {
    ProjectArchiveService archiveService =
        new ProjectArchiveService(apiClient, new SafeZipExtractor());
    return archiveService.downloadAndExtract(
        generationRequest,
        outputPath,
        OverwritePolicy.FAIL_IF_EXISTS,
        () -> Thread.currentThread().isInterrupted(),
        progress ->
            progressLineConsumer.accept(
                switch (progress) {
                  case REQUESTING_ARCHIVE -> "requesting project archive from Quarkus API...";
                  case EXTRACTING_ARCHIVE -> "extracting project archive...";
                }));
  }
}
