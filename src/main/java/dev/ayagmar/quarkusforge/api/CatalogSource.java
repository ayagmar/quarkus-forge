package dev.ayagmar.quarkusforge.api;

public enum CatalogSource {
  LIVE("live"),
  CACHE("cache");

  private final String label;

  CatalogSource(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
