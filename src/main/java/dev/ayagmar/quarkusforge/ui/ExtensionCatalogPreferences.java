package dev.ayagmar.quarkusforge.ui;

import dev.ayagmar.quarkusforge.persistence.ExtensionFavoritesStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

final class ExtensionCatalogPreferences {
  private static final System.Logger LOGGER =
      System.getLogger(ExtensionCatalogPreferences.class.getName());

  private final Set<String> favoriteExtensionIds;
  private final List<String> recentExtensionIds;
  private final ExtensionFavoritesStore favoritesStore;
  private final Executor favoritesPersistenceExecutor;
  private final AtomicReference<CompletableFuture<Void>> preferencePersistenceChain;

  ExtensionCatalogPreferences(
      ExtensionFavoritesStore favoritesStore, Executor favoritesPersistenceExecutor) {
    this.favoritesStore = Objects.requireNonNull(favoritesStore);
    this.favoritesPersistenceExecutor = Objects.requireNonNull(favoritesPersistenceExecutor);
    favoriteExtensionIds = new LinkedHashSet<>(favoritesStore.loadFavoriteExtensionIds());
    recentExtensionIds =
        new ArrayList<>(normalizeRecentIds(favoritesStore.loadRecentExtensionIds()));
    preferencePersistenceChain = new AtomicReference<>(CompletableFuture.completedFuture(null));
  }

  void retainAvailable(Set<String> availableExtensionIds) {
    Objects.requireNonNull(availableExtensionIds);
    boolean favoritesChanged =
        favoriteExtensionIds.removeIf(favoriteId -> !availableExtensionIds.contains(favoriteId));
    boolean recentsChanged =
        recentExtensionIds.removeIf(recentId -> !availableExtensionIds.contains(recentId));
    if (favoritesChanged || recentsChanged) {
      persistUserStateAsync();
    }
  }

  Set<String> favoriteIdsView() {
    return Collections.unmodifiableSet(favoriteExtensionIds);
  }

  List<String> recentIdsView() {
    return Collections.unmodifiableList(recentExtensionIds);
  }

  int favoriteExtensionCount() {
    return favoriteExtensionIds.size();
  }

  boolean isFavorite(String extensionId) {
    return favoriteExtensionIds.contains(extensionId);
  }

  boolean toggleFavorite(String extensionId) {
    Objects.requireNonNull(extensionId);
    boolean isNowFavorite = favoriteExtensionIds.add(extensionId);
    if (!isNowFavorite) {
      favoriteExtensionIds.remove(extensionId);
    }
    persistUserStateAsync();
    return isNowFavorite;
  }

  void recordRecentSelection(String extensionId) {
    Objects.requireNonNull(extensionId);
    recentExtensionIds.removeIf(extensionId::equals);
    recentExtensionIds.add(0, extensionId);
    trimRecentIds();
    persistUserStateAsync();
  }

  private void persistUserStateAsync() {
    Set<String> favoriteSnapshot = Set.copyOf(favoriteExtensionIds);
    List<String> recentSnapshot = List.copyOf(recentExtensionIds);
    preferencePersistenceChain.updateAndGet(
        previousChain ->
            previousChain
                .exceptionally(
                    exception -> {
                      logPersistenceFailure(exception);
                      return null;
                    })
                .thenRunAsync(
                    () -> favoritesStore.saveAll(favoriteSnapshot, recentSnapshot),
                    favoritesPersistenceExecutor)
                .exceptionally(
                    exception -> {
                      logPersistenceFailure(exception);
                      return null;
                    }));
  }

  private void logPersistenceFailure(Throwable exception) {
    Throwable cause = exception;
    while (cause instanceof CompletionException && cause.getCause() != null) {
      cause = cause.getCause();
    }
    LOGGER.log(
        System.Logger.Level.WARNING, "Failed to persist extension catalog preferences", cause);
  }

  private static List<String> normalizeRecentIds(List<String> recentIds) {
    if (recentIds == null || recentIds.isEmpty()) {
      return List.of();
    }
    List<String> normalized = new ArrayList<>(new LinkedHashSet<>(recentIds));
    if (normalized.size() > CatalogRowBuilder.MAX_RECENT_SELECTIONS) {
      normalized = new ArrayList<>(normalized.subList(0, CatalogRowBuilder.MAX_RECENT_SELECTIONS));
    }
    return List.copyOf(normalized);
  }

  private void trimRecentIds() {
    while (recentExtensionIds.size() > CatalogRowBuilder.MAX_RECENT_SELECTIONS) {
      recentExtensionIds.remove(recentExtensionIds.size() - 1);
    }
  }
}
