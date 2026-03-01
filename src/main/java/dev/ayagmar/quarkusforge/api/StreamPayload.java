package dev.ayagmar.quarkusforge.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record StreamPayload(
    String key,
    String platformVersion,
    boolean recommended,
    JavaCompatibilityPayload javaCompatibility) {}
