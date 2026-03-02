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
    String javaVersion,
    SelectorInfo platformStreamInfo,
    SelectorInfo buildToolInfo,
    SelectorInfo javaVersionInfo) {
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
    if (platformStreamInfo == null) platformStreamInfo = SelectorInfo.EMPTY;
    if (buildToolInfo == null) buildToolInfo = SelectorInfo.EMPTY;
    if (javaVersionInfo == null) javaVersionInfo = SelectorInfo.EMPTY;
  }

  record SelectorInfo(int selectedIndex, int totalOptions) {
    static final SelectorInfo EMPTY = new SelectorInfo(0, 0);
  }
}
