package dev.ayagmar.quarkusforge.ui;

record JumpToFavoriteResult(boolean jumped, String extensionName) {
  static JumpToFavoriteResult none() {
    return new JumpToFavoriteResult(false, "");
  }
}
