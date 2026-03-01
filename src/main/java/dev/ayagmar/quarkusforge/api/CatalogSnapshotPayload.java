package dev.ayagmar.quarkusforge.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record CatalogSnapshotPayload(
    int schemaVersion,
    long fetchedAtEpochMillis,
    MetadataDto metadata,
    List<ExtensionDto> extensions) {}
