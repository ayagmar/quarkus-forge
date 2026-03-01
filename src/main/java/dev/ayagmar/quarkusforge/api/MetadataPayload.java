package dev.ayagmar.quarkusforge.api;

import java.util.List;
import java.util.Map;

record MetadataPayload(
    List<String> javaVersions,
    List<String> buildTools,
    Map<String, List<String>> compatibility,
    List<PlatformStreamPayload> platformStreams) {}
