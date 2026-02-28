package dev.ayagmar.quarkusforge;

import picocli.CommandLine.Option;

final class RequestOptions {
  @Option(
      names = {"-g", "--group-id"},
      defaultValue = "org.acme",
      description = "Maven group id")
  String groupId;

  @Option(
      names = {"-a", "--artifact-id"},
      defaultValue = "quarkus-app",
      description = "Maven artifact id")
  String artifactId;

  @Option(
      names = {"-v", "--project-version"},
      defaultValue = "1.0.0-SNAPSHOT",
      description = "Project version")
  String version;

  @Option(
      names = {"-p", "--package-name"},
      description = "Base package name (defaults from group/artifact)")
  String packageName;

  @Option(
      names = {"-o", "--output-dir"},
      defaultValue = ".",
      description = "Output parent directory (project path resolves to <output-dir>/<artifact-id>)")
  String outputDirectory;

  @Option(
      names = {"-S", "--platform-stream"},
      defaultValue = "",
      description = "Quarkus platform stream key (metadata-driven, optional)")
  String platformStream;

  @Option(
      names = {"-b", "--build-tool"},
      defaultValue = "maven",
      description = "Build tool (metadata-driven)")
  String buildTool;

  @Option(
      names = {"-j", "--java-version"},
      defaultValue = "25",
      description = "Java version for generated project (metadata-driven)")
  String javaVersion;
}
