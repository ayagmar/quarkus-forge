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
                + "&b=MAVEN"
                + "&j=25"
                + "&e=io.quarkus%3Aquarkus-rest%2Cio.quarkus%3Aquarkus-jdbc-postgresql");
  }

  @Test
  void buildEncodesGradleKotlinDslWithApiContractValue() {
    GenerationRequest request =
        new GenerationRequest(
            "com.example", "demo", "1.0.0-SNAPSHOT", "gradle-kotlin-dsl", "25", List.of());

    URI uri = GenerationQueryBuilder.build(URI.create("https://code.quarkus.io"), request);

    assertThat(uri.toString()).contains("&b=GRADLE_KOTLIN_DSL&");
  }

  @Test
  void buildEncodesPlatformStreamWhenProvided() {
    GenerationRequest request =
        new GenerationRequest(
            "com.example",
            "demo",
            "1.0.0-SNAPSHOT",
            "io.quarkus.platform:3.31",
            "maven",
            "25",
            List.of());

    URI uri = GenerationQueryBuilder.build(URI.create("https://code.quarkus.io"), request);

    assertThat(uri.toString()).contains("S=io.quarkus.platform%3A3.31");
  }
}
