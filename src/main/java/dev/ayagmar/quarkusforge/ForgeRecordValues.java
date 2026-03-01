package dev.ayagmar.quarkusforge;

import java.util.List;

final class ForgeRecordValues {
  private ForgeRecordValues() {}

  static String normalize(String value) {
    return value == null ? "" : value;
  }

  static List<String> copyOrEmpty(List<String> values) {
    if (values == null) {
      return List.of();
    }
    return List.copyOf(values);
  }
}
