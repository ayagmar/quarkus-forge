package dev.ayagmar.quarkusforge.domain;

import java.util.Locale;
import java.util.regex.Pattern;

public final class CliPrefillMapper {
  private static final Pattern NON_PACKAGE_CHARS = Pattern.compile("[^A-Za-z0-9_.]");

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
        trim(prefill.buildTool()),
        trim(prefill.javaVersion()));
  }

  static String derivePackageName(String groupId, String artifactId) {
    String group = trim(groupId).toLowerCase(Locale.ROOT);
    String artifact = trim(artifactId).toLowerCase(Locale.ROOT).replace('-', '.');
    artifact = NON_PACKAGE_CHARS.matcher(artifact).replaceAll("");

    if (artifact.isBlank()) {
      return group;
    }
    if (group.isBlank()) {
      return artifact;
    }
    return group + "." + artifact;
  }

  private static String trim(String value) {
    if (value == null) {
      return "";
    }
    return value.trim();
  }
}
