package dev.ayagmar.quarkusforge.api;

public record ExtensionDto(
    String id, String name, String shortName, String category, Integer order, String description) {
  public ExtensionDto(String id, String name, String shortName) {
    this(id, name, shortName, "Other", null, "");
  }

  public ExtensionDto(String id, String name, String shortName, String category, Integer order) {
    this(id, name, shortName, category, order, "");
  }
}
