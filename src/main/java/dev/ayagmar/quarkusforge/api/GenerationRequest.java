package dev.ayagmar.quarkusforge.api;

import java.util.List;
import java.util.Objects;

public record GenerationRequest(
    String groupId,
    String artifactId,
    String version,
    String platformStream,
    String buildTool,
    String javaVersion,
    List<String> extensions) {
  public GenerationRequest(
      String groupId,
      String artifactId,
      String version,
      String buildTool,
      String javaVersion,
      List<String> extensions) {
    this(groupId, artifactId, version, "", buildTool, javaVersion, extensions);
  }

  public GenerationRequest {
    extensions = List.copyOf(Objects.requireNonNull(extensions, "extensions must not be null"));
  }
}
