package dev.ayagmar.quarkusforge.application;

import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import java.util.Objects;

public record StartupMetadataSelection(
    MetadataCompatibilityContext metadataCompatibility, String sourceLabel, String detailMessage) {
  public StartupMetadataSelection {
    Objects.requireNonNull(metadataCompatibility);
    sourceLabel = sourceLabel == null ? "" : sourceLabel.strip();
    detailMessage = detailMessage == null ? "" : detailMessage.strip();
  }
}
