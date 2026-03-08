package dev.ayagmar.quarkusforge.headless;

import dev.ayagmar.quarkusforge.api.CatalogData;
import dev.ayagmar.quarkusforge.api.ExtensionDto;
import dev.ayagmar.quarkusforge.domain.ValidationError;
import dev.ayagmar.quarkusforge.persistence.ExtensionFavoritesStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

final class HeadlessExtensionResolutionService {
  private static final String PRESET_FAVORITES = "favorites";

  private final HeadlessCatalogLoader catalogLoader;
  private final ExtensionFavoritesStore favoritesStore;

  HeadlessExtensionResolutionService(
      HeadlessCatalogLoader catalogLoader, ExtensionFavoritesStore favoritesStore) {
    this.catalogLoader = Objects.requireNonNull(catalogLoader);
    this.favoritesStore = Objects.requireNonNull(favoritesStore);
  }

  List<String> resolveExtensionIds(
      String platformStream,
      List<String> extensionInputs,
      List<String> presetInputs,
      Set<String> knownExtensionIds,
      Duration timeout)
      throws ExecutionException, InterruptedException, TimeoutException {
    Map<String, List<String>> presetExtensionsByName = Map.of();
    if (requiresBuiltInPresets(presetInputs)) {
      presetExtensionsByName = catalogLoader.loadBuiltInPresets(platformStream, timeout);
    }
    return resolveRequestedExtensions(
        extensionInputs, presetInputs, knownExtensionIds, presetExtensionsByName);
  }

  static List<String> normalizePresets(List<String> presetInputs) {
    return presetInputs.stream()
        .map(HeadlessExtensionResolutionService::normalizePresetName)
        .filter(s -> !s.isEmpty())
        .distinct()
        .toList();
  }

  static List<String> normalizedPresetIdsForComparison(List<String> presetInputs) {
    return normalizePresets(presetInputs).stream().sorted().toList();
  }

  static List<String> normalizedExtensionIdsForComparison(List<String> extensionIds) {
    return extensionIds == null
        ? List.of()
        : extensionIds.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .distinct()
            .sorted()
            .toList();
  }

  static Set<String> knownExtensionIds(CatalogData catalogData) {
    return catalogData.extensions().stream()
        .map(ExtensionDto::id)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  static boolean requiresBuiltInPresets(List<String> presetInputs) {
    for (String presetInput : presetInputs) {
      String preset = normalizePresetName(presetInput);
      if (!preset.isBlank() && !PRESET_FAVORITES.equals(preset)) {
        return true;
      }
    }
    return false;
  }

  private List<String> resolveRequestedExtensions(
      List<String> extensionInputs,
      List<String> presetInputs,
      Set<String> knownExtensionIds,
      Map<String, List<String>> presetExtensionsByName) {
    List<ValidationError> errors = new ArrayList<>();
    LinkedHashSet<String> resolved = new LinkedHashSet<>();

    for (String presetInput : presetInputs) {
      String preset = normalizePresetName(presetInput);
      if (preset.isBlank()) {
        continue;
      }
      if (PRESET_FAVORITES.equals(preset)) {
        favoritesStore.loadFavoriteExtensionIds().stream()
            .filter(knownExtensionIds::contains)
            .forEach(resolved::add);
        continue;
      }
      List<String> presetExtensions = presetExtensionsByName.get(preset);
      if (presetExtensions == null) {
        List<String> allowed = new ArrayList<>(presetExtensionsByName.keySet());
        allowed.add(PRESET_FAVORITES);
        allowed.sort(String::compareTo);
        errors.add(
            new ValidationError(
                "preset",
                "unknown preset '" + presetInput + "'. Allowed: " + String.join(", ", allowed)));
        continue;
      }
      resolved.addAll(presetExtensions);
    }

    for (String extensionInput : extensionInputs) {
      if (extensionInput == null || extensionInput.isBlank()) {
        errors.add(new ValidationError("extension", "must not be blank"));
        continue;
      }
      resolved.add(extensionInput.trim());
    }

    for (String extensionId : resolved) {
      if (!knownExtensionIds.contains(extensionId)) {
        errors.add(new ValidationError("extension", "unknown extension id '" + extensionId + "'"));
      }
    }

    if (!errors.isEmpty()) {
      throw new ValidationException(errors);
    }
    return List.copyOf(resolved);
  }

  private static String normalizePresetName(String presetName) {
    if (presetName == null) {
      return "";
    }
    return presetName.trim().toLowerCase(Locale.ROOT);
  }
}
