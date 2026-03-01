package dev.ayagmar.quarkusforge;

import java.time.Duration;

/** Timeout configuration for headless catalog/generation operations. */
final class HeadlessTimeouts {
  private static final Duration DEFAULT_CATALOG_TIMEOUT = Duration.ofSeconds(20);
  private static final Duration DEFAULT_GENERATION_TIMEOUT = Duration.ofMinutes(2);
  private static final String CATALOG_TIMEOUT_PROPERTY =
      "quarkus.forge.headless.catalog-timeout-ms";
  private static final String GENERATION_TIMEOUT_PROPERTY =
      "quarkus.forge.headless.generation-timeout-ms";

  private HeadlessTimeouts() {}

  static Duration catalogTimeout() {
    return durationFromProperty(CATALOG_TIMEOUT_PROPERTY, DEFAULT_CATALOG_TIMEOUT);
  }

  static Duration generationTimeout() {
    return durationFromProperty(GENERATION_TIMEOUT_PROPERTY, DEFAULT_GENERATION_TIMEOUT);
  }

  private static Duration durationFromProperty(String propertyName, Duration fallback) {
    String rawValue = System.getProperty(propertyName);
    if (rawValue == null || rawValue.isBlank()) {
      return fallback;
    }
    try {
      long millis = Long.parseLong(rawValue.trim());
      if (millis <= 0) {
        return fallback;
      }
      return Duration.ofMillis(millis);
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }
}
