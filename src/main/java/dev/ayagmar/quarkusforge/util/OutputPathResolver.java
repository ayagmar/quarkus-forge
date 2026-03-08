package dev.ayagmar.quarkusforge.util;

import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import java.nio.file.Path;
import java.util.regex.Pattern;

public final class OutputPathResolver {
  private static final Pattern PATH_SEPARATOR_PATTERN = Pattern.compile("[/\\\\]+");

  private OutputPathResolver() {}

  public static Path resolveOutputRoot(String outputDirectory) {
    String normalizedInput = outputDirectory == null ? "" : outputDirectory.trim();
    String expanded = expandHomePrefix(normalizedInput);
    return Path.of(expanded).toAbsolutePath().normalize();
  }

  public static String absoluteDisplayPath(String outputDirectory) {
    try {
      return resolveOutputRoot(outputDirectory).toString();
    } catch (RuntimeException pathError) {
      return outputDirectory == null ? "" : outputDirectory;
    }
  }

  public static Path resolveGeneratedProjectDirectory(ProjectRequest request) {
    Path outputRoot = resolveOutputRoot(request.outputDirectory());
    return outputRoot.resolve(request.artifactId()).normalize();
  }

  private static String expandHomePrefix(String outputDirectory) {
    if ("~".equals(outputDirectory)) {
      return userHome();
    }
    if (outputDirectory.startsWith("~/") || outputDirectory.startsWith("~\\")) {
      return resolveHomeRelative(outputDirectory.substring(2)).toString();
    }
    return outputDirectory;
  }

  private static String userHome() {
    return System.getProperty("user.home", "");
  }

  private static Path resolveHomeRelative(String relativePath) {
    Path resolved = Path.of(userHome());
    for (String segment : PATH_SEPARATOR_PATTERN.split(relativePath)) {
      if (!segment.isEmpty()) {
        resolved = resolved.resolve(segment);
      }
    }
    return resolved;
  }
}
