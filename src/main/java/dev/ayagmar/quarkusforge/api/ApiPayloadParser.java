package dev.ayagmar.quarkusforge.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Stateless codec for parsing Quarkus API JSON payloads into domain DTOs. Extracted from
 * QuarkusApiClient to separate HTTP transport from payload deserialization (SRP).
 */
final class ApiPayloadParser {
  private ApiPayloadParser() {}

  // ── Metadata ──────────────────────────────────────────────────────────

  public static MetadataDto parseMetadataPayload(String payload) {
    return parseMetadataObject(parseObjectPayload(payload));
  }

  public static MetadataDto parseMetadataObject(Map<String, Object> metadataPayload) {
    List<Object> javaVersionsNode = readArray(metadataPayload, "javaVersions");
    if (javaVersionsNode == null) {
      throw new ApiContractException("Metadata payload is missing 'javaVersions' array");
    }

    List<Object> buildToolsNode = readArray(metadataPayload, "buildTools");
    if (buildToolsNode == null) {
      throw new ApiContractException("Metadata payload is missing 'buildTools' array");
    }

    List<String> javaVersions = copyStringList(javaVersionsNode, "javaVersions");
    List<String> buildTools = copyStringList(buildToolsNode, "buildTools");
    Map<String, List<String>> compatibility =
        parseCompatibility(readObject(metadataPayload, "compatibility"), buildTools);
    List<PlatformStream> platformStreams =
        parsePlatformStreams(readArray(metadataPayload, "platformStreams"));
    return new MetadataDto(javaVersions, buildTools, compatibility, platformStreams);
  }

  // ── Streams ───────────────────────────────────────────────────────────

  public static StreamsMetadata parseStreamsMetadataPayload(String payload) {
    List<Object> streamPayloads = parseArrayPayload(payload);

    Set<Integer> versions = new LinkedHashSet<>();
    List<PlatformStream> platformStreams = new ArrayList<>();
    for (Object streamNode : streamPayloads) {
      Map<String, Object> stream = castObject(streamNode, "Stream payload entries must be objects");
      String key = requiredText(readText(stream, "key"), "key");
      Map<String, Object> javaCompatibility = readObject(stream, "javaCompatibility");
      if (javaCompatibility == null) {
        throw new ApiContractException("Stream payload is missing 'javaCompatibility' object");
      }
      List<Object> javaVersionsNode = readArray(javaCompatibility, "versions");
      if (javaVersionsNode == null) {
        throw new ApiContractException("Stream payload is missing 'javaCompatibility.versions'");
      }

      List<String> streamJavaVersions = new ArrayList<>();
      for (Object versionNode : javaVersionsNode) {
        Integer parsedVersion = toInteger(versionNode);
        if (parsedVersion == null) {
          throw new ApiContractException(
              "Stream payload field 'javaCompatibility.versions' must contain integers");
        }
        if (parsedVersion > 0) {
          versions.add(parsedVersion);
          streamJavaVersions.add(String.valueOf(parsedVersion));
        }
      }

      if (streamJavaVersions.isEmpty()) {
        throw new ApiContractException(
            "Stream payload 'javaCompatibility.versions' must contain at least one positive value");
      }

      String platformVersion = normalizeOptionalText(readText(stream, "platformVersion"));
      boolean recommended = Boolean.TRUE.equals(JsonFieldReader.readBoolean(stream, "recommended"));
      platformStreams.add(
          new PlatformStream(key, platformVersion, recommended, streamJavaVersions));
    }

    if (versions.isEmpty()) {
      throw new ApiContractException("Streams payload did not provide any Java versions");
    }

    List<String> javaVersions =
        versions.stream().sorted(Comparator.naturalOrder()).map(String::valueOf).toList();
    return new StreamsMetadata(javaVersions, List.copyOf(platformStreams));
  }

  // ── OpenAPI build tools ───────────────────────────────────────────────

