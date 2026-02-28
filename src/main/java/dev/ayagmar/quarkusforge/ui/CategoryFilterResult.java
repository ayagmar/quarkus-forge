package dev.ayagmar.quarkusforge.ui;

record CategoryFilterResult(boolean filtered, String categoryTitle, int matchCount) {
  static CategoryFilterResult none(int matchCount) {
    return new CategoryFilterResult(false, "", matchCount);
  }
}
