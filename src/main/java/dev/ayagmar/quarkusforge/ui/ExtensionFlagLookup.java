package dev.ayagmar.quarkusforge.ui;

@FunctionalInterface
interface ExtensionFlagLookup {
  boolean matches(String extensionId);
}
