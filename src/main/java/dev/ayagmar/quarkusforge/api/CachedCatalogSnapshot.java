package dev.ayagmar.quarkusforge.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

record CachedCatalogSnapshot(
    MetadataDto metadata, List<ExtensionDto> extensions, Instant fetchedAt, boolean stale) {
  public CachedCatalogSnapshot {
    metadata = Objects.requireNonNull(metadata);
    extensions = List.copyOf(Objects.requireNonNull(extensions));
    fetchedAt = Objects.requireNonNull(fetchedAt);
    if (extensions.isEmpty()) {
      throw new IllegalArgumentException("cached extensions must not be empty");
    }
  }
}
