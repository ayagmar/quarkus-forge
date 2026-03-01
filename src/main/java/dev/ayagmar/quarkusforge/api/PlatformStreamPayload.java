package dev.ayagmar.quarkusforge.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record PlatformStreamPayload(
    String key, String platformVersion, boolean recommended, List<String> javaVersions) {}
