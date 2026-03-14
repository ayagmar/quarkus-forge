package dev.ayagmar.quarkusforge.cli;

import dev.ayagmar.quarkusforge.domain.CliPrefill;
import dev.ayagmar.quarkusforge.domain.ProjectInputDefaults;
import dev.ayagmar.quarkusforge.forge.Forgefile;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParseResult;

final class RequestOptions {
  private final Set<String> prefilledOptions = new HashSet<>();
  private final Set<String> matchedOptions = new HashSet<>();

  static final String DEFAULT_GROUP_ID = ProjectInputDefaults.GROUP_ID;
  static final String DEFAULT_ARTIFACT_ID = ProjectInputDefaults.ARTIFACT_ID;
  static final String DEFAULT_VERSION = ProjectInputDefaults.VERSION;
  static final String DEFAULT_OUTPUT_DIRECTORY = ProjectInputDefaults.OUTPUT_DIRECTORY;
  static final String DEFAULT_PLATFORM_STREAM = ProjectInputDefaults.PLATFORM_STREAM;
  static final String DEFAULT_BUILD_TOOL = ProjectInputDefaults.BUILD_TOOL;

  /**
   * Default Java version. Update this constant each release when the recommended LTS advances. The
   * live metadata from {@code code.quarkus.io} is used at runtime to recommend the appropriate
   * stream, but this value is the fallback when metadata is unavailable.
   */
  static final String DEFAULT_JAVA_VERSION = ProjectInputDefaults.JAVA_VERSION;

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

  CliPrefill toCliPrefill() {
    return new CliPrefill(
        groupId,
        artifactId,
        version,
        packageName,
        outputDirectory,
        platformStream,
        buildTool,
        javaVersion);
  }

  CliPrefill toExplicitCliPrefill() {
    return new CliPrefill(
        explicitValue(OPT_GROUP_ID, groupId, DEFAULT_GROUP_ID),
        explicitValue(OPT_ARTIFACT_ID, artifactId, DEFAULT_ARTIFACT_ID),
        explicitValue(OPT_VERSION, version, DEFAULT_VERSION),
        explicitValue(OPT_PACKAGE_NAME, packageName, null),
        explicitValue(OPT_OUTPUT_DIR, outputDirectory, DEFAULT_OUTPUT_DIRECTORY),
        explicitValue(OPT_PLATFORM_STREAM, platformStream, DEFAULT_PLATFORM_STREAM),
        explicitValue(OPT_BUILD_TOOL, buildTool, DEFAULT_BUILD_TOOL),
        explicitValue(OPT_JAVA_VERSION, javaVersion, DEFAULT_JAVA_VERSION));
  }

  Forgefile toExplicitTemplate() {
    CliPrefill explicitPrefill = toExplicitCliPrefill();
    return new Forgefile(
        explicitPrefill.groupId(),
        explicitPrefill.artifactId(),
        explicitPrefill.version(),
        explicitPrefill.packageName(),
        explicitPrefill.outputDirectory(),
        explicitPrefill.platformStream(),
        explicitPrefill.buildTool(),
        explicitPrefill.javaVersion(),
        null,
        null);
  }

  static RequestOptions fromCliPrefill(CliPrefill prefill) {
    return fromCliPrefill(prefill, true);
  }

  static RequestOptions explicitFromCliPrefill(CliPrefill prefill) {
    return fromCliPrefill(prefill, false);
  }

  private static RequestOptions fromCliPrefill(CliPrefill prefill, boolean markPrefilled) {
    RequestOptions options = defaults();
    if (prefill == null) {
      return options;
    }
    options.groupId =
        options.applyCliValue(OPT_GROUP_ID, prefill.groupId(), options.groupId, markPrefilled);
    options.artifactId =
        options.applyCliValue(
            OPT_ARTIFACT_ID, prefill.artifactId(), options.artifactId, markPrefilled);
    options.version =
        options.applyCliValue(OPT_VERSION, prefill.version(), options.version, markPrefilled);
    options.packageName =
        options.applyCliValue(OPT_PACKAGE_NAME, prefill.packageName(), null, markPrefilled);
    options.outputDirectory =
        options.applyCliValue(
            OPT_OUTPUT_DIR, prefill.outputDirectory(), options.outputDirectory, markPrefilled);
    options.platformStream =
        options.applyCliValue(
            OPT_PLATFORM_STREAM, prefill.platformStream(), options.platformStream, markPrefilled);
    options.buildTool =
        options.applyCliValue(
            OPT_BUILD_TOOL, prefill.buildTool(), options.buildTool, markPrefilled);
    options.javaVersion =
        options.applyCliValue(
            OPT_JAVA_VERSION, prefill.javaVersion(), options.javaVersion, markPrefilled);
    return options;
  }

  /**
   * Returns {@code true} when the user explicitly supplied the option on the command line. Falls
   * back to value-equality detection when no parse result has been recorded yet (tests,
   * defaults()).
   *
   * @param optionName canonical option name, e.g. {@code "--group-id"}
   * @param currentValue the current field value
   * @param defaultValue the compiled-in default value
   */
  boolean isExplicitlySet(String optionName, String currentValue, String defaultValue) {
    if (matchedOptions.contains(optionName)) {
      return true;
    }
    if (prefilledOptions.contains(optionName)) {
      return false;
    }
    // Fallback: treat value != default as "explicitly set"
    return !Objects.equals(currentValue, defaultValue);
  }

  private String explicitValue(String optionName, String currentValue, String defaultValue) {
    if (!isExplicitlySet(optionName, currentValue, defaultValue)) {
      return null;
    }
    return currentValue;
  }

  void recordMatchedOptions(ParseResult parseResult) {
    matchedOptions.clear();
    if (parseResult == null) {
      return;
    }
    parseResult
        .matchedOptions()
        .forEach(optionSpec -> matchedOptions.add(optionSpec.longestName()));
  }

  private String applyCliValue(
      String optionName, String cliValue, String fallbackValue, boolean markPrefilled) {
    if (cliValue == null || cliValue.isBlank()) {
      return fallbackValue;
    }
    if (markPrefilled) {
      prefilledOptions.add(optionName);
    }
    return cliValue;
  }
}
