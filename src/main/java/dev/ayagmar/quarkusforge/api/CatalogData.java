package dev.ayagmar.quarkusforge.api;

import java.util.List;
import java.util.Objects;

public record CatalogData(
    MetadataDto metadata,
    List<ExtensionDto> extensions,
    CatalogSource source,
    MetadataSource metadataSource,
    boolean stale,
    String detailMessage) {
  public CatalogData {
    metadata = Objects.requireNonNull(metadata);
    source = Objects.requireNonNull(source);
    metadataSource = Objects.requireNonNull(metadataSource);
    extensions = List.copyOf(Objects.requireNonNull(extensions));
    detailMessage = detailMessage == null ? "" : detailMessage.strip();

    if (extensions.isEmpty()) {
      throw new IllegalArgumentException("CatalogData.extensions must not be empty");
    }
    if (source != CatalogSource.CACHE && stale) {
      throw new IllegalArgumentException("Only cache-sourced catalog data can be stale");
    }
    if (source == CatalogSource.CACHE && metadataSource != MetadataSource.CACHE) {
      throw new IllegalArgumentException("Cache-sourced catalog data must use cache metadata");
    }
    if (source == CatalogSource.LIVE && metadataSource == MetadataSource.CACHE) {
      throw new IllegalArgumentException("Live catalog data cannot use cache metadata");
    }
  }

  public CatalogData(
      MetadataDto metadata,
      List<ExtensionDto> extensions,
      CatalogSource source,
      boolean stale,
      String detailMessage) {
    this(
        metadata,
        extensions,
        source,
        source == CatalogSource.CACHE ? MetadataSource.CACHE : MetadataSource.LIVE,
        stale,
        detailMessage);
  }

  /** Returns the source label with a "[stale]" suffix when applicable. */
  public String sourceLabel() {
    return stale ? source.label() + " [stale]" : source.label();
  }

  /** Returns the metadata source label with a "[stale]" suffix when applicable. */
  public String metadataSourceLabel() {
    return metadataSource == MetadataSource.CACHE && stale
        ? metadataSource.label() + " [stale]"
        : metadataSource.label();
  }
}
