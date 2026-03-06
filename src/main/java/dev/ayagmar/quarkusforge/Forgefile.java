package dev.ayagmar.quarkusforge;

import java.util.List;

/**
 * @deprecated Use {@link dev.ayagmar.quarkusforge.forge.Forgefile} instead.
 */
@Deprecated
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
    dev.ayagmar.quarkusforge.forge.Forgefile delegate =
        new dev.ayagmar.quarkusforge.forge.Forgefile(
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
            locked == null ? null : locked.toForgefileLock());
    groupId = delegate.groupId();
    artifactId = delegate.artifactId();
    version = delegate.version();
    packageName = delegate.packageName();
    outputDirectory = delegate.outputDirectory();
    platformStream = delegate.platformStream();
    buildTool = delegate.buildTool();
    javaVersion = delegate.javaVersion();
    presets = delegate.presets();
    extensions = delegate.extensions();
    locked = ForgefileLock.from(delegate.locked());
  }

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

  public static Forgefile from(dev.ayagmar.quarkusforge.forge.Forgefile forgefile) {
    if (forgefile == null) {
      return null;
    }
    return new Forgefile(
        forgefile.groupId(),
        forgefile.artifactId(),
        forgefile.version(),
        forgefile.packageName(),
        forgefile.outputDirectory(),
        forgefile.platformStream(),
        forgefile.buildTool(),
        forgefile.javaVersion(),
        forgefile.presets(),
        forgefile.extensions(),
        ForgefileLock.from(forgefile.locked()));
  }

  public dev.ayagmar.quarkusforge.forge.Forgefile toForgefile() {
    return new dev.ayagmar.quarkusforge.forge.Forgefile(
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
        locked == null ? null : locked.toForgefileLock());
  }
}
