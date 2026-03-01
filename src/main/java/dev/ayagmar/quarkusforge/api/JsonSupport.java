package dev.ayagmar.quarkusforge.api;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonSupport {
  private static final JsonFactory JSON_FACTORY = new JsonFactory();

  private JsonSupport() {}

  public static Map<String, Object> parseObject(String payload) throws IOException {
    Object value = parseValue(payload);
    if (value instanceof Map<?, ?> objectValue) {
      return castObject(objectValue);
    }
    throw new IOException("JSON payload must be an object");
  }

  public static List<Object> parseArray(String payload) throws IOException {
    Object value = parseValue(payload);
    if (value instanceof List<?> listValue) {
      return castArray(listValue);
    }
    throw new IOException("JSON payload must be an array");
  }

  public static Object parseValue(String payload) throws IOException {
    try (JsonParser parser = JSON_FACTORY.createParser(payload)) {
      JsonToken token = parser.nextToken();
      if (token == null) {
        throw new IOException("JSON payload is empty");
      }
      Object value = readValue(parser, token);
      if (parser.nextToken() != null) {
        throw new IOException("JSON payload contains trailing content");
      }
      return value;
    }
  }

  public static byte[] writeBytes(Object value) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (JsonGenerator generator = JSON_FACTORY.createGenerator(outputStream)) {
      writeValue(generator, value);
    }
    return outputStream.toByteArray();
  }

  public static String writeString(Object value) throws IOException {
    return new String(writeBytes(value), StandardCharsets.UTF_8);
  }

  public static String writePrettyString(Object value) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (JsonGenerator generator = JSON_FACTORY.createGenerator(outputStream)) {
      generator.useDefaultPrettyPrinter();
      writeValue(generator, value);
    }
    return outputStream.toString(StandardCharsets.UTF_8);
  }

  private static Object readValue(JsonParser parser, JsonToken token) throws IOException {
    return switch (token) {
      case START_OBJECT -> readObject(parser);
      case START_ARRAY -> readArray(parser);
      case VALUE_STRING -> parser.getText();
      case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> parser.getNumberValue();
      case VALUE_TRUE -> Boolean.TRUE;
      case VALUE_FALSE -> Boolean.FALSE;
      case VALUE_NULL -> null;
      default -> throw new IOException("Unsupported JSON token: " + token);
    };
  }

  private static Map<String, Object> readObject(JsonParser parser) throws IOException {
    Map<String, Object> object = new LinkedHashMap<>();
    while (parser.nextToken() != JsonToken.END_OBJECT) {
      String fieldName = parser.currentName();
      JsonToken fieldToken = parser.nextToken();
      object.put(fieldName, readValue(parser, fieldToken));
    }
    return object;
  }

  private static List<Object> readArray(JsonParser parser) throws IOException {
    List<Object> values = new ArrayList<>();
    while (parser.nextToken() != JsonToken.END_ARRAY) {
      values.add(readValue(parser, parser.currentToken()));
    }
    return values;
  }

  private static Map<String, Object> castObject(Map<?, ?> value) throws IOException {
    Map<String, Object> object = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : value.entrySet()) {
      if (!(entry.getKey() instanceof String key)) {
        throw new IOException("JSON object keys must be strings");
      }
      object.put(key, entry.getValue());
    }
    return object;
  }

  private static List<Object> castArray(List<?> value) {
    return new ArrayList<>(value);
  }

  @SuppressWarnings("unchecked")
  private static void writeValue(JsonGenerator generator, Object value) throws IOException {
    if (value == null) {
      generator.writeNull();
      return;
    }
    if (value instanceof String stringValue) {
      generator.writeString(stringValue);
      return;
    }
    if (value instanceof Boolean booleanValue) {
      generator.writeBoolean(booleanValue);
      return;
    }
    if (value instanceof Integer intValue) {
      generator.writeNumber(intValue);
      return;
    }
    if (value instanceof Long longValue) {
      generator.writeNumber(longValue);
      return;
    }
    if (value instanceof Short shortValue) {
      generator.writeNumber(shortValue.intValue());
      return;
    }
    if (value instanceof Byte byteValue) {
      generator.writeNumber(byteValue.intValue());
      return;
    }
    if (value instanceof Float floatValue) {
      generator.writeNumber(floatValue);
      return;
    }
    if (value instanceof Double doubleValue) {
      generator.writeNumber(doubleValue);
      return;
    }
    if (value instanceof java.math.BigInteger bigIntegerValue) {
      generator.writeNumber(bigIntegerValue);
      return;
    }
    if (value instanceof java.math.BigDecimal bigDecimalValue) {
      generator.writeNumber(bigDecimalValue);
      return;
    }
    if (value instanceof Number numberValue) {
      generator.writeNumber(numberValue.toString());
      return;
    }
    if (value instanceof Map<?, ?> mapValue) {
      generator.writeStartObject();
      for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
        generator.writeFieldName(String.valueOf(entry.getKey()));
        writeValue(generator, entry.getValue());
      }
      generator.writeEndObject();
      return;
    }
    if (value instanceof Iterable<?> iterableValue) {
      generator.writeStartArray();
      for (Object element : iterableValue) {
        writeValue(generator, element);
      }
      generator.writeEndArray();
      return;
    }
    if (value.getClass().isArray()) {
      generator.writeStartArray();
      int length = java.lang.reflect.Array.getLength(value);
      for (int index = 0; index < length; index++) {
        writeValue(generator, java.lang.reflect.Array.get(value, index));
      }
      generator.writeEndArray();
      return;
    }
    generator.writeString(String.valueOf(value));
  }
}
