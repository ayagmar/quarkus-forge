package dev.ayagmar.quarkusforge.ui;

import java.util.Objects;

record ExtensionCatalogRow(String label, ExtensionCatalogItem extension) {
  static ExtensionCatalogRow section(String label) {
    return new ExtensionCatalogRow(label, null);
  }

  static ExtensionCatalogRow item(ExtensionCatalogItem extension) {
    return new ExtensionCatalogRow(extension.name(), extension);
  }

  ExtensionCatalogRow {
    label = Objects.requireNonNull(label).trim();
    if (label.isBlank()) {
      throw new IllegalArgumentException("label must not be blank");
    }
  }

  boolean isSectionHeader() {
    return extension == null;
  }
}
