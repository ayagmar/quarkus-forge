package dev.ayagmar.quarkusforge.forge;

import dev.ayagmar.quarkusforge.domain.CliPrefill;
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

  public CliPrefill toCliPrefill() {
    return new CliPrefill(
        valueOrDefault(groupId, "org.acme"),
        valueOrDefault(artifactId, "quarkus-app"),
        valueOrDefault(version, "1.0.0-SNAPSHOT"),
        packageName.isBlank() ? null : packageName,
        valueOrDefault(outputDirectory, "."),
        platformStream,
        valueOrDefault(buildTool, "maven"),
        valueOrDefault(javaVersion, "25"));
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

  public static Forgefile from(CliPrefill prefill, List<String> presets, List<String> extensions) {
    return new Forgefile(
        prefill.groupId(),
        prefill.artifactId(),
        prefill.version(),
        prefill.packageName(),
        prefill.outputDirectory(),
        prefill.platformStream(),
        prefill.buildTool(),
        prefill.javaVersion(),
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
