package dev.ayagmar.quarkusforge.ui;

import java.util.List;
import java.util.Set;

record ExtensionFavoritesPayload(
    int schemaVersion, Set<String> favoriteExtensionIds, List<String> recentExtensionIds) {}
