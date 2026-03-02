package dev.ayagmar.quarkusforge;

import picocli.CommandLine.Option;

final class RequestOptions {
  static final String DEFAULT_GROUP_ID = "org.acme";
  static final String DEFAULT_ARTIFACT_ID = "quarkus-app";
  static final String DEFAULT_VERSION = "1.0.0-SNAPSHOT";
  static final String DEFAULT_OUTPUT_DIRECTORY = ".";
  static final String DEFAULT_PLATFORM_STREAM = "";
  static final String DEFAULT_BUILD_TOOL = "maven";
  static final String DEFAULT_JAVA_VERSION = "25";

  @Option(
      names = {"-g", "--group-id"},
      defaultValue = DEFAULT_GROUP_ID,
      description = "Maven group id")
  String groupId;

  @Option(
      names = {"-a", "--artifact-id"},
      defaultValue = DEFAULT_ARTIFACT_ID,
      description = "Maven artifact id")
  String artifactId;

  @Option(
      names = {"-v", "--project-version"},
      defaultValue = DEFAULT_VERSION,
      description = "Project version")
  String version;

  @Option(
      names = {"-p", "--package-name"},
      description = "Base package name (defaults from group/artifact)")
  String packageName;

  @Option(
      names = {"-o", "--output-dir"},
      defaultValue = DEFAULT_OUTPUT_DIRECTORY,
      description = "Output parent directory (project path resolves to <output-dir>/<artifact-id>)")
  String outputDirectory;

  @Option(
      names = {"-S", "--platform-stream"},
      defaultValue = DEFAULT_PLATFORM_STREAM,
      description = "Quarkus platform stream key (metadata-driven, optional)")
  String platformStream;

  @Option(
      names = {"-b", "--build-tool"},
      defaultValue = DEFAULT_BUILD_TOOL,
      description = "Build tool (metadata-driven)")
  String buildTool;

  @Option(
      names = {"-j", "--java-version"},
      defaultValue = DEFAULT_JAVA_VERSION,
      description = "Java version for generated project (metadata-driven)")
  String javaVersion;

  /** Returns a RequestOptions instance populated with all default values. */
  static RequestOptions defaults() {
    RequestOptions defaults = new RequestOptions();
    defaults.groupId = DEFAULT_GROUP_ID;
    defaults.artifactId = DEFAULT_ARTIFACT_ID;
    defaults.version = DEFAULT_VERSION;
    defaults.packageName = null;
    defaults.outputDirectory = DEFAULT_OUTPUT_DIRECTORY;
    defaults.platformStream = DEFAULT_PLATFORM_STREAM;
    defaults.buildTool = DEFAULT_BUILD_TOOL;
    defaults.javaVersion = DEFAULT_JAVA_VERSION;
    return defaults;
  }
}
