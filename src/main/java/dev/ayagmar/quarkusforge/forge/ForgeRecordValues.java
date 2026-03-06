package dev.ayagmar.quarkusforge.forge;

import java.util.List;

final class ForgeRecordValues {
  private ForgeRecordValues() {}

  static String normalizeDocument(String value) {
    return value == null ? null : value.trim();
  }

  static String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  static List<String> copyOrNull(List<String> values) {
    return values == null ? null : List.copyOf(values);
  }

  static List<String> copyOrEmpty(List<String> values) {
    if (values == null) {
      return List.of();
    }
    return List.copyOf(values);
  }
}
