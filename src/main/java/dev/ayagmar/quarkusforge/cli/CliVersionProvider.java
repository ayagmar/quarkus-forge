package dev.ayagmar.quarkusforge.cli;

import java.util.Objects;

final class CliVersionProvider {
  private static final String UNKNOWN_VERSION = "unknown";

  static String resolveVersion() {
    String implementationVersion = CliVersionProvider.class.getPackage().getImplementationVersion();
    if (isUsableVersion(implementationVersion)) {
      return implementationVersion.strip();
    }
    return UNKNOWN_VERSION;
  }

  private static boolean isUsableVersion(String value) {
    return value != null && !value.isBlank() && !Objects.equals(value.strip(), UNKNOWN_VERSION);
  }
}
