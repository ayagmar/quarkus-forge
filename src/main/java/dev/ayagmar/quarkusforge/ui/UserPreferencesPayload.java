package dev.ayagmar.quarkusforge.ui;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
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