  public static List<String> parseBuildToolsFromOpenApiPayload(String payload) {
    Map<String, Object> openApiPayload = parseObjectPayload(payload);
    Map<String, Object> paths = readObject(openApiPayload, "paths");
    Map<String, Object> downloadPath = paths == null ? null : readObject(paths, "/api/download");
    Map<String, Object> getOperation =
        downloadPath == null ? null : readObject(downloadPath, "get");
    List<Object> parametersNode =
        getOperation == null ? null : readArray(getOperation, "parameters");
    if (parametersNode == null) {
      throw new ApiContractException(
          "OpenAPI payload is missing '/api/download.get.parameters' array");
    }

    Set<String> buildTools = new LinkedHashSet<>();
    for (Object parameterNode : parametersNode) {
      Map<String, Object> parameter =
          castObject(parameterNode, "OpenAPI parameter entries must be objects");
      if (!"b".equals(readText(parameter, "name"))) {
        continue;
      }

      Map<String, Object> schema = readObject(parameter, "schema");
      List<Object> parameterEnum = schema == null ? null : readArray(schema, "enum");
      if (parameterEnum == null) {
        throw new ApiContractException(
            "OpenAPI payload is missing '/api/download' build tool enum");
      }

      for (String value : copyStringList(parameterEnum, "enum")) {
        String buildTool = BuildToolCodec.toUiValue(normalizeOptionalText(value));
        if (buildTool.isBlank()) {
          throw new ApiContractException("OpenAPI build tool enum must not contain blank values");
        }
        buildTools.add(buildTool);
      }
      break;
    }

    if (buildTools.isEmpty()) {
      throw new ApiContractException("OpenAPI payload did not provide any build tool enum values");
    }
    return List.copyOf(buildTools);
  }

  // ── Extensions ────────────────────────────────────────────────────────

  public static List<ExtensionDto> parseExtensionsPayload(String payload) {
    return parseExtensionsArray(parseArrayPayload(payload));
  }

  public static List<ExtensionDto> parseExtensionsArray(List<Object> root) {
    List<ExtensionDto> extensions = new ArrayList<>();
    for (Object extensionNode : root) {
      Map<String, Object> node =
          castObject(extensionNode, "Extensions payload entries must be objects");
      String id = requiredText(readText(node, "id"), "id");
      String name = requiredText(readText(node, "name"), "name");
      String shortName = resolvedShortName(readText(node, "shortName"), name);
      String category = resolvedCategory(readText(node, "category"));
      Integer order = readInteger(node, "order");
      String description = readText(node, "description");
      extensions.add(
          new ExtensionDto(
              id, name, shortName, category, order, description == null ? "" : description));
    }
    return List.copyOf(extensions);
  }

  // ── Presets ───────────────────────────────────────────────────────────

  public static Map<String, List<String>> parsePresetsPayload(String payload) {
    List<Object> root = parseArrayPayload(payload);
    Map<String, List<String>> presetsByName = new LinkedHashMap<>();
    for (Object presetNode : root) {
      Map<String, Object> preset =
          castObject(presetNode, "Presets payload entries must be objects");
      String key = normalizeOptionalText(readText(preset, "key")).toLowerCase(Locale.ROOT);
      if (key.isBlank()) {
        throw new ApiContractException("Preset payload field 'key' must not be blank");
      }
      List<String> presetExtensions = copyStringList(readArray(preset, "extensions"), "extensions");
      List<String> previous = presetsByName.putIfAbsent(key, presetExtensions);
      if (previous != null) {
        throw new ApiContractException(
            "Preset payload contains duplicate key '" + key + "' after normalization");
      }
    }
    return Map.copyOf(presetsByName);
  }

  // ── Shared JSON helpers ───────────────────────────────────────────────

  static Map<String, Object> parseObjectPayload(String payload) {
    try {
      return JsonSupport.parseObject(payload);
    } catch (IOException | RuntimeException exception) {
      throw new ApiContractException("Malformed JSON payload", exception);
    }
  }

  static List<Object> parseArrayPayload(String payload) {
    try {
      return JsonSupport.parseArray(payload);
    } catch (IOException | RuntimeException exception) {
      throw new ApiContractException("Malformed JSON payload", exception);
    }
  }

  static String readText(Map<String, Object> source, String fieldName) {
    return JsonFieldReader.readString(source, fieldName);
  }

  static Integer readInteger(Map<String, Object> source, String fieldName) {
    return JsonFieldReader.readInt(source, fieldName);
  }

