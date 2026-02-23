package dev.ayagmar.quarkusforge.domain;

import java.util.Locale;
import java.util.regex.Pattern;

public final class CliPrefillMapper {
  private static final Pattern NON_PACKAGE_CHARS = Pattern.compile("[^A-Za-z0-9_]");

  private CliPrefillMapper() {}

  public static ProjectRequest map(CliPrefill prefill) {
    String effectivePackage = prefill.packageName();
    if (effectivePackage == null || effectivePackage.isBlank()) {
      effectivePackage = derivePackageName(prefill.groupId(), prefill.artifactId());
    }

    return new ProjectRequest(
        trim(prefill.groupId()),
        trim(prefill.artifactId()),
        trim(prefill.version()),
        trim(effectivePackage),
        trim(prefill.outputDirectory()),
        trim(prefill.platformStream()),
        trim(prefill.buildTool()),
        trim(prefill.javaVersion()));
  }

  static String derivePackageName(String groupId, String artifactId) {
    String group = trim(groupId).toLowerCase(Locale.ROOT);
    String artifact = normalizeArtifactForPackage(trim(artifactId));

    if (artifact.isBlank()) {
      return group;
    }
    if (group.isBlank()) {
      return artifact;
    }
    return group + "." + artifact;
  }

  private static String normalizeArtifactForPackage(String artifactId) {
    String[] segments = artifactId.toLowerCase(Locale.ROOT).replace('-', '.').split("\\.");
    StringBuilder normalized = new StringBuilder();
    for (String rawSegment : segments) {
      String segment = NON_PACKAGE_CHARS.matcher(rawSegment).replaceAll("");
      if (segment.isBlank()) {
        continue;
      }
      if (Character.isDigit(segment.charAt(0))) {
        segment = "x" + segment;
      }

      if (normalized.length() > 0) {
        normalized.append('.');
      }
      normalized.append(segment);
    }
    return normalized.toString();
  }

  private static String trim(String value) {
    if (value == null) {
      return "";
    }
    return value.trim();
  }
}
