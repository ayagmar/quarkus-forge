package dev.ayagmar.quarkusforge.ui;

import dev.ayagmar.quarkusforge.api.MetadataDto;
import java.util.LinkedHashMap;
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
    presetExtensionsByName = immutablePresetMap(presetExtensionsByName);
  }

  private static Map<String, List<String>> immutablePresetMap(
      Map<String, List<String>> presetExtensionsByName) {
    if (presetExtensionsByName == null || presetExtensionsByName.isEmpty()) {
      return Map.of();
    }
    Map<String, List<String>> copy = new LinkedHashMap<>();
    presetExtensionsByName.forEach(
        (name, extensions) ->
            copy.put(name, extensions == null ? List.of() : List.copyOf(extensions)));
    return Map.copyOf(copy);
  }
}
