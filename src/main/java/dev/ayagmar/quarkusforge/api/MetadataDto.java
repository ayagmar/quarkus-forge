package dev.ayagmar.quarkusforge.api;

import java.util.List;

public record MetadataDto(List<String> javaVersions, List<String> buildTools) {
  public MetadataDto {
    javaVersions = List.copyOf(javaVersions);
    buildTools = List.copyOf(buildTools);
  }
}
