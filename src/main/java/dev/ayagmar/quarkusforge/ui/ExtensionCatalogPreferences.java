package dev.ayagmar.quarkusforge.ui;

import dev.ayagmar.quarkusforge.api.ExtensionFavoritesStore;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

final class ExtensionCatalogPreferences {
  private final Set<String> favoriteExtensionIds;
  private final List<String> recentExtensionIds;
  private final ExtensionFavoritesStore favoritesStore;
  private final Executor favoritesPersistenceExecutor;
  private CompletableFuture<Void> preferencePersistenceChain;

  ExtensionCatalogPreferences(
      ExtensionFavoritesStore favoritesStore, Executor favoritesPersistenceExecutor) {
    this.favoritesStore = Objects.requireNonNull(favoritesStore);
    this.favoritesPersistenceExecutor = Objects.requireNonNull(favoritesPersistenceExecutor);
    favoriteExtensionIds = new LinkedHashSet<>(favoritesStore.loadFavoriteExtensionIds());
    recentExtensionIds = new ArrayList<>(favoritesStore.loadRecentExtensionIds());
    preferencePersistenceChain = CompletableFuture.completedFuture(null);
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
    return favoriteExtensionIds;
  }

  List<String> recentIdsView() {
    return recentExtensionIds;
  }

  int favoriteExtensionCount() {
    return favoriteExtensionIds.size();
  }

  boolean isFavorite(String extensionId) {
    return favoriteExtensionIds.contains(extensionId);
  }

  boolean toggleFavorite(String extensionId) {
    boolean isNowFavorite = favoriteExtensionIds.add(extensionId);
    if (!isNowFavorite) {
      favoriteExtensionIds.remove(extensionId);
    }
    persistUserStateAsync();
    return isNowFavorite;
  }

  void recordRecentSelection(String extensionId) {
    recentExtensionIds.remove(extensionId);
    recentExtensionIds.add(0, extensionId);
    while (recentExtensionIds.size() > CatalogRowBuilder.MAX_RECENT_SELECTIONS) {
      recentExtensionIds.remove(recentExtensionIds.size() - 1);
    }
    persistUserStateAsync();
  }

  private void persistUserStateAsync() {
    Set<String> favoriteSnapshot = Set.copyOf(favoriteExtensionIds);
    List<String> recentSnapshot = List.copyOf(recentExtensionIds);
    preferencePersistenceChain =
        preferencePersistenceChain
            .exceptionally(ignored -> null)
            .thenRunAsync(
                () -> favoritesStore.saveAll(favoriteSnapshot, recentSnapshot),
                favoritesPersistenceExecutor);
  }
}
