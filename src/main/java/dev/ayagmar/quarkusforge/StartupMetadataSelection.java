package dev.ayagmar.quarkusforge;

import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import java.util.Objects;

record StartupMetadataSelection(
    MetadataCompatibilityContext metadataCompatibility, String sourceLabel, String detailMessage) {
  StartupMetadataSelection {
    Objects.requireNonNull(metadataCompatibility);
    sourceLabel = sourceLabel == null ? "" : sourceLabel.strip();
    detailMessage = detailMessage == null ? "" : detailMessage.strip();
  }
}
