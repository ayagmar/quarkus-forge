package dev.ayagmar.quarkusforge.ui;

record SectionFocusResult(boolean moved, String sectionTitle) {
  static SectionFocusResult none() {
    return new SectionFocusResult(false, "");
  }
}
