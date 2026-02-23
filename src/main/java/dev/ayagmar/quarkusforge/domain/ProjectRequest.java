package dev.ayagmar.quarkusforge.domain;

public record ProjectRequest(
    String groupId,
    String artifactId,
    String version,
    String packageName,
    String outputDirectory,
    String platformStream,
    String buildTool,
    String javaVersion) {
  public ProjectRequest(
      String groupId,
      String artifactId,
      String version,
      String packageName,
      String outputDirectory,
      String buildTool,
      String javaVersion) {
    this(groupId, artifactId, version, packageName, outputDirectory, "", buildTool, javaVersion);
  }
}
