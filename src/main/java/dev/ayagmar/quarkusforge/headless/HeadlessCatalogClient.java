package dev.ayagmar.quarkusforge.headless;

import dev.ayagmar.quarkusforge.api.CatalogData;
import dev.ayagmar.quarkusforge.api.CatalogDataService;
import dev.ayagmar.quarkusforge.api.GenerationRequest;
import dev.ayagmar.quarkusforge.api.QuarkusApiClient;
import dev.ayagmar.quarkusforge.archive.OverwritePolicy;
import dev.ayagmar.quarkusforge.archive.ProjectArchiveService;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Manages catalog loading, preset loading, and project generation for headless mode. Shares a
 * single {@link QuarkusApiClient} across all calls within one generation session. Must be closed
 * after use to release transport resources.
 */
final class HeadlessCatalogClient implements HeadlessCatalogOperations {
  private final QuarkusApiClient apiClient;
  private final CatalogDataService catalogDataService;
  private final ProjectArchiveService projectArchiveService;

  HeadlessCatalogClient(
      QuarkusApiClient apiClient,
      CatalogDataService catalogDataService,
      ProjectArchiveService projectArchiveService) {
    this.apiClient = Objects.requireNonNull(apiClient);
    this.catalogDataService = Objects.requireNonNull(catalogDataService);
    this.projectArchiveService = Objects.requireNonNull(projectArchiveService);
  }

  @Override
  public void close() {
    apiClient.close();
  }

  @Override
  public CatalogData loadCatalogData(Duration timeout)
      throws ExecutionException, InterruptedException, TimeoutException {
    CompletableFuture<CatalogData> loadFuture = catalogDataService.load();
    try {
      return loadFuture.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException timeoutException) {
      loadFuture.cancel(true);
      CatalogData cachedCatalog =
          catalogDataService
              .fallbackToCachedSnapshot(
                  "Live catalog unavailable (timed out after " + timeout.toMillis() + "ms)")
              .orElse(null);
      if (cachedCatalog != null) {
        return cachedCatalog;
      }
      TimeoutException wrapped =
          new TimeoutException("catalog load timed out after " + timeout.toMillis() + "ms");
      wrapped.initCause(timeoutException);
      throw wrapped;
    }
  }

  @Override
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

  @Override
  public CompletableFuture<Path> startGeneration(
      GenerationRequest generationRequest,
      Path outputPath,
      BooleanSupplier cancelled,
      Consumer<String> progressLineConsumer) {
    return projectArchiveService.downloadAndExtract(
        generationRequest,
        outputPath,
        OverwritePolicy.FAIL_IF_EXISTS,
        cancelled,
        progress ->
            progressLineConsumer.accept(
                switch (progress) {
                  case REQUESTING_ARCHIVE -> "requesting project archive from Quarkus API...";
                  case EXTRACTING_ARCHIVE -> "extracting project archive...";
                }));
  }
}
