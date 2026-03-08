package dev.ayagmar.quarkusforge.ui;

import java.util.List;

final class UiFocusTargets {
  private static final List<FocusTarget> ORDER =
      List.of(
          FocusTarget.GROUP_ID,
          FocusTarget.ARTIFACT_ID,
          FocusTarget.BUILD_TOOL,
          FocusTarget.PLATFORM_STREAM,
          FocusTarget.VERSION,
          FocusTarget.PACKAGE_NAME,
          FocusTarget.JAVA_VERSION,
          FocusTarget.OUTPUT_DIR,
          FocusTarget.EXTENSION_SEARCH,
          FocusTarget.EXTENSION_LIST,
          FocusTarget.SUBMIT);

  private UiFocusTargets() {}

  static List<FocusTarget> ordered() {
    return ORDER;
  }

  static FocusTarget move(FocusTarget current, int offset) {
    int index = ORDER.indexOf(current);
    int nextIndex = Math.floorMod(index + offset, ORDER.size());
    return ORDER.get(nextIndex);
  }

  static String nameOf(FocusTarget target) {
    return switch (target) {
      case GROUP_ID -> "groupId";
      case ARTIFACT_ID -> "artifactId";
      case VERSION -> "version";
      case PACKAGE_NAME -> "packageName";
      case OUTPUT_DIR -> "outputDirectory";
      case PLATFORM_STREAM -> "platformStream";
      case BUILD_TOOL -> "buildTool";
      case JAVA_VERSION -> "javaVersion";
      case EXTENSION_SEARCH -> "extensionSearch";
      case EXTENSION_LIST -> "extensionList";
      case SUBMIT -> "submit";
    };
  }

  static String displayNameOf(FocusTarget target) {
    return switch (target) {
      case GROUP_ID -> "Group ID";
      case ARTIFACT_ID -> "Artifact ID";
      case VERSION -> "Version";
      case PACKAGE_NAME -> "Package name";
      case OUTPUT_DIR -> "Output directory";
      case PLATFORM_STREAM -> "Platform stream";
      case BUILD_TOOL -> "Build tool";
      case JAVA_VERSION -> "Java version";
      case EXTENSION_SEARCH -> "Extension search";
      case EXTENSION_LIST -> "Extension list";
      case SUBMIT -> "Submit";
    };
  }
}
