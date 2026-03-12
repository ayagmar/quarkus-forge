package dev.ayagmar.quarkusforge.ui;

import java.util.Locale;
import java.util.Objects;

record ExtensionCatalogItem(
    String id,
    String name,
    String shortName,
    String category,
    Integer apiOrder,
    String description) {
  public ExtensionCatalogItem {
    id = requireValue(id, "id");
    name = requireValue(name, "name");
    shortName = requireValue(shortName, "shortName");
    category = normalizeCategory(category);
    description = description == null ? "" : description.trim();
  }

  ExtensionCatalogItem(String id, String name, String shortName) {
    this(id, name, shortName, "Other", null, "");
  }

  ExtensionCatalogItem(
      String id, String name, String shortName, String category, Integer apiOrder) {
    this(id, name, shortName, category, apiOrder, "");
  }

  String categoryKey() {
    return category.toLowerCase(Locale.ROOT);
  }

  private static String requireValue(String value, String field) {
    String normalized = Objects.requireNonNull(value, field + " must not be null").trim();
    if (normalized.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return normalized;
  }

  private static String normalizeCategory(String value) {
    if (value == null) {
      return "Other";
    }
    String normalized = value.trim();
    if (normalized.isBlank()) {
      return "Other";
    }
    return normalized;
  }
}
