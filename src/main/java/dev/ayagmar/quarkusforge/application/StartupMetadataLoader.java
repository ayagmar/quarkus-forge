package dev.ayagmar.quarkusforge.application;

@FunctionalInterface
public interface StartupMetadataLoader {
  StartupMetadataSelection load();
}
