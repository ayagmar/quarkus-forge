package dev.ayagmar.quarkusforge.api;

import java.util.List;

public record GenerationRequest(
    String groupId,
    String artifactId,
    String version,
    String buildTool,
    String javaVersion,
    List<String> extensions) {
  public GenerationRequest {
    extensions = List.copyOf(extensions);
  }
}
