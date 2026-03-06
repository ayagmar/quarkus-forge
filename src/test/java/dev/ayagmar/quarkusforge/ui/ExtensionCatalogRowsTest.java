package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExtensionCatalogRowsTest {
  private static final ExtensionCatalogItem REST =
      new ExtensionCatalogItem("io.quarkus:quarkus-rest", "REST", "rest", "Web", 10);
  private static final ExtensionCatalogItem OPENAPI =
      new ExtensionCatalogItem(
          "io.quarkus:quarkus-smallrye-openapi", "OpenAPI", "openapi", "Web", 11);
  private static final ExtensionCatalogItem JDBC =
      new ExtensionCatalogItem("io.quarkus:quarkus-jdbc-postgresql", "JDBC", "jdbc", "Data", 20);

  @Test
  void updateBuildsRowsIndexesAndRecentSectionHelpers() {
    ExtensionCatalogRows rows = new ExtensionCatalogRows();
    rows.update(List.of(REST, OPENAPI, JDBC), List.of(REST.id()), new ExtensionCatalogFilters(""));

    assertThat(rows.filteredExtensions()).containsExactly(REST, OPENAPI, JDBC);
    assertThat(rows.filteredRows()).hasSize(7);
    assertThat(rows.firstNonRecentSectionHeaderIndex()).isEqualTo(2);
    assertThat(rows.rowIndexByExtensionId(REST.id())).isEqualTo(3);
    assertThat(rows.itemAtRow(1)).isEqualTo(REST);
    assertThat(rows.itemIdAtRow(3)).isEqualTo(REST.id());
    assertThat(rows.itemAtRow(-1)).isNull();
    assertThat(rows.itemIdAtRow(null)).isNull();
    assertThat(rows.categoryTitleForRow(0)).isNull();
    assertThat(rows.categoryTitleForRow(1)).isNull();
    assertThat(rows.categoryTitleForRow(2)).isEqualTo("Web");
    assertThat(rows.categoryTitleForRow(3)).isEqualTo("Web");
    assertThat(rows.categoryTitleForRow(5)).isEqualTo("Data");
    assertThat(rows.categoryTitleForRow(6)).isEqualTo("Data");
    assertThat(rows.favoriteRowIndexes(Set.of(REST.id(), JDBC.id()))).containsExactly(1, 3, 6);
    assertThat(rows.navigationRowIndexes(null)).containsExactly(1, 3, 4, 6);
    assertThat(rows.sectionHeaderRowIndex("Data")).isEqualTo(5);
    assertThat(rows.sectionHeaderRowIndex("Missing")).isNull();
  }

  @Test
  void collapsedCategoriesSwitchNavigationAndSelectionHelpers() {
    ExtensionCatalogRows rows = new ExtensionCatalogRows();
    ExtensionCatalogFilters filters = new ExtensionCatalogFilters("");
    rows.update(List.of(REST, JDBC), List.of(), filters);

    assertThat(rows.toggleCategoryCollapse("Web")).isTrue();
    rows.update(List.of(REST, JDBC), List.of(), filters);

    assertThat(rows.selectableRowIndexes()).containsExactly(2);
    assertThat(rows.allRowIndexes()).containsExactly(0, 1, 2);
    assertThat(rows.navigationRowIndexes(0)).containsExactly(0, 1, 2);
    assertThat(rows.isCategorySectionHeaderSelected(0)).isTrue();
    assertThat(rows.isSelectedCategorySectionCollapsed(0)).isTrue();
    assertThat(rows.selectedSectionHeaderTitle(0)).isEqualTo("Web");
    assertThat(rows.selectedSectionHeaderTitle(2)).isEmpty();

    rows.retainAvailableCategories(Set.of("Data"));

    assertThat(rows.expandAllCategories()).isZero();
    rows.update(List.of(REST, JDBC), List.of(), filters);
    assertThat(rows.expandAllCategories()).isZero();
    assertThat(rows.isSelectedCategorySectionCollapsed(0)).isFalse();
  }

  @Test
  void toggleCategoryCollapseIgnoresBlankTitles() {
    ExtensionCatalogRows rows = new ExtensionCatalogRows();

    assertThat(rows.toggleCategoryCollapse(null)).isFalse();
    assertThat(rows.toggleCategoryCollapse(" ")).isFalse();
    assertThat(rows.expandAllCategories()).isZero();
  }
}
