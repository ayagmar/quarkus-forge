package dev.ayagmar.quarkusforge.ui;

record UserPreferencesPayload(
    int schemaVersion,
    String groupId,
    String artifactId,
    String version,
    String packageName,
    String outputDirectory,
    String platformStream,
    String buildTool,
    String javaVersion) {}
