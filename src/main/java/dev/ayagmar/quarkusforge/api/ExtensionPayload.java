package dev.ayagmar.quarkusforge.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record ExtensionPayload(String id, String name, String shortName, String category, Integer order) {}
