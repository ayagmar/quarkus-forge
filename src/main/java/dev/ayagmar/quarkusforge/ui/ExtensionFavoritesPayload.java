package dev.ayagmar.quarkusforge.ui;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
record ExtensionFavoritesPayload(
    int schemaVersion, Set<String> favoriteExtensionIds, List<String> recentExtensionIds) {}
