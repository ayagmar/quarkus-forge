package dev.ayagmar.quarkusforge.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reusable JSON field reading helpers for navigating parsed JSON object maps. Eliminates duplicated
 * readString/readInt/readStringList methods across multiple store classes.
 */
public final class JsonFieldReader {
  private JsonFieldReader() {}

  public static String readString(Map<String, Object> source, String fieldName) {
    Object value = source.get(fieldName);
    if (value == null) {
      return null;
    }
    if (value instanceof String text) {
      return text;
    }
    throw new ApiContractException("Malformed JSON payload");
  }

  public static String readStringOrEmpty(Map<String, Object> source, String fieldName) {
    String value = readString(source, fieldName);
    return value == null ? "" : value;
  }

  public static Integer readInt(Map<String, Object> source, String fieldName) {
    Object value = source.get(fieldName);
    if (value == null) {
      return null;
    }
    if (!(value instanceof Number number)) {
      throw new ApiContractException("Malformed JSON payload");
    }
    return toExactInt(number);
  }

  public static Long readLong(Map<String, Object> source, String fieldName) {
    Object value = source.get(fieldName);
    if (value == null) {
      return null;
    }
    if (!(value instanceof Number number)) {
      throw new ApiContractException("Malformed JSON payload");
    }
    return toExactLong(number);
  }

  public static List<String> readStringList(Map<String, Object> source, String fieldName) {
    Object value = source.get(fieldName);
    if (value == null) {
      return null;
    }
    if (!(value instanceof List<?> rawList)) {
      throw new ApiContractException("Malformed JSON payload");
    }
    List<String> values = new ArrayList<>();
    for (Object element : rawList) {
      if (!(element instanceof String stringValue)) {
        throw new ApiContractException("Malformed JSON payload");
      }
      values.add(stringValue);
    }
    return List.copyOf(values);
  }

  public static List<String> readStringListOrEmpty(Map<String, Object> source, String fieldName) {
    List<String> value = readStringList(source, fieldName);
    return value == null ? List.of() : value;
  }

  public static Set<String> readStringSet(Map<String, Object> source, String fieldName) {
    List<String> values = readStringList(source, fieldName);
    if (values == null) {
      return null;
    }
    return Set.copyOf(values);
  }

  public static Map<String, Object> readObject(Map<String, Object> source, String fieldName) {
    Object value = source.get(fieldName);
    if (value == null) {
      return null;
    }
    if (!(value instanceof Map<?, ?> rawObject)) {
      throw new ApiContractException("Malformed JSON payload");
    }
    Map<String, Object> object = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : rawObject.entrySet()) {
      if (!(entry.getKey() instanceof String key)) {
        throw new ApiContractException("Malformed JSON payload");
      }
      object.put(key, entry.getValue());
    }
    return object;
  }

  public static List<Object> readArray(Map<String, Object> source, String fieldName) {
    Object value = source.get(fieldName);
    if (value == null) {
      return null;
    }
    if (!(value instanceof List<?> rawArray)) {
      throw new ApiContractException("Malformed JSON payload");
    }
    return new ArrayList<>(rawArray);
  }

  private static int toExactInt(Number number) {
    try {
      return new java.math.BigDecimal(number.toString()).intValueExact();
    } catch (ArithmeticException e) {
      throw new ApiContractException("Malformed JSON payload");
    }
  }

  private static long toExactLong(Number number) {
    try {
      return new java.math.BigDecimal(number.toString()).longValueExact();
    } catch (ArithmeticException e) {
      throw new ApiContractException("Malformed JSON payload");
    }
  }
}
