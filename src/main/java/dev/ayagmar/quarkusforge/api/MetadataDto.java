package dev.ayagmar.quarkusforge.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record MetadataDto(
    List<String> javaVersions, List<String> buildTools, Map<String, List<String>> compatibility) {
  public MetadataDto {
    javaVersions = List.copyOf(javaVersions);
    buildTools = List.copyOf(buildTools);

    Map<String, List<String>> copy = new LinkedHashMap<>();
    compatibility.forEach((key, value) -> copy.put(key, List.copyOf(value)));
    compatibility = Map.copyOf(copy);
  }
}
