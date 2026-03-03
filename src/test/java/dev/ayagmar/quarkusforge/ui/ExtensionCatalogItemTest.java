package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ExtensionCatalogItemTest {

  @Test
  void validItemPreservesFields() {
    ExtensionCatalogItem item =
        new ExtensionCatalogItem("io.quarkus:rest", "REST", "rest", "Web", 1, "RESTful services");

    assertThat(item.id()).isEqualTo("io.quarkus:rest");
    assertThat(item.name()).isEqualTo("REST");
    assertThat(item.shortName()).isEqualTo("rest");
    assertThat(item.category()).isEqualTo("Web");
    assertThat(item.apiOrder()).isEqualTo(1);
    assertThat(item.description()).isEqualTo("RESTful services");
  }

  @Test
  void threeArgConstructorDefaultsCategoryAndOrder() {
    ExtensionCatalogItem item = new ExtensionCatalogItem("io.quarkus:arc", "CDI", "cdi");

    assertThat(item.category()).isEqualTo("Other");
    assertThat(item.apiOrder()).isNull();
    assertThat(item.description()).isEmpty();
  }

  @Test
  void fiveArgConstructorDefaultsDescription() {
    ExtensionCatalogItem item = new ExtensionCatalogItem("io.quarkus:arc", "CDI", "cdi", "Core", 5);

    assertThat(item.description()).isEmpty();
    assertThat(item.category()).isEqualTo("Core");
    assertThat(item.apiOrder()).isEqualTo(5);
  }

  @Test
  void nullCategoryNormalizesToOther() {
    ExtensionCatalogItem item =
        new ExtensionCatalogItem("io.quarkus:arc", "CDI", "cdi", null, null, "");

    assertThat(item.category()).isEqualTo("Other");
  }

  @Test
  void blankCategoryNormalizesToOther() {
    ExtensionCatalogItem item =
        new ExtensionCatalogItem("io.quarkus:arc", "CDI", "cdi", "   ", null, "");

    assertThat(item.category()).isEqualTo("Other");
  }

  @Test
  void categoryKeyIsLowerCase() {
    ExtensionCatalogItem item =
        new ExtensionCatalogItem("io.quarkus:arc", "CDI", "cdi", "Web Services", null, "");

    assertThat(item.categoryKey()).isEqualTo("web services");
  }

  @Test
  void nullIdThrowsNullPointerException() {
    assertThatThrownBy(() -> new ExtensionCatalogItem(null, "CDI", "cdi"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("id");
  }

  @Test
  void blankNameThrowsIllegalArgumentException() {
    assertThatThrownBy(() -> new ExtensionCatalogItem("io.quarkus:arc", "  ", "cdi"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name");
  }

  @Test
  void blankShortNameThrowsIllegalArgumentException() {
    assertThatThrownBy(() -> new ExtensionCatalogItem("io.quarkus:arc", "CDI", "  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("shortName");
  }

  @Test
  void nullDescriptionNormalizesToEmpty() {
    ExtensionCatalogItem item =
        new ExtensionCatalogItem("io.quarkus:arc", "CDI", "cdi", "Core", null, null);

    assertThat(item.description()).isEmpty();
  }

  @Test
  void descriptionIsWhitespaceTrimmed() {
    ExtensionCatalogItem item =
        new ExtensionCatalogItem("io.quarkus:arc", "CDI", "cdi", "Core", null, "  trimmed  ");

    assertThat(item.description()).isEqualTo("trimmed");
  }

  @Test
  void idIsWhitespaceTrimmed() {
    ExtensionCatalogItem item = new ExtensionCatalogItem("  io.quarkus:arc  ", "CDI", "cdi");

    assertThat(item.id()).isEqualTo("io.quarkus:arc");
  }
}
