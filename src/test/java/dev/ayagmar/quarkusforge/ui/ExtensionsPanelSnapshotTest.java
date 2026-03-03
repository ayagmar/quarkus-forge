package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExtensionsPanelSnapshotTest {

  @Test
  void nullFieldsDefaultToEmptyOrZero() {
    ExtensionsPanelSnapshot snapshot =
        new ExtensionsPanelSnapshot(
            "Extensions",
            false,
            false,
            false,
            false,
            false,
            null,
            null,
            false,
            false,
            0,
            null,
            null,
            -5,
            -10,
            List.of(),
            List.of(),
            null,
            null);

    assertThat(snapshot.catalogErrorMessage()).isEmpty();
    assertThat(snapshot.catalogSource()).isEmpty();
    assertThat(snapshot.activePresetFilterName()).isEmpty();
    assertThat(snapshot.activeCategoryFilterTitle()).isEmpty();
    assertThat(snapshot.searchQuery()).isEmpty();
    assertThat(snapshot.focusedExtensionDescription()).isEmpty();
    assertThat(snapshot.filteredExtensionCount()).isZero();
    assertThat(snapshot.totalCatalogExtensionCount()).isZero();
  }

  @Test
  void nonNullFieldsArePreserved() {
    ExtensionsPanelSnapshot snapshot =
        new ExtensionsPanelSnapshot(
            "Extensions",
            true,
            true,
            false,
            true,
            true,
            "Connection error",
            "cache",
            true,
            true,
            5,
            "web",
            "Web Services",
            42,
            100,
            List.of(),
            List.of("io.quarkus:rest"),
            "rest",
            "RESTful web services");

    assertThat(snapshot.catalogErrorMessage()).isEqualTo("Connection error");
    assertThat(snapshot.catalogSource()).isEqualTo("cache");
    assertThat(snapshot.activePresetFilterName()).isEqualTo("web");
    assertThat(snapshot.activeCategoryFilterTitle()).isEqualTo("Web Services");
    assertThat(snapshot.searchQuery()).isEqualTo("rest");
    assertThat(snapshot.focusedExtensionDescription()).isEqualTo("RESTful web services");
    assertThat(snapshot.filteredExtensionCount()).isEqualTo(42);
    assertThat(snapshot.totalCatalogExtensionCount()).isEqualTo(100);
    assertThat(snapshot.selectedExtensionIds()).containsExactly("io.quarkus:rest");
  }

  @Test
  void listsAreImmutableCopies() {
    var mutableRows = new java.util.ArrayList<>(List.of(ExtensionCatalogRow.section("Web")));
    var mutableSelected = new java.util.ArrayList<>(List.of("io.quarkus:rest"));

    ExtensionsPanelSnapshot snapshot =
        new ExtensionsPanelSnapshot(
            "Extensions", false, false, false, false, false, "", "", false, false, 0, "", "", 0, 0,
            mutableRows, mutableSelected, "", "");

    mutableRows.clear();
    mutableSelected.clear();

    assertThat(snapshot.filteredRows()).hasSize(1);
    assertThat(snapshot.selectedExtensionIds()).hasSize(1);
  }
}
