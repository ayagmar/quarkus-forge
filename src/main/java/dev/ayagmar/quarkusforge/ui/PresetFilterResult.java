package dev.ayagmar.quarkusforge.ui;

record PresetFilterResult(boolean filtered, String presetName, int matchCount) {
  static PresetFilterResult none(int matchCount) {
    return new PresetFilterResult(false, "", matchCount);
  }
}
