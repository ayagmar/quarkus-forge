package dev.ayagmar.quarkusforge.forge;

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
    groupId = ForgeRecordValues.normalizeDocument(groupId);
    artifactId = ForgeRecordValues.normalizeDocument(artifactId);
    version = ForgeRecordValues.normalizeDocument(version);
    packageName = ForgeRecordValues.normalizeDocument(packageName);
    outputDirectory = ForgeRecordValues.normalizeDocument(outputDirectory);
    platformStream = ForgeRecordValues.normalizeDocument(platformStream);
    buildTool = ForgeRecordValues.normalizeDocument(buildTool);
    javaVersion = ForgeRecordValues.normalizeDocument(javaVersion);
    presets = ForgeRecordValues.copyOrNull(presets);
    extensions = ForgeRecordValues.copyOrNull(extensions);
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

  /** Returns a new Forgefile with the locked section set or replaced. */
  public Forgefile withLock(ForgefileLock lock) {
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

  /** Returns a new Forgefile with updated preset/extension selections. */
  public Forgefile withSelections(List<String> presets, List<String> extensions) {
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
        locked);
  }

  /** Returns a new Forgefile with non-null document fields from {@code overrides}. */
  public Forgefile withOverrides(Forgefile overrides) {
    if (overrides == null) {
      return this;
    }
    return new Forgefile(
        overrides.groupId != null ? overrides.groupId : groupId,
        overrides.artifactId != null ? overrides.artifactId : artifactId,
        overrides.version != null ? overrides.version : version,
        overrides.packageName != null ? overrides.packageName : packageName,
        overrides.outputDirectory != null ? overrides.outputDirectory : outputDirectory,
        overrides.platformStream != null ? overrides.platformStream : platformStream,
        overrides.buildTool != null ? overrides.buildTool : buildTool,
        overrides.javaVersion != null ? overrides.javaVersion : javaVersion,
        overrides.presets != null ? overrides.presets : presets,
        overrides.extensions != null ? overrides.extensions : extensions,
        overrides.locked != null ? overrides.locked : locked);
  }
}
