package dev.ayagmar.quarkusforge.ui;

import java.util.Objects;

public record ExtensionCatalogItem(String id, String name, String shortName) {
  public ExtensionCatalogItem {
    id = requireValue(id, "id");
    name = requireValue(name, "name");
    shortName = requireValue(shortName, "shortName");
  }

  private static String requireValue(String value, String field) {
    String normalized = Objects.requireNonNull(value, field + " must not be null").trim();
    if (normalized.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return normalized;
  }
}
