package dev.ayagmar.quarkusforge;

import java.util.List;

/**
 * A shareable project template that captures all inputs needed to reproduce a Quarkus project
 * generation. The optional {@link #locked} section pins exact resolved values for deterministic
 * builds.
 *
 * <p>Example JSON:
 *
 * <pre>{@code
 * {
 *   "groupId": "com.acme",
 *   "artifactId": "my-service",
 *   "buildTool": "maven",
 *   "javaVersion": "21",
 *   "extensions": ["io.quarkus:quarkus-rest"],
 *   "locked": {
 *     "platformStream": "io.quarkus.platform:3.31",
 *     "buildTool": "maven",
 *     "javaVersion": "21",
 *     "extensions": ["io.quarkus:quarkus-rest"],
 *     "presets": []
 *   }
 * }
 * }</pre>
 */
public record Forgefile(
    String groupId,
    String artifactId,
    String version,
    String packageName,
    String outputDirectory,
    String platformStream,
    String buildTool,
    String javaVersion,
    List<String> presets,
    List<String> extensions,
    ForgefileLock locked) {

  public Forgefile {
    groupId = ForgeRecordValues.normalize(groupId);
    artifactId = ForgeRecordValues.normalize(artifactId);
    version = ForgeRecordValues.normalize(version);
    packageName = ForgeRecordValues.normalize(packageName);
    outputDirectory = ForgeRecordValues.normalize(outputDirectory);
    platformStream = ForgeRecordValues.normalize(platformStream);
    buildTool = ForgeRecordValues.normalize(buildTool);
    javaVersion = ForgeRecordValues.normalize(javaVersion);
    presets = ForgeRecordValues.copyOrEmpty(presets);
    extensions = ForgeRecordValues.copyOrEmpty(extensions);
  }

  /** Creates a Forgefile without a locked section. */
  public Forgefile(
      String groupId,
      String artifactId,
      String version,
      String packageName,
      String outputDirectory,
      String platformStream,
      String buildTool,
      String javaVersion,
      List<String> presets,
      List<String> extensions) {
    this(
        groupId,
        artifactId,
        version,
        packageName,
        outputDirectory,
        platformStream,
        buildTool,
        javaVersion,
        presets,
        extensions,
        null);
  }

  RequestOptions toRequestOptions() {
    RequestOptions options = new RequestOptions();
    options.groupId = valueOrDefault(groupId, RequestOptions.DEFAULT_GROUP_ID);
    options.artifactId = valueOrDefault(artifactId, RequestOptions.DEFAULT_ARTIFACT_ID);
    options.version = valueOrDefault(version, RequestOptions.DEFAULT_VERSION);
    options.packageName = packageName.isBlank() ? null : packageName;
    options.outputDirectory =
        valueOrDefault(outputDirectory, RequestOptions.DEFAULT_OUTPUT_DIRECTORY);
    options.platformStream = platformStream;
    options.buildTool = valueOrDefault(buildTool, RequestOptions.DEFAULT_BUILD_TOOL);
    options.javaVersion = valueOrDefault(javaVersion, RequestOptions.DEFAULT_JAVA_VERSION);
    return options;
  }

  /** Returns a new Forgefile with the locked section set or replaced. */
  Forgefile withLock(ForgefileLock lock) {
    return new Forgefile(
        groupId,
        artifactId,
        version,
        packageName,
        outputDirectory,
        platformStream,
        buildTool,
        javaVersion,
        presets,
        extensions,
        lock);
  }

  static Forgefile from(RequestOptions options, List<String> presets, List<String> extensions) {
    return new Forgefile(
        options.groupId,
        options.artifactId,
        options.version,
        options.packageName,
        options.outputDirectory,
        options.platformStream,
        options.buildTool,
        options.javaVersion,
        presets,
        extensions);
  }

  private static String valueOrDefault(String value, String fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value;
  }
}
