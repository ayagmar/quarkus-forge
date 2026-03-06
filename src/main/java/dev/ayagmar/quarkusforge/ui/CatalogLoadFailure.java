package dev.ayagmar.quarkusforge.ui;

record CatalogLoadFailure(CatalogLoadState nextState, String errorMessage, String statusMessage) {}
