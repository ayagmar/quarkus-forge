package dev.ayagmar.quarkusforge.domain;

public record CliPrefill(
    String groupId,
    String artifactId,
    String version,
    String packageName,
    String outputDirectory,
    String buildTool,
    String javaVersion) {}
