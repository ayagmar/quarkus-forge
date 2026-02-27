package dev.ayagmar.quarkusforge.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class CatalogDataService {
  private final QuarkusApiClient apiClient;
  private final CatalogSnapshotCache snapshotCache;

  public CatalogDataService(QuarkusApiClient apiClient, CatalogSnapshotCache snapshotCache) {
    this.apiClient = Objects.requireNonNull(apiClient);
    this.snapshotCache = Objects.requireNonNull(snapshotCache);
  }

  public CompletableFuture<CatalogData> load() {
    CompletableFuture<List<ExtensionDto>> extensionsFuture = apiClient.fetchExtensions();
    CompletableFuture<MetadataSelection> metadataFuture = loadMetadataSelection();

    return extensionsFuture
        .thenCombine(metadataFuture, this::toLiveCatalogData)
        .exceptionallyCompose(this::fallbackToCache);
  }

  public CompletableFuture<CatalogData> loadForStartup() {
    Optional<CatalogSnapshotCache.CachedCatalogSnapshot> cachedSnapshot = snapshotCache.read();
    if (cachedSnapshot.isPresent()) {
      CatalogSnapshotCache.CachedCatalogSnapshot snapshot = cachedSnapshot.get();
      String detail =
          snapshot.stale()
              ? "Loaded stale extension catalog from cache at startup (Ctrl+R to refresh live data)"
              : "Loaded extension catalog from cache at startup (Ctrl+R to refresh live data)";
      return CompletableFuture.completedFuture(
          new CatalogData(
              snapshot.metadata(),
              snapshot.extensions(),
              CatalogSource.CACHE,
              snapshot.stale(),
              detail));
    }
    return load();
  }

  private CatalogData toLiveCatalogData(
      List<ExtensionDto> extensions, MetadataSelection metadataSelection) {
    if (extensions.isEmpty()) {
      throw new ApiContractException("Catalog load returned no extensions");
    }

    CatalogSnapshotCache.CacheWriteOutcome writeOutcome =
        snapshotCache.write(metadataSelection.metadata(), extensions);
    String detailMessage = metadataSelection.detailMessage();
    if (!writeOutcome.written()) {
      String cacheWriteDetail =
          writeOutcome.rejected()
              ? "Live catalog loaded; cache update skipped (%s)".formatted(writeOutcome.detail())
              : "Live catalog loaded; cache update failed (%s)".formatted(writeOutcome.detail());
      detailMessage =
          detailMessage.isBlank() ? cacheWriteDetail : detailMessage + " | " + cacheWriteDetail;
    }
    return new CatalogData(
        metadataSelection.metadata(), extensions, CatalogSource.LIVE, false, detailMessage);
  }

  private CompletableFuture<MetadataSelection> loadMetadataSelection() {
    return apiClient
        .fetchMetadata()
        .thenApply(metadata -> new MetadataSelection(metadata, ""))
        .exceptionally(this::fallbackMetadataSelection);
  }

  private MetadataSelection fallbackMetadataSelection(Throwable throwable) {
    Throwable cause = unwrapCompletionCause(throwable);
    try {
      MetadataDto snapshotMetadata = MetadataSnapshotLoader.loadDefault();
      return new MetadataSelection(
          snapshotMetadata,
          "Live metadata unavailable (%s); using bundled metadata snapshot"
              .formatted(userFriendlyError(cause)));
    } catch (RuntimeException snapshotFailure) {
      throw new CompletionException(
          new ApiClientException(
              "Live metadata unavailable (%s) and bundled metadata snapshot failed"
                  .formatted(userFriendlyError(cause)),
              snapshotFailure));
    }
  }

  private CompletableFuture<CatalogData> fallbackToCache(Throwable throwable) {
    Throwable cause = unwrapCompletionCause(throwable);
    Optional<CatalogSnapshotCache.CachedCatalogSnapshot> cachedSnapshot = snapshotCache.read();
    if (cachedSnapshot.isPresent()) {
      CatalogSnapshotCache.CachedCatalogSnapshot snapshot = cachedSnapshot.get();
      String detail =
          "Live catalog unavailable (%s); using %scached snapshot"
              .formatted(userFriendlyError(cause), snapshot.stale() ? "stale " : "");
      return CompletableFuture.completedFuture(
          new CatalogData(
              snapshot.metadata(),
              snapshot.extensions(),
              CatalogSource.CACHE,
              snapshot.stale(),
              detail));
    }

    return CompletableFuture.failedFuture(
        new ApiClientException(
            "Live catalog unavailable and no valid cache snapshot found", cause));
  }

  private static Throwable unwrapCompletionCause(Throwable throwable) {
    if (throwable instanceof CompletionException completionException
        && completionException.getCause() != null) {
      return completionException.getCause();
    }
    return throwable;
  }

  private static String userFriendlyError(Throwable throwable) {
    if (throwable == null) {
      return "unknown error";
    }
    if (throwable.getMessage() != null && !throwable.getMessage().isBlank()) {
      return throwable.getMessage();
    }
    return throwable.getClass().getSimpleName();
  }

  private record MetadataSelection(MetadataDto metadata, String detailMessage) {
    private MetadataSelection {
      metadata = Objects.requireNonNull(metadata);
      detailMessage = detailMessage == null ? "" : detailMessage.strip();
    }
  }
}
