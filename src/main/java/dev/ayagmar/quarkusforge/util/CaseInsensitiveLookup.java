package dev.ayagmar.quarkusforge.util;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class CaseInsensitiveLookup {
  private CaseInsensitiveLookup() {}

  public static boolean contains(List<String> values, String expected) {
    Objects.requireNonNull(values);
    if (expected == null) {
      return false;
    }
    String normalizedExpected = normalize(expected);
    for (String value : values) {
      if (normalize(value).equals(normalizedExpected)) {
        return true;
      }
    }
    return false;
  }

  public static <V> V find(Map<String, V> values, String expectedKey) {
    Objects.requireNonNull(values);
    if (expectedKey == null) {
      return null;
    }
    String normalizedExpected = normalize(expectedKey);
    for (var entry : values.entrySet()) {
      if (normalize(entry.getKey()).equals(normalizedExpected)) {
        return entry.getValue();
      }
    }
    return null;
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }
}
