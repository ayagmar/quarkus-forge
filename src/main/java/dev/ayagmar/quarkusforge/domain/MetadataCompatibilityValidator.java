package dev.ayagmar.quarkusforge.domain;

import dev.ayagmar.quarkusforge.api.MetadataDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class MetadataCompatibilityValidator {
  public ValidationReport validate(ProjectRequest request, MetadataDto metadata) {
    List<ValidationError> errors = new ArrayList<>();

    String buildTool = request.buildTool().toLowerCase(Locale.ROOT);
    String javaVersion = request.javaVersion();

    List<String> availableBuildTools = metadata.buildTools();
    List<String> availableJavaVersions = metadata.javaVersions();

    if (!availableBuildTools.contains(buildTool)) {
      errors.add(
          new ValidationError(
              "buildTool",
              "unsupported build tool '"
                  + request.buildTool()
                  + "'. Allowed: "
                  + String.join(", ", availableBuildTools)));
    }

    if (!availableJavaVersions.contains(javaVersion)) {
      errors.add(
          new ValidationError(
              "javaVersion",
              "unsupported Java version '"
                  + javaVersion
                  + "'. Allowed: "
                  + String.join(", ", availableJavaVersions)));
    }

    Map<String, List<String>> compatibility = metadata.compatibility();
    if (compatibility.isEmpty()) {
      errors.add(new ValidationError("metadata", "compatibility matrix is missing from metadata"));
    } else {
      List<String> allowedForBuildTool = compatibility.get(buildTool);
      if (allowedForBuildTool == null) {
        errors.add(
            new ValidationError(
                "metadata",
                "metadata contract mismatch: missing compatibility entry for build tool '"
                    + buildTool
                    + "'"));
      } else if (!allowedForBuildTool.contains(javaVersion)) {
        String allowed = allowedForBuildTool.stream().sorted().collect(Collectors.joining(", "));
        errors.add(
            new ValidationError(
                "compatibility",
                "unsupported combination: build tool '"
                    + buildTool
                    + "' does not support Java "
                    + javaVersion
                    + ". Allowed Java versions: "
                    + allowed));
      }
    }

    return new ValidationReport(errors);
  }
}
