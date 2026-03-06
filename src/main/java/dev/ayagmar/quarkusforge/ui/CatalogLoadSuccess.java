package dev.ayagmar.quarkusforge.ui;

import dev.ayagmar.quarkusforge.api.MetadataDto;
import java.util.List;
import java.util.Map;

record CatalogLoadSuccess(
    List<ExtensionCatalogItem> items,
    MetadataDto metadata,
    Map<String, List<String>> presetExtensionsByName,
    CatalogLoadState nextState,
    String statusMessage) {
  CatalogLoadSuccess {
    items = List.copyOf(items);
    presetExtensionsByName = Map.copyOf(presetExtensionsByName);
  }
}
