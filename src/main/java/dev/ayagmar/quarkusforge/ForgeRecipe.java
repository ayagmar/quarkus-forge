package dev.ayagmar.quarkusforge;

import java.util.List;
import java.util.Objects;

public record ForgeRecipe(
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
  public ForgeRecipe {
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

  RequestOptions toRequestOptions() {
    RequestOptions options = new RequestOptions();
    options.groupId = valueOrDefault(groupId, "org.acme");
    options.artifactId = valueOrDefault(artifactId, "quarkus-app");
    options.version = valueOrDefault(version, "1.0.0-SNAPSHOT");
    options.packageName = packageName.isBlank() ? null : packageName;
    options.outputDirectory = valueOrDefault(outputDirectory, ".");
    options.platformStream = platformStream;
    options.buildTool = valueOrDefault(buildTool, "maven");
    options.javaVersion = valueOrDefault(javaVersion, "25");
    return options;
  }

  static ForgeRecipe from(RequestOptions options, List<String> presets, List<String> extensions) {
    Objects.requireNonNull(options);
    return new ForgeRecipe(
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
