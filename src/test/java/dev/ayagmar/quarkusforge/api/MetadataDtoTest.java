package dev.ayagmar.quarkusforge.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MetadataDtoTest {
  @Test
  void normalizesCompatibilityKeysCaseInsensitively() {
    MetadataDto metadata =
        new MetadataDto(
            List.of("17", "21", "25"),
            List.of("maven"),
            Map.of("MAVEN", List.of("17", "21", "25")));

    assertThat(metadata.compatibility().containsKey("maven")).isTrue();
    assertThat(metadata.compatibility().get("maven")).containsExactly("17", "21", "25");
  }
}
