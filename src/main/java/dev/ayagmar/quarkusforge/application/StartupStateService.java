package dev.ayagmar.quarkusforge.application;

@FunctionalInterface
public interface StartupStateService {
  StartupState resolve(StartupRequest request);
}
