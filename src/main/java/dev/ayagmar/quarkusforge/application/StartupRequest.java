package dev.ayagmar.quarkusforge.application;

import dev.ayagmar.quarkusforge.domain.CliPrefill;
import java.util.Objects;

public record StartupRequest(
    CliPrefill requestedPrefill, CliPrefill storedPrefill, StartupMetadataLoader metadataLoader) {
  public StartupRequest {
    requestedPrefill = Objects.requireNonNull(requestedPrefill);
    metadataLoader = Objects.requireNonNull(metadataLoader);
  }
}
