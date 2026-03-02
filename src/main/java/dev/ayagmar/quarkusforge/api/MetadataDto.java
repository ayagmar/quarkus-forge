package dev.ayagmar.quarkusforge.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public record MetadataDto(
    List<String> javaVersions,
    List<String> buildTools,
    Map<String, List<String>> compatibility,
    List<PlatformStream> platformStreams) {
  public MetadataDto(
      List<String> javaVersions, List<String> buildTools, Map<String, List<String>> compatibility) {
    this(javaVersions, buildTools, compatibility, List.of());
  }

  public MetadataDto {
    javaVersions = copyNormalized(javaVersions);
    buildTools = copyNormalized(buildTools);

    Map<String, List<String>> copy = new LinkedHashMap<>();
    compatibility.forEach((key, value) -> copy.put(normalizeKey(key), copyNormalized(value)));
    compatibility = Map.copyOf(copy);

    Map<String, PlatformStream> streamByKey = new LinkedHashMap<>();
    for (PlatformStream stream : Objects.requireNonNull(platformStreams)) {
      String normalizedKey = normalizeKey(stream.key());
      streamByKey.putIfAbsent(normalizedKey, stream);
    }
    platformStreams = List.copyOf(streamByKey.values());
  }

  public String recommendedPlatformStreamKey() {
    for (PlatformStream stream : platformStreams) {
      if (stream.recommended()) {
        return stream.key();
      }
    }
    return platformStreams.isEmpty() ? "" : platformStreams.getFirst().key();
  }

  public PlatformStream findPlatformStream(String streamKey) {
    String normalizedKey = normalizeKey(streamKey);
    if (normalizedKey.isBlank()) {
      return null;
    }
    for (PlatformStream stream : platformStreams) {
      if (normalizeKey(stream.key()).equals(normalizedKey)) {
        return stream;
      }
    }
    return null;
  }

  static List<String> copyNormalized(List<String> values) {
    Objects.requireNonNull(values);
    return values.stream().map(MetadataDto::normalizeText).toList();
  }

  static String normalizeText(String value) {
    return value == null ? "" : value.trim();
  }

  private static String normalizeKey(String value) {
    return normalizeText(value).toLowerCase(Locale.ROOT);
  }
}
