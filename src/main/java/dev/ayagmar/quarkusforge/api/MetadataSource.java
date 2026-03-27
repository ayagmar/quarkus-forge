package dev.ayagmar.quarkusforge.api;

public enum MetadataSource {
  LIVE("live"),
  SNAPSHOT("bundled snapshot"),
  CACHE("cache");

  private final String label;

  MetadataSource(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
