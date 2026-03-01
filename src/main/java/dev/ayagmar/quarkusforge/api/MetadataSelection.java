package dev.ayagmar.quarkusforge.api;

import java.util.Objects;

record MetadataSelection(MetadataDto metadata, String detailMessage) {
  MetadataSelection {
    metadata = Objects.requireNonNull(metadata);
    detailMessage = detailMessage == null ? "" : detailMessage.strip();
  }
}
