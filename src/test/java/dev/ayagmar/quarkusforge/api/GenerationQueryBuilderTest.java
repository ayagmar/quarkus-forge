package dev.ayagmar.quarkusforge.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class GenerationQueryBuilderTest {
  @Test
  void buildEncodesExpectedQueryParameters() {
    GenerationRequest request =
        new GenerationRequest(
            "com.example",
            "demo app",
            "1.0.0-SNAPSHOT",
            "maven",
            "25",
            List.of("io.quarkus:quarkus-rest", "io.quarkus:quarkus-jdbc-postgresql"));

    URI uri = GenerationQueryBuilder.build(URI.create("https://code.quarkus.io"), request);

    assertThat(uri.toString())
        .isEqualTo(
            "https://code.quarkus.io/api/download"
                + "?g=com.example"
                + "&a=demo+app"
                + "&v=1.0.0-SNAPSHOT"
                + "&b=maven"
                + "&j=25"
                + "&e=io.quarkus%3Aquarkus-rest%2Cio.quarkus%3Aquarkus-jdbc-postgresql");
  }
}
