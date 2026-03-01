package dev.ayagmar.quarkusforge.ui;

public record UiAction(boolean handled, boolean shouldQuit) {
  static UiAction ignored() {
    return new UiAction(false, false);
  }

  static UiAction handled(boolean shouldQuit) {
    return new UiAction(true, shouldQuit);
  }
}
