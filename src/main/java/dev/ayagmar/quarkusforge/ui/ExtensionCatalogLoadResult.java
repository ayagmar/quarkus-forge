package dev.ayagmar.quarkusforge.ui;

import dev.ayagmar.quarkusforge.api.CatalogSource;
import dev.ayagmar.quarkusforge.api.ExtensionDto;
import dev.ayagmar.quarkusforge.api.MetadataDto;
import dev.ayagmar.quarkusforge.api.MetadataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public record ExtensionCatalogLoadResult(
    List<ExtensionDto> extensions,
    CatalogSource source,
    MetadataSource metadataSource,
    boolean stale,
    String detailMessage,
    MetadataDto metadata,
    Map<String, List<String>> presetExtensionsByName) {
  public ExtensionCatalogLoadResult {
    extensions = List.copyOf(Objects.requireNonNull(extensions));
    source = Objects.requireNonNull(source);
    metadataSource = Objects.requireNonNull(metadataSource);
    detailMessage = detailMessage == null ? "" : detailMessage.strip();
    presetExtensionsByName = normalizePresetMap(presetExtensionsByName);

    if (source != CatalogSource.CACHE && stale) {
      throw new IllegalArgumentException("stale flag is allowed only for cache source");
    }
    if (source == CatalogSource.CACHE && metadataSource != MetadataSource.CACHE) {
      throw new IllegalArgumentException("cache source must use cache metadata");
    }
    if (source == CatalogSource.LIVE && metadataSource == MetadataSource.CACHE) {
      throw new IllegalArgumentException("live source cannot use cache metadata");
    }
  }

  public ExtensionCatalogLoadResult(
      List<ExtensionDto> extensions,
      CatalogSource source,
      boolean stale,
      String detailMessage,
      MetadataDto metadata,
      Map<String, List<String>> presetExtensionsByName) {
    this(
        extensions,
        source,
        source == CatalogSource.CACHE ? MetadataSource.CACHE : MetadataSource.LIVE,
        stale,
        detailMessage,
        metadata,
        presetExtensionsByName);
  }

  public ExtensionCatalogLoadResult(
      List<ExtensionDto> extensions,
      CatalogSource source,
      boolean stale,
      String detailMessage,
      MetadataDto metadata) {
    this(extensions, source, stale, detailMessage, metadata, Map.of());
  }

  public static ExtensionCatalogLoadResult live(List<ExtensionDto> extensions) {
    return new ExtensionCatalogLoadResult(
        extensions, CatalogSource.LIVE, MetadataSource.LIVE, false, "", null, Map.of());
  }

  public String metadataSourceLabel() {
    return metadataSource == MetadataSource.CACHE && stale
        ? metadataSource.label() + " [stale]"
        : metadataSource.label();
  }

  private static Map<String, List<String>> normalizePresetMap(
      Map<String, List<String>> presetExtensionsByName) {
    if (presetExtensionsByName == null || presetExtensionsByName.isEmpty()) {
      return Map.of();
    }
    LinkedHashMap<String, List<String>> normalized = new LinkedHashMap<>();
    for (Map.Entry<String, List<String>> entry : presetExtensionsByName.entrySet()) {
      if (entry.getKey() == null || entry.getKey().isBlank()) {
        continue;
      }
      String key = entry.getKey().trim().toLowerCase(Locale.ROOT);
      List<String> extensions =
          entry.getValue() == null ? List.of() : List.copyOf(entry.getValue());
      normalized.put(key, extensions);
    }
    return Map.copyOf(normalized);
  }
}
