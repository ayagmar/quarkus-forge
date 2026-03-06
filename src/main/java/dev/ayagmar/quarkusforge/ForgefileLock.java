package dev.ayagmar.quarkusforge;

import java.util.List;

/**
 * @deprecated Use {@link dev.ayagmar.quarkusforge.forge.ForgefileLock} instead.
 */
@Deprecated
public record ForgefileLock(
    String platformStream,
    String buildTool,
    String javaVersion,
    List<String> presets,
    List<String> extensions) {

  public ForgefileLock {
    dev.ayagmar.quarkusforge.forge.ForgefileLock delegate =
        new dev.ayagmar.quarkusforge.forge.ForgefileLock(
            platformStream, buildTool, javaVersion, presets, extensions);
    platformStream = delegate.platformStream();
    buildTool = delegate.buildTool();
    javaVersion = delegate.javaVersion();
    presets = delegate.presets();
    extensions = delegate.extensions();
  }

  public static ForgefileLock of(
      String platformStream,
      String buildTool,
      String javaVersion,
      List<String> presets,
      List<String> extensions) {
    return new ForgefileLock(platformStream, buildTool, javaVersion, presets, extensions);
  }

  public static ForgefileLock from(dev.ayagmar.quarkusforge.forge.ForgefileLock lock) {
    if (lock == null) {
      return null;
    }
    return new ForgefileLock(
        lock.platformStream(),
        lock.buildTool(),
        lock.javaVersion(),
        lock.presets(),
        lock.extensions());
  }

  public dev.ayagmar.quarkusforge.forge.ForgefileLock toForgefileLock() {
    return new dev.ayagmar.quarkusforge.forge.ForgefileLock(
        platformStream, buildTool, javaVersion, presets, extensions);
  }
}
