package dev.ayagmar.quarkusforge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class QuarkusApiContractDriftTest {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void contractSnapshotStillMatchesRequiredFields() throws IOException {
    JsonNode snapshot =
        objectMapper.readTree(
            getClass().getClassLoader().getResourceAsStream("contracts/quarkus-api-snapshot.json"));

    var extensions =
        QuarkusApiClient.parseExtensionsPayload(
            snapshot.get("extensions").toString(), objectMapper);
    MetadataDto metadata =
        QuarkusApiClient.parseMetadataPayload(snapshot.get("metadata").toString(), objectMapper);

    assertThat(extensions).isNotEmpty();
    assertThat(metadata.javaVersions()).contains("25");
    assertThat(metadata.buildTools()).contains("maven");
  }

  @Test
  void contractDriftIsDetectedWhenRequiredFieldsDisappear() {
    String driftedMetadataPayload = "{\"javaVersions\":[\"25\"]}";

    assertThatThrownBy(
            () -> QuarkusApiClient.parseMetadataPayload(driftedMetadataPayload, objectMapper))
        .isInstanceOf(ApiContractException.class)
        .hasMessage("Metadata payload is missing 'buildTools' array");
  }
}
