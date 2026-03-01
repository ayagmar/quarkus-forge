package dev.ayagmar.quarkusforge.ui;

record FavoriteToggleResult(boolean changed, String extensionName, boolean favoriteNow) {
  static FavoriteToggleResult none() {
    return new FavoriteToggleResult(false, "", false);
  }
}
