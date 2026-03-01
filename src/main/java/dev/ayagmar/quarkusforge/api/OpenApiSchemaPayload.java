package dev.ayagmar.quarkusforge.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record OpenApiSchemaPayload(@JsonProperty("enum") List<String> enumValues) {}
