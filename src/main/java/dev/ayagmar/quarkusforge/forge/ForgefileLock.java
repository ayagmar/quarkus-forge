package dev.ayagmar.quarkusforge.forge;

import java.util.List;

/**
 * The locked section of a {@link Forgefile}. Pins exact resolved values for deterministic builds.
 * When present, drift detection compares generation inputs against these pinned values.
 */
public record ForgefileLock(
    String platformStream,
    String buildTool,
    String javaVersion,
    List<String> presets,
    List<String> extensions) {

  public ForgefileLock {
    platformStream = ForgeRecordValues.normalize(platformStream);
    buildTool = ForgeRecordValues.normalize(buildTool);
    javaVersion = ForgeRecordValues.normalize(javaVersion);
    presets = ForgeRecordValues.copyOrEmpty(presets);
    extensions = ForgeRecordValues.copyOrEmpty(extensions);
  }

  public static ForgefileLock of(
      String platformStream,
      String buildTool,
      String javaVersion,
      List<String> presets,
      List<String> extensions) {
    return new ForgefileLock(platformStream, buildTool, javaVersion, presets, extensions);
  }
}
