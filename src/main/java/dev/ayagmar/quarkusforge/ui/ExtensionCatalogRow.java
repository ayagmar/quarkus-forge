package dev.ayagmar.quarkusforge.ui;

import java.util.Objects;

record ExtensionCatalogRow(
    String label, ExtensionCatalogItem extension, boolean collapsed, int hiddenCount) {
  static ExtensionCatalogRow section(String label) {
    return section(label, false, 0);
  }

  static ExtensionCatalogRow section(String label, boolean collapsed, int hiddenCount) {
    return new ExtensionCatalogRow(label, null, collapsed, hiddenCount);
  }

  static ExtensionCatalogRow item(ExtensionCatalogItem extension) {
    return new ExtensionCatalogRow(extension.name(), extension, false, 0);
  }

  ExtensionCatalogRow {
    label = Objects.requireNonNull(label).trim();
    if (label.isBlank()) {
      throw new IllegalArgumentException("label must not be blank");
    }
    if (hiddenCount < 0) {
      throw new IllegalArgumentException("hiddenCount must be >= 0");
    }
    if (extension != null && (collapsed || hiddenCount > 0)) {
      throw new IllegalArgumentException("Item rows must not carry collapsed metadata");
    }
    if (extension == null && !collapsed && hiddenCount > 0) {
      throw new IllegalArgumentException("Visible section headers must not carry hidden counts");
    }
  }

  boolean isSectionHeader() {
    return extension == null;
  }
}
