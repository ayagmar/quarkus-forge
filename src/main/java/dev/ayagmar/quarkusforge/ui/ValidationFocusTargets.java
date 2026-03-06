package dev.ayagmar.quarkusforge.ui;

import dev.ayagmar.quarkusforge.domain.ValidationReport;
import java.util.ArrayList;
import java.util.List;

final class ValidationFocusTargets {
  private ValidationFocusTargets() {}

  static FocusTarget firstInvalid(ValidationReport report) {
    return orderedInvalid(report).stream().findFirst().orElse(null);
  }

  static List<FocusTarget> orderedInvalid(ValidationReport report) {
    List<FocusTarget> targets = new ArrayList<>();
    for (var error : report.errors()) {
      FocusTarget target = fromField(error.field());
      if (target != null && !targets.contains(target)) {
        targets.add(target);
      }
    }
    return List.copyOf(targets);
  }

  private static FocusTarget fromField(String fieldName) {
    if (fieldName == null || fieldName.isBlank()) {
      return null;
    }
    return switch (fieldName.trim().toLowerCase()) {
      case "groupid" -> FocusTarget.GROUP_ID;
      case "artifactid" -> FocusTarget.ARTIFACT_ID;
      case "version" -> FocusTarget.VERSION;
      case "packagename" -> FocusTarget.PACKAGE_NAME;
      case "outputdirectory" -> FocusTarget.OUTPUT_DIR;
      case "platformstream" -> FocusTarget.PLATFORM_STREAM;
      case "buildtool" -> FocusTarget.BUILD_TOOL;
      case "javaversion" -> FocusTarget.JAVA_VERSION;
      default -> null;
    };
  }
}
