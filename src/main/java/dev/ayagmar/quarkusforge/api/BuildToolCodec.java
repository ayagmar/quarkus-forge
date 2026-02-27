package dev.ayagmar.quarkusforge.api;

import java.util.Locale;

public final class BuildToolCodec {
  private BuildToolCodec() {}

  public static String toApiValue(String uiValue) {
    String normalized = normalize(uiValue).replace('_', '-');
    return switch (normalized) {
      case "maven" -> "MAVEN";
      case "gradle" -> "GRADLE";
      case "gradle-kotlin-dsl" -> "GRADLE_KOTLIN_DSL";
      default -> normalized.toUpperCase(Locale.ROOT).replace('-', '_');
    };
  }

  public static String toUiValue(String apiValue) {
    String normalized = normalize(apiValue).replace('-', '_');
    return switch (normalized) {
      case "maven" -> "maven";
      case "gradle" -> "gradle";
      case "gradle_kotlin_dsl" -> "gradle-kotlin-dsl";
      default -> normalized.replace('_', '-');
    };
  }

  private static String normalize(String value) {
    if (value == null) {
      return "";
    }
    return value.trim().toLowerCase(Locale.ROOT);
  }
}
