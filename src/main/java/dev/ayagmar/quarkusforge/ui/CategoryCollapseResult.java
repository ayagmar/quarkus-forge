package dev.ayagmar.quarkusforge.ui;

record CategoryCollapseResult(boolean changed, String categoryTitle, boolean collapsed) {
  static CategoryCollapseResult none() {
    return new CategoryCollapseResult(false, "", false);
  }
}
