package dev.ayagmar.quarkusforge.ui;

import dev.ayagmar.quarkusforge.api.CatalogSource;
import dev.ayagmar.quarkusforge.api.ExtensionDto;
import dev.ayagmar.quarkusforge.api.MetadataDto;
import java.util.List;
import java.util.Objects;

public record ExtensionCatalogLoadResult(
    List<ExtensionDto> extensions,
    CatalogSource source,
    boolean stale,
    String detailMessage,
    MetadataDto metadata) {
  public ExtensionCatalogLoadResult {
    extensions = List.copyOf(Objects.requireNonNull(extensions));
    source = Objects.requireNonNull(source);
    detailMessage = detailMessage == null ? "" : detailMessage.strip();

    if (source != CatalogSource.CACHE && stale) {
      throw new IllegalArgumentException("stale flag is allowed only for cache source");
    }
  }

  public static ExtensionCatalogLoadResult live(List<ExtensionDto> extensions) {
    return new ExtensionCatalogLoadResult(extensions, CatalogSource.LIVE, false, "", null);
  }
}
