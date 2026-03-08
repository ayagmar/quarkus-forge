package dev.ayagmar.quarkusforge.application;

import dev.ayagmar.quarkusforge.domain.CliPrefill;
import java.util.Objects;

/**
 * Startup inputs for building the initial UI state.
 *
 * <p>{@code storedPrefill} may be {@code null} when no persisted preferences exist yet.
 */
public record StartupRequest(
    CliPrefill requestedPrefill, CliPrefill storedPrefill, StartupMetadataLoader metadataLoader) {
  public StartupRequest {
    requestedPrefill = Objects.requireNonNull(requestedPrefill);
    metadataLoader = Objects.requireNonNull(metadataLoader);
  }
}
