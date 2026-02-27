package dev.ayagmar.quarkusforge.api;

import java.util.List;
import java.util.Objects;

public record CatalogData(
    MetadataDto metadata,
    List<ExtensionDto> extensions,
    CatalogSource source,
    boolean stale,
    String detailMessage) {
  public CatalogData {
    metadata = Objects.requireNonNull(metadata);
    source = Objects.requireNonNull(source);
    extensions = List.copyOf(Objects.requireNonNull(extensions));
    detailMessage = detailMessage == null ? "" : detailMessage.strip();

    if (extensions.isEmpty()) {
      throw new IllegalArgumentException("CatalogData.extensions must not be empty");
    }
    if (source != CatalogSource.CACHE && stale) {
      throw new IllegalArgumentException("Only cache-sourced catalog data can be stale");
    }
  }
}
