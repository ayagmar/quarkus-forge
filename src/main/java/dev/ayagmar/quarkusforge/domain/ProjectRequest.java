package dev.ayagmar.quarkusforge.domain;

public record ProjectRequest(
    String groupId,
    String artifactId,
    String version,
    String packageName,
    String outputDirectory) {}
