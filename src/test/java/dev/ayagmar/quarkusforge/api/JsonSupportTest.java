package dev.ayagmar.quarkusforge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonSupportTest {
  @Test
  void parseObjectReadsSimpleJsonObject() throws Exception {
    Map<String, Object> root = JsonSupport.parseObject("{\"key\":\"value\"}");

    assertThat(root).containsEntry("key", "value");
  }

  @Test
  void writeStringSerializesNestedValues() throws Exception {
    String payload =
        JsonSupport.writeString(
            Map.of("name", "forge", "versions", List.of("21", "25"), "enabled", true));

    assertThat(payload).contains("\"name\":\"forge\"");
    assertThat(payload).contains("\"versions\":[\"21\",\"25\"]");
    assertThat(payload).contains("\"enabled\":true");
  }

  // ── parseArray ──────────────────────────────────────────────────────

  @Test
  void parseArrayReadsJsonArray() throws Exception {
    List<Object> array = JsonSupport.parseArray("[1,\"two\",true,null]");
    assertThat(array).containsExactly(1, "two", true, null);
  }

  @Test
  void parseArrayThrowsForObjectPayload() {
    assertThatThrownBy(() -> JsonSupport.parseArray("{\"key\":\"value\"}"))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("must be an array");
  }

  @Test
  void parseObjectThrowsForArrayPayload() {
    assertThatThrownBy(() -> JsonSupport.parseObject("[1,2,3]"))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("must be an object");
  }

  @Test
  void parseObjectThrowsForEmptyPayload() {
    assertThatThrownBy(() -> JsonSupport.parseObject(""))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("empty");
  }

  // ── writeBytes ──────────────────────────────────────────────────────

  @Test
  void writeBytesProducesValidJson() throws Exception {
    byte[] bytes = JsonSupport.writeBytes(Map.of("a", 1));
    String json = new String(bytes, StandardCharsets.UTF_8);
    assertThat(json).contains("\"a\":1");
  }

  // ── writeValue for various Number types ──────────────────────────────

  @Test
  void writeNullValue() throws Exception {
    String json = JsonSupport.writeString(nullValueMap());
    assertThat(json).contains("\"v\":null");
  }

  @Test
  void writeLongValue() throws Exception {
    assertThat(JsonSupport.writeString(Map.of("v", 42L))).contains("\"v\":42");
  }

  @Test
  void writeShortValue() throws Exception {
    assertThat(JsonSupport.writeString(Map.of("v", (short) 7))).contains("\"v\":7");
  }

  @Test
  void writeByteValue() throws Exception {
    assertThat(JsonSupport.writeString(Map.of("v", (byte) 3))).contains("\"v\":3");
  }

  @Test
  void writeFloatValue() throws Exception {
    String json = JsonSupport.writeString(Map.of("v", 1.5f));
    assertThat(json).contains("\"v\":1.5");
  }

  @Test
  void writeDoubleValue() throws Exception {
    String json = JsonSupport.writeString(Map.of("v", 2.5d));
    assertThat(json).contains("\"v\":2.5");
  }

  @Test
  void writeBigIntegerValue() throws Exception {
    String json = JsonSupport.writeString(Map.of("v", BigInteger.valueOf(999)));
    assertThat(json).contains("\"v\":999");
  }

  @Test
  void writeBigDecimalValue() throws Exception {
    String json = JsonSupport.writeString(Map.of("v", new BigDecimal("3.14")));
    assertThat(json).contains("\"v\":3.14");
  }

  @Test
  void writeBooleanFalseValue() throws Exception {
    assertThat(JsonSupport.writeString(Map.of("v", false))).contains("\"v\":false");
  }

  @Test
  void writeArrayValue() throws Exception {
    String json = JsonSupport.writeString(Map.of("v", new int[] {1, 2}));
    assertThat(json).contains("\"v\":[1,2]");
  }

  @Test
  void writeNestedObject() throws Exception {
    Map<String, Object> inner = new LinkedHashMap<>();
    inner.put("nested", "value");
    String json = JsonSupport.writeString(Map.of("outer", inner));
    assertThat(json).contains("\"outer\":{\"nested\":\"value\"}");
  }

  @Test
  void writePrettyStringProducesPrettyOutput() throws Exception {
    String json = JsonSupport.writePrettyString(Map.of("a", 1));
    assertThat(json).contains("\n"); // pretty-printed has newlines
    assertThat(json).contains("\"a\"");
  }

  @Test
  void writeUnknownTypeConvertsToString() throws Exception {
    // An object type not recognized by writeValue falls to String.valueOf
    Object custom =
        new Object() {
          @Override
          public String toString() {
            return "custom-object";
          }
        };
    String json = JsonSupport.writeString(Map.of("v", custom));
    assertThat(json).contains("custom-object");
  }

  @Test
  void parseObjectHandlesTrailingContent() {
    assertThatThrownBy(() -> JsonSupport.parseObject("{\"a\":1} extra"))
        .isInstanceOf(IOException.class);
  }

  @Test
  void parseObjectHandlesNestedObjectsAndArrays() throws Exception {
    String input = "{\"list\":[1,2],\"obj\":{\"k\":\"v\"},\"b\":false,\"n\":null}";
    Map<String, Object> root = JsonSupport.parseObject(input);

    assertThat(root.get("list")).isInstanceOf(List.class);
    assertThat(root.get("obj")).isInstanceOf(Map.class);
    assertThat(root.get("b")).isEqualTo(false);
    assertThat(root.get("n")).isNull();
  }

  private static Map<String, Object> nullValueMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("v", null);
    return map;
  }
}
