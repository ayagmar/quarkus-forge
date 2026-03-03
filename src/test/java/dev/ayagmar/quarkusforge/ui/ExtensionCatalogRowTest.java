package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ExtensionCatalogRowTest {

  @Test
  void sectionFactoryCreatesHeaderRow() {
    ExtensionCatalogRow row = ExtensionCatalogRow.section("Web");

    assertThat(row.label()).isEqualTo("Web");
    assertThat(row.extension()).isNull();
    assertThat(row.collapsed()).isFalse();
    assertThat(row.hiddenCount()).isZero();
    assertThat(row.totalCount()).isZero();
    assertThat(row.isSectionHeader()).isTrue();
  }

  @Test
  void sectionWithCollapsedAndHiddenCount() {
    ExtensionCatalogRow row = ExtensionCatalogRow.section("Core", true, 5);

    assertThat(row.label()).isEqualTo("Core");
    assertThat(row.collapsed()).isTrue();
    assertThat(row.hiddenCount()).isEqualTo(5);
    assertThat(row.totalCount()).isEqualTo(5);
    assertThat(row.isSectionHeader()).isTrue();
  }

  @Test
  void sectionWithDifferentTotalAndHiddenCount() {
    ExtensionCatalogRow row = ExtensionCatalogRow.section("Core", true, 3, 10);

    assertThat(row.hiddenCount()).isEqualTo(3);
    assertThat(row.totalCount()).isEqualTo(10);
  }

  @Test
  void itemFactoryCreatesNonHeaderRow() {
    ExtensionCatalogItem extension = new ExtensionCatalogItem("io.quarkus:rest", "REST", "rest");
    ExtensionCatalogRow row = ExtensionCatalogRow.item(extension);

    assertThat(row.label()).isEqualTo("REST");
    assertThat(row.extension()).isSameAs(extension);
    assertThat(row.collapsed()).isFalse();
    assertThat(row.hiddenCount()).isZero();
    assertThat(row.isSectionHeader()).isFalse();
  }

  @Test
  void blankLabelThrowsIllegalArgumentException() {
    assertThatThrownBy(() -> ExtensionCatalogRow.section("  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("blank");
  }

  @Test
  void nullLabelThrowsNullPointerException() {
    assertThatThrownBy(() -> ExtensionCatalogRow.section(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void negativeHiddenCountThrowsIllegalArgumentException() {
    assertThatThrownBy(() -> ExtensionCatalogRow.section("Web", false, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("hiddenCount");
  }

  @Test
  void itemRowWithCollapsedThrowsIllegalArgumentException() {
    ExtensionCatalogItem ext = new ExtensionCatalogItem("io.quarkus:rest", "REST", "rest");
    assertThatThrownBy(() -> new ExtensionCatalogRow("REST", ext, true, 0, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Item rows");
  }

  @Test
  void itemRowWithHiddenCountThrowsIllegalArgumentException() {
    ExtensionCatalogItem ext = new ExtensionCatalogItem("io.quarkus:rest", "REST", "rest");
    assertThatThrownBy(() -> new ExtensionCatalogRow("REST", ext, false, 1, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Item rows");
  }

  @Test
  void labelIsTrimmed() {
    ExtensionCatalogRow row = ExtensionCatalogRow.section("  Web  ");

    assertThat(row.label()).isEqualTo("Web");
  }
}
