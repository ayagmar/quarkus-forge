package dev.ayagmar.quarkusforge.runtime;

import java.util.Locale;

final class TerminalBackendPreference {
  private static final String BACKEND_PROPERTY_NAME = "tamboui.backend";
  private static final String BACKEND_ENV_NAME = "TAMBOUI_BACKEND";
  private static final String PANAMA_BACKEND = "panama";
  private static final String JLINE3_BACKEND = "jline3";

  private TerminalBackendPreference() {}

  static String defaultBackendPreference() {
    return defaultBackendPreference(System.getProperty("os.name", ""));
  }

  static void configure() {
    configure(
        System.getProperty(BACKEND_PROPERTY_NAME),
        System.getenv(BACKEND_ENV_NAME),
        System.getProperty("os.name", ""));
  }

  static void configure(String propertyValue, String envValue, String osName) {
    if (isExplicitlyConfigured(propertyValue, envValue)) {
      return;
    }
    System.setProperty(BACKEND_PROPERTY_NAME, defaultBackendPreference(osName));
  }

  static boolean isExplicitlyConfigured(String propertyValue, String envValue) {
    if (propertyValue != null && !propertyValue.isBlank()) {
      return true;
    }
    return envValue != null && !envValue.isBlank();
  }

  static String defaultBackendPreference(String osName) {
    if (isWindowsOsName(osName)) {
      return JLINE3_BACKEND;
    }
    return PANAMA_BACKEND;
  }

  static void restore(String previousBackendPreference) {
    if (previousBackendPreference == null) {
      System.clearProperty(BACKEND_PROPERTY_NAME);
      return;
    }
    System.setProperty(BACKEND_PROPERTY_NAME, previousBackendPreference);
  }

  private static boolean isWindowsOsName(String osName) {
    if (osName == null) {
      return false;
    }
    String normalized = osName.strip().toLowerCase(Locale.ROOT);
    return normalized.startsWith("windows");
  }
}
