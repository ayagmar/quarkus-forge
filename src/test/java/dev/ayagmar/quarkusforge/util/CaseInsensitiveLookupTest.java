package dev.ayagmar.quarkusforge.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CaseInsensitiveLookupTest {
  @Test
  void containsMatchesIgnoringCase() {
    boolean result = CaseInsensitiveLookup.contains(List.of("Maven", "Gradle"), "maven");

    assertThat(result).isTrue();
  }

  @Test
  void findReturnsValueIgnoringCase() {
    Map<String, List<String>> compatibility = Map.of("MAVEN", List.of("21", "25"));

    List<String> javaVersions = CaseInsensitiveLookup.find(compatibility, "maven");

    assertThat(javaVersions).containsExactly("21", "25");
  }

  @Test
  void findReturnsNullWhenKeyDoesNotExist() {
    Map<String, String> values = Map.of("maven", "yes");

    String match = CaseInsensitiveLookup.find(values, "gradle");

    assertThat(match).isNull();
  }

  @Test
  void lookupTrimsBuildToolNames() {
    Map<String, List<String>> compatibility = Map.of(" maven ", List.of("21", "25"));

    List<String> javaVersions = CaseInsensitiveLookup.find(compatibility, "maven");

    assertThat(javaVersions).containsExactly("21", "25");
    assertThat(CaseInsensitiveLookup.contains(List.of(" Maven "), "maven")).isTrue();
  }
}
