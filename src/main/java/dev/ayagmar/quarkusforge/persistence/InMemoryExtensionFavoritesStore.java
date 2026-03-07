package dev.ayagmar.quarkusforge.persistence;

import java.util.List;
import java.util.Set;

final class InMemoryExtensionFavoritesStore implements ExtensionFavoritesStore {
  private Set<String> favoriteExtensionIds = Set.of();
  private List<String> recentExtensionIds = List.of();

  @Override
  public synchronized Set<String> loadFavoriteExtensionIds() {
    return favoriteExtensionIds;
  }

  @Override
  public synchronized List<String> loadRecentExtensionIds() {
    return recentExtensionIds;
  }

  @Override
  public synchronized void saveAll(
      Set<String> favoriteExtensionIds, List<String> recentExtensionIds) {
    this.favoriteExtensionIds = ExtensionFavoriteIds.normalizeSet(favoriteExtensionIds);
    this.recentExtensionIds = ExtensionFavoriteIds.normalizeList(recentExtensionIds);
  }
}
