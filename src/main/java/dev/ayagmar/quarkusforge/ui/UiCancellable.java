package dev.ayagmar.quarkusforge.ui;

@FunctionalInterface
interface UiCancellable {
  boolean cancel();
}
