package dev.ayagmar.quarkusforge.api;

import static org.assertj.core.api.Assertions.assertThat;

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
}
