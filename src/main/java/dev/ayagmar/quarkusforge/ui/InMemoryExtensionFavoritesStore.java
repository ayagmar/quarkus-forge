package dev.ayagmar.quarkusforge.ui;

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
  public synchronized void saveFavoriteExtensionIds(Set<String> favoriteExtensionIds) {
    this.favoriteExtensionIds = ExtensionFavoriteIds.normalizeSet(favoriteExtensionIds);
  }

  @Override
  public synchronized List<String> loadRecentExtensionIds() {
    return recentExtensionIds;
  }

  @Override
  public synchronized void saveRecentExtensionIds(List<String> recentExtensionIds) {
    this.recentExtensionIds = ExtensionFavoriteIds.normalizeList(recentExtensionIds);
  }
}
