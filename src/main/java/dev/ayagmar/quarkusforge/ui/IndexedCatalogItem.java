package dev.ayagmar.quarkusforge.ui;

record IndexedCatalogItem(
    ExtensionCatalogItem item,
    String id,
    String name,
    String shortName,
    String category,
    int apiOrderRank) {}
