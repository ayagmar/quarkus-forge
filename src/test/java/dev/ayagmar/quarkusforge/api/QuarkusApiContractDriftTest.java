package dev.ayagmar.quarkusforge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuarkusApiContractDriftTest {
  @Test
  void contractSnapshotStillMatchesRequiredFields() throws IOException {
    Map<String, Object> snapshot;
    try (InputStream snapshotInputStream =
        getClass().getClassLoader().getResourceAsStream("contracts/quarkus-api-snapshot.json")) {
      assertThat(snapshotInputStream).isNotNull();
      snapshot =
          JsonSupport.parseObject(
              new String(snapshotInputStream.readAllBytes(), StandardCharsets.UTF_8));
    }

    var extensions = QuarkusApiClient.parseExtensionsArray(asArray(snapshot.get("extensions")));
    MetadataDto metadata = QuarkusApiClient.parseMetadataObject(asObject(snapshot.get("metadata")));

    assertThat(extensions).isNotEmpty();
    assertThat(metadata.javaVersions()).contains("25");
    assertThat(metadata.buildTools()).contains("maven");
    assertThat(metadata.compatibility()).containsKey("gradle");
    assertThat(metadata.recommendedPlatformStreamKey()).isEqualTo("io.quarkus.platform:3.31");
  }

  @Test
  void contractDriftIsDetectedWhenRequiredFieldsDisappear() {
    String driftedMetadataPayload = "{\"javaVersions\":[\"25\"]}";

    assertThatThrownBy(() -> QuarkusApiClient.parseMetadataPayload(driftedMetadataPayload))
        .isInstanceOf(ApiContractException.class)
        .hasMessage("Metadata payload is missing 'buildTools' array");
  }

  @Test
  void missingCompatibilityMatrixParsesAsEmptyCompatibilityMap() {
    String metadataWithoutCompatibility = "{\"javaVersions\":[\"25\"],\"buildTools\":[\"maven\"]}";

    MetadataDto metadata = QuarkusApiClient.parseMetadataPayload(metadataWithoutCompatibility);

    assertThat(metadata.compatibility()).isEmpty();
  }

  @Test
  void compatibilityLookupIsCaseInsensitiveDuringParsing() {
    String mixedCaseMetadataPayload =
        """
        {
          "javaVersions": ["21", "25"],
          "buildTools": ["Maven", "Gradle"],
          "compatibility": {
            "maven": ["21", "25"],
            "GRADLE": ["25"]
          }
        }
        """;

    MetadataDto metadata = QuarkusApiClient.parseMetadataPayload(mixedCaseMetadataPayload);

    assertThat(metadata.compatibility()).containsEntry("maven", List.of("21", "25"));
    assertThat(metadata.compatibility()).containsEntry("gradle", List.of("25"));
  }

  @Test
  void compatibilityCaseCollisionsAreRejectedDuringParsing() {
    String duplicateCaseCompatibilityPayload =
        """
        {
          "javaVersions": ["21", "25"],
          "buildTools": ["maven"],
          "compatibility": {
            "maven": ["25"],
            "MAVEN": ["21"]
          }
        }
        """;

    assertThatThrownBy(
            () -> QuarkusApiClient.parseMetadataPayload(duplicateCaseCompatibilityPayload))
        .isInstanceOf(ApiContractException.class)
        .hasMessageContaining("differing only by case");
  }

  @Test
  void streamsPayloadParsesJavaVersionsFromJavaCompatibility() {
    String streamsPayload =
        """
        [
          {
            "key":"io.quarkus.platform:3.31",
            "javaCompatibility":{"versions":[17,21,25],"recommended":25}
          },
          {
            "key":"io.quarkus.platform:3.20",
            "javaCompatibility":{"versions":[17,21],"recommended":21}
          }
        ]
        """;

    StreamsMetadata streamsMetadata = QuarkusApiClient.parseStreamsMetadataPayload(streamsPayload);

    assertThat(streamsMetadata.javaVersions()).containsExactly("17", "21", "25");
  }

  @Test
  void streamsPayloadParsesPlatformStreamMetadata() {
    String streamsPayload =
        """
        [
          {
            "key":"io.quarkus.platform:3.31",
            "platformVersion":"3.31",
            "recommended":true,
            "javaCompatibility":{"versions":[17,21,25],"recommended":25}
          }
        ]
        """;

    StreamsMetadata streamsMetadata = QuarkusApiClient.parseStreamsMetadataPayload(streamsPayload);

    assertThat(streamsMetadata.platformStreams())
        .containsExactly(
            new PlatformStream(
                "io.quarkus.platform:3.31", "3.31", true, List.of("17", "21", "25")));
  }

  @Test
  void streamsPayloadWithoutJavaCompatibilityVersionsIsRejected() {
    String driftedStreamsPayload =
        """
        [
          {
            "key":"io.quarkus.platform:3.31",
            "javaCompatibility":{"recommended":25}
          }
        ]
        """;

    assertThatThrownBy(() -> QuarkusApiClient.parseStreamsMetadataPayload(driftedStreamsPayload))
        .isInstanceOf(ApiContractException.class)
        .hasMessageContaining("javaCompatibility.versions");
  }

  @Test
  void openApiPayloadParsesBuildToolEnumForDownloadEndpoint() {
    String openApiPayload =
        """
        {
          "paths": {
            "/api/download": {
              "get": {
                "parameters": [
                  {"name":"g","schema":{"type":"string"}},
                  {"name":"b","schema":{"enum":["MAVEN","GRADLE","GRADLE_KOTLIN_DSL"]}}
                ]
              }
            }
          }
        }
        """;

    List<String> buildTools = QuarkusApiClient.parseBuildToolsFromOpenApiPayload(openApiPayload);

    assertThat(buildTools).containsExactly("maven", "gradle", "gradle-kotlin-dsl");
  }

  @Test
  void openApiPayloadWithoutBuildToolEnumIsRejected() {
    String driftedOpenApiPayload =
        """
        {
          "paths": {
            "/api/download": {
              "get": {
                "parameters": [
                  {"name":"g","schema":{"type":"string"}}
                ]
              }
            }
          }
        }
        """;

    assertThatThrownBy(
            () -> QuarkusApiClient.parseBuildToolsFromOpenApiPayload(driftedOpenApiPayload))
        .isInstanceOf(ApiContractException.class)
        .hasMessageContaining("build tool enum");
  }

  @Test
  void docsOpenApiSnapshotStillProvidesDownloadBuildToolEnum() throws IOException {
    String openApiPayload;
    try (InputStream inputStream =
        getClass().getClassLoader().getResourceAsStream("contracts/openapi.json")) {
      assertThat(inputStream).isNotNull();
      openApiPayload = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    List<String> buildTools = QuarkusApiClient.parseBuildToolsFromOpenApiPayload(openApiPayload);

    assertThat(buildTools).containsExactly("maven", "gradle", "gradle-kotlin-dsl");
  }

  private static Map<String, Object> asObject(Object value) {
    if (!(value instanceof Map<?, ?> rawObject)) {
      throw new ApiContractException("Malformed JSON payload");
    }
    Map<String, Object> object = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : rawObject.entrySet()) {
      if (!(entry.getKey() instanceof String key)) {
        throw new ApiContractException("Malformed JSON payload");
      }
      object.put(key, entry.getValue());
    }
    return object;
  }

  private static List<Object> asArray(Object value) {
    if (!(value instanceof List<?> rawArray)) {
      throw new ApiContractException("Malformed JSON payload");
    }
    return new ArrayList<>(rawArray);
  }
}
