package dev.ayagmar.quarkusforge.persistence;

import java.util.List;
import java.util.Set;

record ExtensionFavoritesPayload(
    int schemaVersion, Set<String> favoriteExtensionIds, List<String> recentExtensionIds) {}
