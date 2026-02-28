package dev.ayagmar.quarkusforge.ui;

import java.util.Objects;

record MetadataPanelSnapshot(
    String title,
    boolean focused,
    boolean invalid,
    String groupId,
    String artifactId,
    String version,
    String packageName,
    String outputDir,
    String platformStream,
    String buildTool,
    String javaVersion) {
  MetadataPanelSnapshot {
    title = Objects.requireNonNull(title);
    groupId = groupId == null ? "" : groupId;
    artifactId = artifactId == null ? "" : artifactId;
    version = version == null ? "" : version;
    packageName = packageName == null ? "" : packageName;
    outputDir = outputDir == null ? "" : outputDir;
    platformStream = platformStream == null ? "" : platformStream;
    buildTool = buildTool == null ? "" : buildTool;
    javaVersion = javaVersion == null ? "" : javaVersion;
  }
}
