package dev.ayagmar.quarkusforge.util;

import java.nio.file.Path;

public final class OutputPathResolver {
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

  private static String expandHomePrefix(String outputDirectory) {
    if ("~".equals(outputDirectory)) {
      return userHome();
    }
    if (outputDirectory.startsWith("~/") || outputDirectory.startsWith("~\\")) {
      return userHome() + outputDirectory.substring(1);
    }
    return outputDirectory;
  }

  private static String userHome() {
    return System.getProperty("user.home", "");
  }
}
