package dev.ayagmar.quarkusforge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ApiPayloadParserTest {

  // ── parseMetadataObject ──────────────────────────────────────────────

  @Nested
  class MetadataParsingTests {
    @Test
    void rejectsMissingJavaVersions() {
      String payload =
          """
          {"buildTools": ["maven"], "compatibility": {"maven": ["25"]}}
          """;

      assertThatThrownBy(() -> ApiPayloadParser.parseMetadataPayload(payload))
          .isInstanceOf(ApiContractException.class)
          .hasMessageContaining("javaVersions");
    }

    @Test
    void rejectsMissingBuildTools() {
      String payload =
          """
          {"javaVersions": ["25"], "compatibility": {"maven": ["25"]}}
          """;

      assertThatThrownBy(() -> ApiPayloadParser.parseMetadataPayload(payload))
          .isInstanceOf(ApiContractException.class)
          .hasMessageContaining("buildTools");
    }

    @Test
    void parsesEmptyCompatibilityAsEmptyMap() {
      String payload =
          """
          {"javaVersions": ["25"], "buildTools": []}
          """;

      MetadataDto metadata = ApiPayloadParser.parseMetadataPayload(payload);
      assertThat(metadata.compatibility()).isEmpty();
    }

    @Test
    void parsesNullPlatformStreamsAsEmptyList() {
      String payload =
          """
          {"javaVersions": ["25"], "buildTools": ["maven"], "compatibility": {"maven": ["25"]}}
          """;

      MetadataDto metadata = ApiPayloadParser.parseMetadataPayload(payload);
      assertThat(metadata.platformStreams()).isEmpty();
    }

    @Test
    void parsesPlatformStreamRecommendedFlag() {
      String payload =
          """
          {
            "javaVersions": ["25"],
            "buildTools": ["maven"],
            "compatibility": {"maven": ["25"]},
            "platformStreams": [
              {"key": "io.quarkus.platform:3.18", "platformVersion": "3.18.0", "recommended": true, "javaVersions": ["25"]}
            ]
          }
          """;

      MetadataDto metadata = ApiPayloadParser.parseMetadataPayload(payload);
      assertThat(metadata.platformStreams()).hasSize(1);
      assertThat(metadata.platformStreams().getFirst().recommended()).isTrue();
    }
  }

  // ── parseExtensionsArray ─────────────────────────────────────────────

  @Nested
  class ExtensionParsingTests {
    @Test
    void parsesExtensionWithAllFields() {
      String payload =
          """
          [{"id": "io.quarkus:quarkus-rest", "name": "REST", "shortName": "rest", "category": "Web", "order": 10, "description": "REST endpoint support"}]
          """;

      List<ExtensionDto> extensions = ApiPayloadParser.parseExtensionsPayload(payload);
      assertThat(extensions).hasSize(1);
      assertThat(extensions.getFirst().id()).isEqualTo("io.quarkus:quarkus-rest");
      assertThat(extensions.getFirst().shortName()).isEqualTo("rest");
      assertThat(extensions.getFirst().category()).isEqualTo("Web");
      assertThat(extensions.getFirst().order()).isEqualTo(10);
      assertThat(extensions.getFirst().description()).isEqualTo("REST endpoint support");
    }

    @Test
    void extensionWithNullShortNameFallsBackToName() {
      String payload =
          """
          [{"id": "io.quarkus:quarkus-rest", "name": "REST"}]
          """;

      List<ExtensionDto> extensions = ApiPayloadParser.parseExtensionsPayload(payload);
      assertThat(extensions.getFirst().shortName()).isEqualTo("REST");
    }

    @Test
    void extensionWithBlankShortNameFallsBackToName() {
      String payload =
          """
          [{"id": "io.quarkus:quarkus-rest", "name": "REST", "shortName": "  "}]
          """;

      List<ExtensionDto> extensions = ApiPayloadParser.parseExtensionsPayload(payload);
      assertThat(extensions.getFirst().shortName()).isEqualTo("REST");
    }

    @Test
    void extensionWithNullCategoryDefaultsToOther() {
      String payload =
          """
          [{"id": "io.quarkus:quarkus-rest", "name": "REST"}]
          """;

      List<ExtensionDto> extensions = ApiPayloadParser.parseExtensionsPayload(payload);
      assertThat(extensions.getFirst().category()).isEqualTo("Other");
    }

    @Test
    void extensionWithBlankCategoryDefaultsToOther() {
      String payload =
          """
          [{"id": "io.quarkus:quarkus-rest", "name": "REST", "category": "  "}]
          """;

      List<ExtensionDto> extensions = ApiPayloadParser.parseExtensionsPayload(payload);
      assertThat(extensions.getFirst().category()).isEqualTo("Other");
    }

    @Test
    void extensionWithNullDescriptionDefaultsToEmpty() {
      String payload =
          """
          [{"id": "io.quarkus:quarkus-rest", "name": "REST"}]
          """;

      List<ExtensionDto> extensions = ApiPayloadParser.parseExtensionsPayload(payload);
      assertThat(extensions.getFirst().description()).isEmpty();
    }

    @Test
    void extensionWithMissingIdIsRejected() {
      String payload =
          """
          [{"name": "REST"}]
          """;

      assertThatThrownBy(() -> ApiPayloadParser.parseExtensionsPayload(payload))
          .isInstanceOf(ApiContractException.class)
          .hasMessageContaining("id");
    }

    @Test
    void extensionWithMissingNameIsRejected() {
      String payload =
          """
          [{"id": "io.quarkus:quarkus-rest"}]
          """;

      assertThatThrownBy(() -> ApiPayloadParser.parseExtensionsPayload(payload))
          .isInstanceOf(ApiContractException.class)
          .hasMessageContaining("name");
    }

    @Test
    void extensionEntryMustBeObject() {
      String payload = "[42]";

      assertThatThrownBy(() -> ApiPayloadParser.parseExtensionsPayload(payload))
          .isInstanceOf(ApiContractException.class)
          .hasMessageContaining("objects");
    }
  }

  // ── parsePresetsPayload ──────────────────────────────────────────────

  @Nested
  class PresetParsingTests {
    @Test
    void parsesSimplePreset() {
      String payload =
          """
          [{"key": "web", "extensions": ["io.quarkus:quarkus-rest"]}]
          """;

      Map<String, List<String>> presets = ApiPayloadParser.parsePresetsPayload(payload);
      assertThat(presets).containsKey("web");
      assertThat(presets.get("web")).containsExactly("io.quarkus:quarkus-rest");
    }

    @Test
    void presetKeyIsNormalized() {
      String payload =
          """
          [{"key": "  Web  ", "extensions": ["id"]}]
          """;

      Map<String, List<String>> presets = ApiPayloadParser.parsePresetsPayload(payload);
      assertThat(presets).containsKey("web");
    }

    @Test
    void rejectsBlankPresetKey() {
      String payload =
          """
          [{"key": "  ", "extensions": ["id"]}]
          """;

      assertThatThrownBy(() -> ApiPayloadParser.parsePresetsPayload(payload))
          .isInstanceOf(ApiContractException.class)
          .hasMessageContaining("blank");
    }

    @Test
    void rejectsDuplicatePresetKeys() {
      String payload =
          """
          [{"key": "web", "extensions": ["a"]}, {"key": "Web", "extensions": ["b"]}]
          """;

      assertThatThrownBy(() -> ApiPayloadParser.parsePresetsPayload(payload))
          .isInstanceOf(ApiContractException.class)
          .hasMessageContaining("duplicate");
    }
  }

  // ── Shared helpers ───────────────────────────────────────────────────

  @Nested
  class SharedHelperTests {
    @Test
    void parseObjectPayloadRejectsMalformedJson() {
      assertThatThrownBy(() -> ApiPayloadParser.parseObjectPayload("not json"))
          .isInstanceOf(ApiContractException.class)
          .hasMessageContaining("Malformed");
    }

    @Test
    void parseArrayPayloadRejectsMalformedJson() {
      assertThatThrownBy(() -> ApiPayloadParser.parseArrayPayload("not json"))
          .isInstanceOf(ApiContractException.class)
          .hasMessageContaining("Malformed");
    }

    @Test
    void copyStringListRejectsNonStringElements() {
      assertThatThrownBy(() -> ApiPayloadParser.copyStringList(List.of(42), "field"))
          .isInstanceOf(ApiContractException.class)
          .hasMessageContaining("strings");
    }

    @Test
    void castObjectRejectsNonMapValue() {
      assertThatThrownBy(() -> ApiPayloadParser.castObject("not a map", "error"))
          .isInstanceOf(ApiContractException.class)
          .hasMessageContaining("error");
    }

    @Test
    void castArrayRejectsNonListValue() {
      assertThatThrownBy(() -> ApiPayloadParser.castArray("not a list", "error"))
          .isInstanceOf(ApiContractException.class)
          .hasMessageContaining("error");
    }

    @Test
    void toIntegerReturnsNullForNull() {
      assertThat(ApiPayloadParser.toInteger(null)).isNull();
    }

    @Test
    void toIntegerParsesValidNumber() {
      assertThat(ApiPayloadParser.toInteger(42)).isEqualTo(42);
    }

    @Test
    void toIntegerRejectsNonNumber() {
      assertThatThrownBy(() -> ApiPayloadParser.toInteger("not a number"))
          .isInstanceOf(ApiContractException.class);
    }

    @Test
    void toIntegerRejectsDecimalFraction() {
      assertThatThrownBy(() -> ApiPayloadParser.toInteger(3.14))
          .isInstanceOf(ApiContractException.class);
    }
  }

  // ── Streams metadata ──────────────────────────────────────────────────

  @Nested
  class StreamsParsingTests {
    @Test
    void rejectsMissingJavaCompatibility() {
      String payload = "[{\"key\": \"io.quarkus.platform:3.18\"}]";

      assertThatThrownBy(() -> ApiPayloadParser.parseStreamsMetadataPayload(payload))
          .isInstanceOf(ApiContractException.class)
          .hasMessageContaining("javaCompatibility");
    }

    @Test
    void rejectsMissingVersionsInJavaCompatibility() {
      String payload =
          """
          [{"key": "io.quarkus.platform:3.18", "javaCompatibility": {}}]
          """;

      assertThatThrownBy(() -> ApiPayloadParser.parseStreamsMetadataPayload(payload))
          .isInstanceOf(ApiContractException.class)
          .hasMessageContaining("versions");
    }

    @Test
    void rejectsNonIntegerJavaVersions() {
      String payload =
          """
          [{"key": "io.quarkus.platform:3.18", "javaCompatibility": {"versions": ["abc"]}}]
          """;

      assertThatThrownBy(() -> ApiPayloadParser.parseStreamsMetadataPayload(payload))
          .isInstanceOf(ApiContractException.class)
          .hasMessageContaining("Malformed");
    }

    @Test
    void rejectsStreamWithOnlyNonPositiveVersions() {
      String payload =
          """
          [{"key": "io.quarkus.platform:3.18", "javaCompatibility": {"versions": [0, -1]}}]
          """;

      assertThatThrownBy(() -> ApiPayloadParser.parseStreamsMetadataPayload(payload))
          .isInstanceOf(ApiContractException.class)
          .hasMessageContaining("positive");
    }

    @Test
    void rejectsStreamEntryThatIsNotObject() {
      String payload = "[42]";

      assertThatThrownBy(() -> ApiPayloadParser.parseStreamsMetadataPayload(payload))
          .isInstanceOf(ApiContractException.class)
          .hasMessageContaining("objects");
    }

    @Test
    void parsesRecommendedFlagInStream() {
      String payload =
          """
          [{"key": "io.quarkus:3.18", "recommended": true, "javaCompatibility": {"versions": [25]}}]
          """;

      StreamsMetadata result = ApiPayloadParser.parseStreamsMetadataPayload(payload);
      assertThat(result.platformStreams().getFirst().recommended()).isTrue();
    }

    @Test
    void filtersOutNonPositiveVersions() {
      String payload =
          """
          [{"key": "io.quarkus:3.18", "javaCompatibility": {"versions": [0, 21, 25]}}]
          """;

      StreamsMetadata result = ApiPayloadParser.parseStreamsMetadataPayload(payload);
      assertThat(result.javaVersions()).containsExactly("21", "25");
    }
  }

  // ── OpenAPI build tools ──────────────────────────────────────────────

  @Nested
  class OpenApiParsingTests {
    @Test
    void rejectsMissingParametersArray() {
      String payload =
          """
          {"paths": {"/api/download": {"get": {}}}}
          """;

      assertThatThrownBy(() -> ApiPayloadParser.parseBuildToolsFromOpenApiPayload(payload))
          .isInstanceOf(ApiContractException.class)
          .hasMessageContaining("parameters");
    }

    @Test
    void rejectsEmptyBuildToolEnum() {
      String payload =
          """
          {"paths": {"/api/download": {"get": {"parameters": [{"name": "b", "schema": {"enum": []}}]}}}}
          """;

      assertThatThrownBy(() -> ApiPayloadParser.parseBuildToolsFromOpenApiPayload(payload))
          .isInstanceOf(ApiContractException.class)
          .hasMessageContaining("did not provide");
    }

    @Test
    void rejectsMissingSchemaEnum() {
      String payload =
          """
          {"paths": {"/api/download": {"get": {"parameters": [{"name": "b", "schema": {}}]}}}}
          """;

      assertThatThrownBy(() -> ApiPayloadParser.parseBuildToolsFromOpenApiPayload(payload))
          .isInstanceOf(ApiContractException.class)
          .hasMessageContaining("enum");
    }

    @Test
    void skipsNonBuildToolParameters() {
      String payload =
          """
          {"paths": {"/api/download": {"get": {"parameters": [
            {"name": "g", "schema": {"type": "string"}},
            {"name": "b", "schema": {"enum": ["MAVEN"]}}
          ]}}}}
          """;

      List<String> buildTools = ApiPayloadParser.parseBuildToolsFromOpenApiPayload(payload);
      assertThat(buildTools).containsExactly("maven");
    }

    @Test
    void skipsParametersWithNoEnum() {
      String payload =
          """
          {"paths": {"/api/download": {"get": {"parameters": [
            {"name": "a"},
            {"name": "b", "schema": {"enum": ["MAVEN"]}}
          ]}}}}
          """;

      List<String> buildTools = ApiPayloadParser.parseBuildToolsFromOpenApiPayload(payload);
      assertThat(buildTools).containsExactly("maven");
    }
  }
}
