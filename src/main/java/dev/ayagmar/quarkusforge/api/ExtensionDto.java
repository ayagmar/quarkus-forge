package dev.ayagmar.quarkusforge.api;

public record ExtensionDto(
    String id, String name, String shortName, String category, Integer order) {
  public ExtensionDto(String id, String name, String shortName) {
    this(id, name, shortName, "Other", null);
  }
}
