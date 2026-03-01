package dev.ayagmar.quarkusforge;

import java.util.List;

public record ForgeLock(
    String platformStream,
    String buildTool,
    String javaVersion,
    List<String> presets,
    List<String> extensions) {
  public ForgeLock {
    platformStream = ForgeRecordValues.normalize(platformStream);
    buildTool = ForgeRecordValues.normalize(buildTool);
    javaVersion = ForgeRecordValues.normalize(javaVersion);
    presets = ForgeRecordValues.copyOrEmpty(presets);
    extensions = ForgeRecordValues.copyOrEmpty(extensions);
  }

  public static ForgeLock from(
      String platformStream,
      String buildTool,
      String javaVersion,
      List<String> presets,
      List<String> extensions) {
    return new ForgeLock(platformStream, buildTool, javaVersion, presets, extensions);
  }
}