  static Integer toInteger(Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof Number number)) {
      throw new ApiContractException("Malformed JSON payload");
    }
    try {
      return new java.math.BigDecimal(number.toString()).intValueExact();
    } catch (ArithmeticException | NumberFormatException e) {
      throw new ApiContractException("Malformed JSON payload");
    }
  }

  static Map<String, Object> readObject(Map<String, Object> source, String fieldName) {
    return JsonFieldReader.readObject(source, fieldName);
  }

  static List<Object> readArray(Map<String, Object> source, String fieldName) {
    return JsonFieldReader.readArray(source, fieldName);
  }

  static List<String> copyStringList(List<Object> values, String fieldName) {
    List<String> valuesCopy = new ArrayList<>();
    for (Object element : values) {
      if (!(element instanceof String stringValue)) {
        throw new ApiContractException("Field '" + fieldName + "' must contain only strings");
      }
      valuesCopy.add(stringValue);
    }
    return List.copyOf(valuesCopy);
  }

  static Map<String, Object> castObject(Object value, String errorMessage) {
    if (!(value instanceof Map<?, ?> rawObject)) {
      throw new ApiContractException(errorMessage);
    }
    Map<String, Object> object = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : rawObject.entrySet()) {
      if (!(entry.getKey() instanceof String key)) {
        throw new ApiContractException(errorMessage);
      }
      object.put(key, entry.getValue());
    }
    return object;
  }

  static List<Object> castArray(Object value, String errorMessage) {
    if (!(value instanceof List<?> rawArray)) {
      throw new ApiContractException(errorMessage);
    }
    return new ArrayList<>(rawArray);
  }

  // ── Private helpers ───────────────────────────────────────────────────

  private static String requiredText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new ApiContractException("Missing required contract field '" + fieldName + "'");
    }
    return value.trim();
  }

  private static String normalizeOptionalText(String value) {
    return value == null ? "" : value.trim();
  }

  private static String resolvedShortName(String value, String name) {
    String shortName = normalizeOptionalText(value);
    if (!shortName.isBlank()) {
      return shortName;
    }
    return name;
  }

  private static String resolvedCategory(String value) {
    String category = normalizeOptionalText(value);
    if (!category.isBlank()) {
      return category;
    }
    return "Other";
  }

  private static String normalizeKey(String key) {
    return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
  }

  private static Map<String, List<String>> parseCompatibility(
      Map<String, Object> compatibilityPayload, List<String> buildTools) {
    Map<String, List<String>> compatibility = new LinkedHashMap<>();
    if (compatibilityPayload == null) {
      return Map.of();
    }

    Map<String, BuildToolCompatibilitySelection> compatibilityByNormalizedBuildTool =
        new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : compatibilityPayload.entrySet()) {
      List<String> compatibilityValues =
          entry.getValue() == null
              ? null
              : copyStringList(
                  castArray(entry.getValue(), "Field 'compatibility' values must be arrays"),
                  "compatibility." + entry.getKey());

      String normalizedBuildTool = normalizeKey(entry.getKey());
      BuildToolCompatibilitySelection previous =
          compatibilityByNormalizedBuildTool.putIfAbsent(
              normalizedBuildTool,
              new BuildToolCompatibilitySelection(entry.getKey(), compatibilityValues));
      if (previous != null) {
        throw new ApiContractException(
            "Metadata payload compatibility contains duplicate build tool entries differing"
                + " only by case: '"
                + previous.key()
                + "' and '"
                + entry.getKey()
                + "'");
      }
    }

    for (String buildTool : buildTools) {
      BuildToolCompatibilitySelection compatibilityEntry =
          compatibilityByNormalizedBuildTool.get(normalizeKey(buildTool));
      List<String> javaVersions = compatibilityEntry == null ? null : compatibilityEntry.values();
      if (javaVersions == null) {
        throw new ApiContractException(
            "Metadata payload compatibility missing build tool entry '" + buildTool + "'");
      }
      compatibility.put(buildTool, List.copyOf(javaVersions));
    }
    return compatibility;
  }

  private static List<PlatformStream> parsePlatformStreams(List<Object> node) {
    if (node == null) {
      return List.of();
    }

    List<PlatformStream> platformStreams = new ArrayList<>();
    for (Object platformStreamNode : node) {
      Map<String, Object> stream =
          castObject(platformStreamNode, "Metadata platform stream entry must be an object");
      String key = requiredText(readText(stream, "key"), "key");
      String platformVersion = normalizeOptionalText(readText(stream, "platformVersion"));
      List<Object> javaVersionsNode = readArray(stream, "javaVersions");
      if (javaVersionsNode == null) {
        throw new ApiContractException(
            "Metadata platform stream entry is missing 'javaVersions' array");
      }
      List<String> javaVersions = copyStringList(javaVersionsNode, "platformStreams.javaVersions");
      boolean recommended = Boolean.TRUE.equals(JsonFieldReader.readBoolean(stream, "recommended"));
      platformStreams.add(new PlatformStream(key, platformVersion, recommended, javaVersions));
    }
    return List.copyOf(platformStreams);
  }
}
