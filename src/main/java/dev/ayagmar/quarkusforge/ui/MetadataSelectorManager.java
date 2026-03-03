package dev.ayagmar.quarkusforge.ui;

import dev.ayagmar.quarkusforge.api.MetadataDto;
import dev.ayagmar.quarkusforge.api.PlatformStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the metadata selector state (platform stream, build tool, java version) for the TUI form.
 * Encapsulates option resolution and cycling.
 */
final class MetadataSelectorManager {
  private List<String> availableBuildTools = List.of();
  private List<String> availableJavaVersions = List.of();
  private List<String> availablePlatformStreams = List.of();

  MetadataSelectorManager() {}

  List<String> availableBuildTools() {
    return availableBuildTools;
  }

  List<String> availableJavaVersions() {
    return availableJavaVersions;
  }

  List<String> availablePlatformStreams() {
    return availablePlatformStreams;
  }

  /**
   * Resolves available options from metadata and returns the normalized (platform, buildTool,
   * javaVersion) tuple.
   */
  ResolvedSelections sync(
      MetadataDto metadataSnapshot,
      String currentPlatformStream,
      String currentBuildTool,
      String currentJavaVersion) {
    availableBuildTools =
        resolveSelectorOptions(
            metadataSnapshot == null ? List.of() : metadataSnapshot.buildTools(), currentBuildTool);
    availableJavaVersions =
        resolveSelectorOptions(
            metadataSnapshot == null ? List.of() : metadataSnapshot.javaVersions(),
            currentJavaVersion);
    availablePlatformStreams =
        resolvePlatformStreamOptions(metadataSnapshot, currentPlatformStream);

    String selectedPlatformStream =
        normalizeSelectedPlatformStream(
            currentPlatformStream, metadataSnapshot, availablePlatformStreams);
    String selectedBuildTool = normalizeSelectedOption(currentBuildTool, availableBuildTools);
    String selectedJavaVersion = normalizeSelectedOption(currentJavaVersion, availableJavaVersions);

    return new ResolvedSelections(selectedPlatformStream, selectedBuildTool, selectedJavaVersion);
  }

  /**
   * Cycles the selector for the given focus target by the given delta. Returns the new selected
   * value, or null if cycling had no effect.
   */
  String cycle(FocusTarget target, String currentValue, int delta) {
    List<String> options = optionsFor(target);
    if (options.isEmpty()) {
      return null;
    }
    int currentIndex = indexOfOption(options, currentValue);
    if (currentIndex < 0) {
      currentIndex = 0;
    }
    int selectedIndex = Math.floorMod(currentIndex + delta, options.size());
    return options.get(selectedIndex);
  }

  /**
   * Selects the first or last option for the given selector. Returns the new selected value, or
   * null if no options available.
   */
  String selectEdge(FocusTarget target, boolean first) {
    List<String> options = optionsFor(target);
    if (options.isEmpty()) {
      return null;
    }
    return first ? options.getFirst() : options.getLast();
  }

  List<String> optionsFor(FocusTarget target) {
    return switch (target) {
      case PLATFORM_STREAM -> availablePlatformStreams;
      case BUILD_TOOL -> availableBuildTools;
      case JAVA_VERSION -> availableJavaVersions;
      case GROUP_ID,
          ARTIFACT_ID,
          VERSION,
          PACKAGE_NAME,
          OUTPUT_DIR,
          EXTENSION_SEARCH,
          EXTENSION_LIST,
          SUBMIT ->
          List.of();
    };
  }

  /** Returns selector position info for a given focus target. */
  MetadataPanelSnapshot.SelectorInfo selectorInfo(FocusTarget target, String currentValue) {
    List<String> options = optionsFor(target);
    if (options.isEmpty()) {
      return MetadataPanelSnapshot.SelectorInfo.EMPTY;
    }
    int index = indexOfOption(options, currentValue);
    return new MetadataPanelSnapshot.SelectorInfo(Math.max(0, index), options.size());
  }

  static boolean isSelectorFocus(FocusTarget focusTarget) {
    return focusTarget == FocusTarget.PLATFORM_STREAM
        || focusTarget == FocusTarget.BUILD_TOOL
        || focusTarget == FocusTarget.JAVA_VERSION;
  }

  // ── Private helpers ───────────────────────────────────────────────────

  static String optionDisplayLabel(
      FocusTarget target, String option, MetadataDto metadataSnapshot) {
    if (target != FocusTarget.PLATFORM_STREAM) {
      return option.isBlank() ? "default" : option;
    }
    if (option.isBlank()) {
      return "default";
    }
    if (metadataSnapshot == null) {
      return option;
    }
    PlatformStream platformStream = metadataSnapshot.findPlatformStream(option);
    if (platformStream == null) {
      return option;
    }
    return platformStream.recommended()
        ? platformStream.platformVersion() + "*"
        : platformStream.platformVersion();
  }

  private static List<String> resolveSelectorOptions(
      List<String> metadataValues, String fallbackValue) {
    List<String> options = new ArrayList<>();
    for (String metadataValue : metadataValues) {
      if (metadataValue == null || metadataValue.isBlank()) {
        continue;
      }
      if (indexOfOption(options, metadataValue) < 0) {
        options.add(metadataValue);
      }
    }
    if (options.isEmpty() && fallbackValue != null && !fallbackValue.isBlank()) {
      options.add(fallbackValue);
    }
    return List.copyOf(options);
  }

  private static List<String> resolvePlatformStreamOptions(
      MetadataDto metadataSnapshot, String fallbackValue) {
    List<String> options = new ArrayList<>();
    if (metadataSnapshot != null) {
      for (PlatformStream platformStream : metadataSnapshot.platformStreams()) {
        if (platformStream.key().isBlank()) {
          continue;
        }
        if (indexOfOption(options, platformStream.key()) < 0) {
          options.add(platformStream.key());
        }
      }
    }
    if (options.isEmpty()) {
      if (fallbackValue != null && !fallbackValue.isBlank()) {
        options.add(fallbackValue);
      } else {
        options.add("");
      }
    }
    return List.copyOf(options);
  }

  private static String normalizeSelectedOption(String currentValue, List<String> options) {
    if (options.isEmpty()) {
      return currentValue == null ? "" : currentValue.trim();
    }
    int index = indexOfOption(options, currentValue);
    return index >= 0 ? options.get(index) : options.getFirst();
  }

  private static String normalizeSelectedPlatformStream(
      String currentValue, MetadataDto metadataSnapshot, List<String> options) {
    int explicitIndex = indexOfOption(options, currentValue);
    if (explicitIndex >= 0) {
      return options.get(explicitIndex);
    }
    if (metadataSnapshot != null && !metadataSnapshot.platformStreams().isEmpty()) {
      int recommendedIndex =
          indexOfOption(options, metadataSnapshot.recommendedPlatformStreamKey());
      if (recommendedIndex >= 0) {
        return options.get(recommendedIndex);
      }
    }
    return options.isEmpty() ? "" : options.getFirst();
  }

  static int indexOfOption(List<String> options, String candidate) {
    if (candidate == null || candidate.isBlank()) {
      return options.indexOf("");
    }
    for (int i = 0; i < options.size(); i++) {
      if (options.get(i).equalsIgnoreCase(candidate.trim())) {
        return i;
      }
    }
    return -1;
  }

  record ResolvedSelections(String platformStream, String buildTool, String javaVersion) {}
}
