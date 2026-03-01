package dev.ayagmar.quarkusforge.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
record MetadataPayload(
    List<String> javaVersions,
    List<String> buildTools,
    Map<String, List<String>> compatibility,
    List<PlatformStreamPayload> platformStreams) {}
