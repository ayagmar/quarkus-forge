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
    Map<String, Object> payload =
        JsonSupport.parseObject(
            JsonSupport.writeString(
                Map.of("name", "forge", "versions", List.of("21", "25"), "enabled", true)));

    assertThat(payload).containsEntry("name", "forge");
    assertThat(payload.get("versions")).isEqualTo(List.of("21", "25"));
    assertThat(payload).containsEntry("enabled", true);
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
    Map<String, Object> root =
        JsonSupport.parseObject(
            new String(JsonSupport.writeBytes(Map.of("a", 1)), StandardCharsets.UTF_8));
    assertThat(root).containsEntry("a", 1);
  }

  // ── writeValue for various Number types ──────────────────────────────

  @Test
  void writeNullValue() throws Exception {
    assertThat(singleValueFromWritten(nullValueMap())).isNull();
  }

  @Test
  void writeLongValue() throws Exception {
    assertThat(((Number) singleValueFromWritten(Map.of("v", 42L))).longValue()).isEqualTo(42L);
  }

  @Test
  void writeShortValue() throws Exception {
    assertThat(((Number) singleValueFromWritten(Map.of("v", (short) 7))).intValue()).isEqualTo(7);
  }

  @Test
  void writeByteValue() throws Exception {
    assertThat(((Number) singleValueFromWritten(Map.of("v", (byte) 3))).intValue()).isEqualTo(3);
  }

  @Test
  void writeFloatValue() throws Exception {
    assertThat(((Number) singleValueFromWritten(Map.of("v", 1.5f))).doubleValue()).isEqualTo(1.5d);
  }

  @Test
  void writeDoubleValue() throws Exception {
    assertThat(((Number) singleValueFromWritten(Map.of("v", 2.5d))).doubleValue()).isEqualTo(2.5d);
  }

  @Test
  void writeBigIntegerValue() throws Exception {
    assertThat(((Number) singleValueFromWritten(Map.of("v", BigInteger.valueOf(999)))).longValue())
        .isEqualTo(999L);
  }

  @Test
  void writeBigDecimalValue() throws Exception {
    BigDecimal actual =
        new BigDecimal(String.valueOf(singleValueFromWritten(Map.of("v", new BigDecimal("3.14")))));
    assertThat(actual).isEqualTo(new BigDecimal("3.14"));
  }

  @Test
  void writeBooleanFalseValue() throws Exception {
    assertThat(singleValueFromWritten(Map.of("v", false))).isEqualTo(false);
  }

  @Test
  void writeArrayValue() throws Exception {
    assertThat(singleValueFromWritten(Map.of("v", new int[] {1, 2}))).isEqualTo(List.of(1, 2));
  }

  @Test
  void writeNestedObject() throws Exception {
    Map<String, Object> inner = new LinkedHashMap<>();
    inner.put("nested", "value");
    assertThat(JsonSupport.parseObject(JsonSupport.writeString(Map.of("outer", inner))))
        .containsEntry("outer", Map.of("nested", "value"));
  }

  @Test
  void writePrettyStringProducesPrettyOutput() throws Exception {
    String json = JsonSupport.writePrettyString(Map.of("a", 1));
    assertThat(json).contains("\n"); // pretty-printed has newlines
    assertThat(JsonSupport.parseObject(json)).containsEntry("a", 1);
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
    assertThat(JsonSupport.parseObject(json)).containsEntry("v", "custom-object");
  }

  @Test
  void parseObjectHandlesTrailingContent() {
    assertThatThrownBy(() -> JsonSupport.parseObject("{\"a\":1} extra"))
        .isInstanceOf(IOException.class)
        .hasMessageMatching("(?s).*(trailing content|Unrecognized token).*");
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

  private static Object singleValueFromWritten(Map<String, Object> value) throws Exception {
    return JsonSupport.parseObject(JsonSupport.writeString(value)).get("v");
  }
}
