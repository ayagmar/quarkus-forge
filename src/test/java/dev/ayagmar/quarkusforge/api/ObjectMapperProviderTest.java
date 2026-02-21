package dev.ayagmar.quarkusforge.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

class ObjectMapperProviderTest {
  @Test
  void sharedReturnsSingletonMapper() {
    assertThat(ObjectMapperProvider.shared()).isSameAs(ObjectMapperProvider.shared());
  }

  @Test
  void sharedMapperCanParseJsonPayload() throws Exception {
    JsonNode node = ObjectMapperProvider.shared().readTree("{\"key\":\"value\"}");

    assertThat(node.path("key").asText()).isEqualTo("value");
  }
}
