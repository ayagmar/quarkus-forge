package dev.ayagmar.quarkusforge.domain;

import dev.ayagmar.quarkusforge.api.MetadataDto;
import dev.ayagmar.quarkusforge.api.PlatformStream;
import dev.ayagmar.quarkusforge.util.CaseInsensitiveLookup;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class MetadataCompatibilityValidator {
  public ValidationReport validate(ProjectRequest request, MetadataDto metadata) {
    List<ValidationError> errors = new ArrayList<>();

    String buildTool = request.buildTool();
    String javaVersion = request.javaVersion();
    String platformStream = request.platformStream();

    List<String> availableBuildTools = metadata.buildTools();
    List<String> availableJavaVersions = metadata.javaVersions();

    if (!CaseInsensitiveLookup.contains(availableBuildTools, buildTool)) {
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
      List<String> allowedForBuildTool = CaseInsensitiveLookup.find(compatibility, buildTool);
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

    if (!metadata.platformStreams().isEmpty()) {
      PlatformStream selectedStream = null;
      if (platformStream != null && !platformStream.isBlank()) {
        selectedStream = metadata.findPlatformStream(platformStream);
        if (selectedStream == null) {
          errors.add(
              new ValidationError(
                  "platformStream",
                  "unsupported platform stream '"
                      + platformStream
                      + "'. Allowed: "
                      + metadata.platformStreams().stream()
                          .map(PlatformStream::key)
                          .collect(Collectors.joining(", "))));
        }
      } else {
        selectedStream = metadata.findPlatformStream(metadata.recommendedPlatformStreamKey());
      }

      if (selectedStream != null && !selectedStream.javaVersions().contains(javaVersion)) {
        String allowed =
            selectedStream.javaVersions().stream().sorted().collect(Collectors.joining(", "));
        errors.add(
            new ValidationError(
                "compatibility",
                "unsupported combination: platform stream '"
                    + selectedStream.key()
                    + "' does not support Java "
                    + javaVersion
                    + ". Allowed Java versions: "
                    + allowed));
      }
    }

    return new ValidationReport(errors);
  }
}
