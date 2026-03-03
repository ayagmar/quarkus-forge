package dev.ayagmar.quarkusforge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JsonFieldReaderTest {

  // ── readString ──────────────────────────────────────────────────────

  @Test
  void readStringReturnsNullForMissingField() {
    assertThat(JsonFieldReader.readString(Map.of(), "missing")).isNull();
  }

  @Test
  void readStringReturnsValue() {
    assertThat(JsonFieldReader.readString(Map.of("k", "v"), "k")).isEqualTo("v");
  }

  @Test
  void readStringThrowsForNonStringValue() {
    assertThatThrownBy(() -> JsonFieldReader.readString(Map.of("k", 42), "k"))
        .isInstanceOf(ApiContractException.class);
  }

  // ── readStringOrEmpty ──────────────────────────────────────────────

  @Test
  void readStringOrEmptyReturnsEmptyForMissingField() {
    assertThat(JsonFieldReader.readStringOrEmpty(Map.of(), "missing")).isEmpty();
  }

  // ── readInt ──────────────────────────────────────────────────────

  @Test
  void readIntReturnsNullForMissingField() {
    assertThat(JsonFieldReader.readInt(Map.of(), "missing")).isNull();
  }

  @Test
  void readIntReturnsIntValue() {
    assertThat(JsonFieldReader.readInt(Map.of("count", 5), "count")).isEqualTo(5);
  }

  @Test
  void readIntThrowsForNonNumber() {
    assertThatThrownBy(() -> JsonFieldReader.readInt(Map.of("k", "text"), "k"))
        .isInstanceOf(ApiContractException.class);
  }

  // ── readLong ──────────────────────────────────────────────────────

  @Test
  void readLongReturnsNullForMissingField() {
    assertThat(JsonFieldReader.readLong(Map.of(), "missing")).isNull();
  }

  @Test
  void readLongReturnsLongValue() {
    assertThat(JsonFieldReader.readLong(Map.of("size", 100L), "size")).isEqualTo(100L);
  }

  @Test
  void readLongThrowsForNonNumber() {
    assertThatThrownBy(() -> JsonFieldReader.readLong(Map.of("k", "text"), "k"))
        .isInstanceOf(ApiContractException.class);
  }

  @Test
  void readLongThrowsForNonExactValue() {
    assertThatThrownBy(() -> JsonFieldReader.readLong(Map.of("k", 1.5), "k"))
        .isInstanceOf(ApiContractException.class);
  }

  // ── readStringList ──────────────────────────────────────────────

  @Test
  void readStringListReturnsNullForMissing() {
    assertThat(JsonFieldReader.readStringList(Map.of(), "missing")).isNull();
  }

  @Test
  void readStringListReturnsValues() {
    assertThat(JsonFieldReader.readStringList(Map.of("items", List.of("a", "b")), "items"))
        .containsExactly("a", "b");
  }

  @Test
  void readStringListThrowsForNonList() {
    assertThatThrownBy(() -> JsonFieldReader.readStringList(Map.of("k", "text"), "k"))
        .isInstanceOf(ApiContractException.class);
  }

  @Test
  void readStringListThrowsForNonStringElements() {
    assertThatThrownBy(() -> JsonFieldReader.readStringList(Map.of("k", List.of(1, 2)), "k"))
        .isInstanceOf(ApiContractException.class);
  }

  // ── readStringListOrEmpty ──────────────────────────────────────────

  @Test
  void readStringListOrEmptyReturnsEmptyForMissing() {
    assertThat(JsonFieldReader.readStringListOrEmpty(Map.of(), "missing")).isEmpty();
  }

  // ── readStringSet ──────────────────────────────────────────────

  @Test
  void readStringSetReturnsNullForMissing() {
    assertThat(JsonFieldReader.readStringSet(Map.of(), "missing")).isNull();
  }

  @Test
  void readStringSetReturnsDeduplicatedValues() {
    Set<String> result = JsonFieldReader.readStringSet(Map.of("s", List.of("a", "b", "a")), "s");
    assertThat(result).containsExactly("a", "b");
  }

  // ── readObject ──────────────────────────────────────────────────

  @Test
  void readObjectReturnsNullForMissing() {
    assertThat(JsonFieldReader.readObject(Map.of(), "missing")).isNull();
  }

  @Test
  void readObjectReturnsNestedMap() {
    Map<String, Object> nested = new LinkedHashMap<>();
    nested.put("inner", "value");
    Map<String, Object> result = JsonFieldReader.readObject(Map.of("obj", nested), "obj");
    assertThat(result).containsEntry("inner", "value");
  }

  @Test
  void readObjectThrowsForNonMap() {
    assertThatThrownBy(() -> JsonFieldReader.readObject(Map.of("k", "text"), "k"))
        .isInstanceOf(ApiContractException.class);
  }

  @Test
  void readObjectThrowsForNonStringKeys() {
    Map<Object, Object> badKeys = new LinkedHashMap<>();
    badKeys.put(123, "value");
    Map<String, Object> source = new LinkedHashMap<>();
    source.put("obj", badKeys);
    assertThatThrownBy(() -> JsonFieldReader.readObject(source, "obj"))
        .isInstanceOf(ApiContractException.class);
  }

  // ── readArray ──────────────────────────────────────────────────

  @Test
  void readArrayReturnsNullForMissing() {
    assertThat(JsonFieldReader.readArray(Map.of(), "missing")).isNull();
  }

  @Test
  void readArrayReturnsValues() {
    assertThat(JsonFieldReader.readArray(Map.of("items", List.of(1, "two", true)), "items"))
        .containsExactly(1, "two", true);
  }

  @Test
  void readArrayThrowsForNonList() {
    assertThatThrownBy(() -> JsonFieldReader.readArray(Map.of("k", "text"), "k"))
        .isInstanceOf(ApiContractException.class);
  }

  // ── readBoolean ──────────────────────────────────────────────────

  @Test
  void readBooleanReturnsNullForMissing() {
    assertThat(JsonFieldReader.readBoolean(Map.of(), "missing")).isNull();
  }

  @Test
  void readBooleanReturnsTrueValue() {
    assertThat(JsonFieldReader.readBoolean(Map.of("flag", true), "flag")).isTrue();
  }

  @Test
  void readBooleanReturnsFalseValue() {
    assertThat(JsonFieldReader.readBoolean(Map.of("flag", false), "flag")).isFalse();
  }

  @Test
  void readBooleanThrowsForNonBoolean() {
    assertThatThrownBy(() -> JsonFieldReader.readBoolean(Map.of("k", "true"), "k"))
        .isInstanceOf(ApiContractException.class);
  }

  // ── toExactInt edge case ──────────────────────────────────────────

  @Test
  void readIntThrowsForNonExactDecimal() {
    assertThatThrownBy(() -> JsonFieldReader.readInt(Map.of("k", 1.5), "k"))
        .isInstanceOf(ApiContractException.class);
  }
}
