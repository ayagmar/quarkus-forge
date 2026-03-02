package dev.ayagmar.quarkusforge;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

final class RequestOptions {

  /**
   * Injected by Picocli after parsing; null when the object is constructed manually (e.g. in tests
   * or when calling {@link #defaults()}).
   */
  @Spec CommandSpec spec;

  static final String DEFAULT_GROUP_ID = "org.acme";
  static final String DEFAULT_ARTIFACT_ID = "quarkus-app";
  static final String DEFAULT_VERSION = "1.0.0-SNAPSHOT";
  static final String DEFAULT_OUTPUT_DIRECTORY = ".";
  static final String DEFAULT_PLATFORM_STREAM = "";
  static final String DEFAULT_BUILD_TOOL = "maven";

  /**
   * Default Java version. Update this constant each release when the recommended LTS advances. The
   * live metadata from {@code code.quarkus.io} is used at runtime to recommend the appropriate
   * stream, but this value is the fallback when metadata is unavailable.
   */
  static final String DEFAULT_JAVA_VERSION = "25";

  static final String OPT_GROUP_ID = "--group-id";
  static final String OPT_ARTIFACT_ID = "--artifact-id";
  static final String OPT_VERSION = "--project-version";
  static final String OPT_PACKAGE_NAME = "--package-name";
  static final String OPT_OUTPUT_DIR = "--output-dir";
  static final String OPT_PLATFORM_STREAM = "--platform-stream";
  static final String OPT_BUILD_TOOL = "--build-tool";
  static final String OPT_JAVA_VERSION = "--java-version";

  @Option(
      names = {"-g", OPT_GROUP_ID},
      defaultValue = DEFAULT_GROUP_ID,
      description = "Maven group id")
  String groupId;

  @Option(
      names = {"-a", OPT_ARTIFACT_ID},
      defaultValue = DEFAULT_ARTIFACT_ID,
      description = "Maven artifact id")
  String artifactId;

  @Option(
      names = {"-v", OPT_VERSION},
      defaultValue = DEFAULT_VERSION,
      description = "Project version")
  String version;

  @Option(
      names = {"-p", OPT_PACKAGE_NAME},
      description = "Base package name (defaults from group/artifact)")
  String packageName;

  @Option(
      names = {"-o", OPT_OUTPUT_DIR},
      defaultValue = DEFAULT_OUTPUT_DIRECTORY,
      description = "Output parent directory (project path resolves to <output-dir>/<artifact-id>)")
  String outputDirectory;

  @Option(
      names = {"-S", OPT_PLATFORM_STREAM},
      defaultValue = DEFAULT_PLATFORM_STREAM,
      description = "Quarkus platform stream key (metadata-driven, optional)")
  String platformStream;

  @Option(
      names = {"-b", OPT_BUILD_TOOL},
      defaultValue = DEFAULT_BUILD_TOOL,
      description = "Build tool (metadata-driven)")
  String buildTool;

  @Option(
      names = {"-j", OPT_JAVA_VERSION},
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

  /**
   * Returns {@code true} when the user explicitly supplied the option on the command line. Falls
   * back to value-equality detection when {@link #spec} is not available (tests, defaults()).
   *
   * @param optionName canonical option name, e.g. {@code "--group-id"}
   * @param currentValue the current field value
   * @param defaultValue the compiled-in default value
   */
  boolean isExplicitlySet(String optionName, String currentValue, String defaultValue) {
    if (spec != null && spec.commandLine() != null && spec.commandLine().getParseResult() != null) {
      return spec.commandLine().getParseResult().hasMatchedOption(optionName);
    }
    // Fallback: treat value != default as "explicitly set"
    return !java.util.Objects.equals(currentValue, defaultValue);
  }
}
