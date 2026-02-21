package dev.ayagmar.quarkusforge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuarkusApiContractDriftTest {
  private final ObjectMapper objectMapper = ObjectMapperProvider.shared();

  @Test
  void contractSnapshotStillMatchesRequiredFields() throws IOException {
    JsonNode snapshot;
    try (InputStream snapshotInputStream =
        getClass().getClassLoader().getResourceAsStream("contracts/quarkus-api-snapshot.json")) {
      assertThat(snapshotInputStream).isNotNull();
      snapshot = objectMapper.readTree(snapshotInputStream);
    }

    var extensions =
        QuarkusApiClient.parseExtensionsPayload(
            snapshot.get("extensions").toString(), objectMapper);
    MetadataDto metadata =
        QuarkusApiClient.parseMetadataPayload(snapshot.get("metadata").toString(), objectMapper);

    assertThat(extensions).isNotEmpty();
    assertThat(metadata.javaVersions()).contains("25");
    assertThat(metadata.buildTools()).contains("maven");
    assertThat(metadata.compatibility()).containsKey("gradle");
  }

  @Test
  void contractDriftIsDetectedWhenRequiredFieldsDisappear() {
    String driftedMetadataPayload = "{\"javaVersions\":[\"25\"]}";

    assertThatThrownBy(
            () -> QuarkusApiClient.parseMetadataPayload(driftedMetadataPayload, objectMapper))
        .isInstanceOf(ApiContractException.class)
        .hasMessage("Metadata payload is missing 'buildTools' array");
  }

  @Test
  void missingCompatibilityMatrixParsesAsEmptyCompatibilityMap() {
    String metadataWithoutCompatibility = "{\"javaVersions\":[\"25\"],\"buildTools\":[\"maven\"]}";

    MetadataDto metadata =
        QuarkusApiClient.parseMetadataPayload(metadataWithoutCompatibility, objectMapper);

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

    MetadataDto metadata =
        QuarkusApiClient.parseMetadataPayload(mixedCaseMetadataPayload, objectMapper);

    assertThat(metadata.compatibility()).containsEntry("Maven", List.of("21", "25"));
    assertThat(metadata.compatibility()).containsEntry("Gradle", List.of("25"));
  }
}
