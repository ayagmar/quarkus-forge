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
    return apiClient
        .fetchMetadata()
        .thenCombine(apiClient.fetchExtensions(), this::toLiveCatalogData)
        .exceptionallyCompose(this::fallbackToCache);
  }

  private CatalogData toLiveCatalogData(MetadataDto metadata, List<ExtensionDto> extensions) {
    if (extensions.isEmpty()) {
      throw new ApiContractException("Catalog load returned no extensions");
    }

    CatalogSnapshotCache.CacheWriteOutcome writeOutcome = snapshotCache.write(metadata, extensions);
    String detailMessage = "";
    if (!writeOutcome.written()) {
      detailMessage =
          writeOutcome.rejected()
              ? "Live catalog loaded; cache update skipped (%s)".formatted(writeOutcome.detail())
              : "Live catalog loaded; cache update failed (%s)".formatted(writeOutcome.detail());
    }
    return new CatalogData(metadata, extensions, CatalogSource.LIVE, false, detailMessage);
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
            "Live catalog unavailable and no valid cache snapshot found: %s"
                .formatted(userFriendlyError(cause)),
            cause));
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
}
