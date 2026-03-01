package dev.ayagmar.quarkusforge.api;

import java.util.List;

record CatalogSnapshotPayload(
    int schemaVersion,
    long fetchedAtEpochMillis,
    MetadataDto metadata,
    List<ExtensionDto> extensions) {}
