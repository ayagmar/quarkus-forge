package dev.ayagmar.quarkusforge.domain;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class ProjectRequestValidator {
  private static final Pattern GROUP_ID_PATTERN =
      Pattern.compile("^[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*$");
  private static final Pattern ARTIFACT_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9._-]*$");
  private static final Pattern VERSION_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._+-]*$");
  private static final Pattern PACKAGE_NAME_PATTERN =
      Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*$");
  private static final Pattern WINDOWS_INVALID_CHARS = Pattern.compile("[<>:\"|?*\\u0000-\\u001F]");
  private static final Pattern PLATFORM_STREAM_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]+$");
  private static final Pattern BUILD_TOOL_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]*$");
  private static final Pattern JAVA_VERSION_PATTERN = Pattern.compile("^[0-9]+$");

  private static final Set<String> WINDOWS_RESERVED_NAMES =
      Set.of(
          "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7",
          "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

  public ValidationReport validate(ProjectRequest request) {
    List<ValidationError> errors = new ArrayList<>();

    validatePattern(
        request.groupId(),
        GROUP_ID_PATTERN,
        "groupId",
        "must be dot-separated Java identifiers",
        errors);
    validatePattern(
        request.artifactId(),
        ARTIFACT_ID_PATTERN,
        "artifactId",
        "must start with a lowercase letter and contain only lowercase letters, digits, '.', '-' or '_'",
        errors);
    validatePattern(
        request.version(),
        VERSION_PATTERN,
        "version",
        "must contain only letters, digits, '.', '_', '-' or '+'",
        errors);
    validatePattern(
        request.packageName(),
        PACKAGE_NAME_PATTERN,
        "packageName",
        "must be a valid Java package name",
        errors);
    validatePattern(
        request.platformStream(),
        PLATFORM_STREAM_PATTERN,
        "platformStream",
        "must contain only letters, digits, '.', '-', '_' or ':'",
        errors,
        true);
    validatePattern(
        request.buildTool(),
        BUILD_TOOL_PATTERN,
        "buildTool",
        "must be an identifier such as maven or gradle",
        errors);
    validatePattern(
        request.javaVersion(),
        JAVA_VERSION_PATTERN,
        "javaVersion",
        "must be a numeric Java feature version",
        errors);

    validateOutputDirectory(request.outputDirectory(), errors);

    return new ValidationReport(errors);
  }

  private static void validatePattern(
      String value,
      Pattern pattern,
      String field,
      String errorMessage,
      List<ValidationError> errors) {
    validatePattern(value, pattern, field, errorMessage, errors, false);
  }

  private static void validatePattern(
      String value,
      Pattern pattern,
      String field,
      String errorMessage,
      List<ValidationError> errors,
      boolean optional) {
    if (value == null || value.isBlank()) {
      if (!optional) {
        errors.add(new ValidationError(field, "must not be blank"));
      }
      return;
    }

    if (!pattern.matcher(value).matches()) {
      errors.add(new ValidationError(field, errorMessage));
    }
  }

  private static void validateOutputDirectory(
      String outputDirectory, List<ValidationError> errors) {
    if (outputDirectory == null || outputDirectory.isBlank()) {
      errors.add(new ValidationError("outputDirectory", "must not be blank"));
      return;
    }

    try {
      Path.of(outputDirectory).normalize();
    } catch (InvalidPathException invalidPathException) {
      errors.add(
          new ValidationError(
              "outputDirectory", "contains unsupported path characters: " + invalidPathException));
      return;
    }

    String[] segments = outputDirectory.replace('\\', '/').split("/");
    int nonBlankSegmentIndex = 0;
    for (String rawSegment : segments) {
      if (rawSegment.isBlank()) {
        continue;
      }
      if (isWindowsDriveLetterSegment(rawSegment, nonBlankSegmentIndex)) {
        nonBlankSegmentIndex++;
        continue;
      }

      String segment = rawSegment;
      if (".".equals(segment) || "..".equals(segment)) {
        // Intentional: this is a user-selected local destination path. Relative navigation
        // segments are allowed; archive extraction still enforces ZIP entry containment.
        continue;
      }

      if (WINDOWS_INVALID_CHARS.matcher(segment).find()) {
        errors.add(
            new ValidationError(
                "outputDirectory",
                "contains Windows-invalid characters in segment '" + segment + "'"));
        return;
      }

      if (segment.endsWith(".") || segment.endsWith(" ")) {
        errors.add(
            new ValidationError(
                "outputDirectory",
                "contains Windows-invalid trailing dot/space in segment '" + segment + "'"));
        return;
      }

      if (isWindowsReservedName(segment)) {
        errors.add(
            new ValidationError(
                "outputDirectory", "contains Windows reserved name segment '" + segment + "'"));
        return;
      }
      nonBlankSegmentIndex++;
    }
  }

  private static boolean isWindowsDriveLetterSegment(String segment, int nonBlankSegmentIndex) {
    return nonBlankSegmentIndex == 0
        && segment.length() == 2
        && Character.isLetter(segment.charAt(0))
        && segment.charAt(1) == ':';
  }

  private static boolean isWindowsReservedName(String segment) {
    String baseName = segment;
    int extensionIndex = baseName.indexOf('.');
    if (extensionIndex >= 0) {
      baseName = baseName.substring(0, extensionIndex);
    }
    return WINDOWS_RESERVED_NAMES.contains(baseName.toUpperCase(Locale.ROOT));
  }
}
