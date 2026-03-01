package dev.ayagmar.quarkusforge.ui;

record SectionJumpResult(boolean moved, String categoryTitle) {
  static SectionJumpResult none() {
    return new SectionJumpResult(false, "");
  }
}
