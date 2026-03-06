package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExtensionCatalogFiltersTest {
  private static final ExtensionCatalogItem REST =
      new ExtensionCatalogItem("io.quarkus:quarkus-rest", "REST", "rest", "Web", 10);
  private static final ExtensionCatalogItem JDBC =
      new ExtensionCatalogItem("io.quarkus:quarkus-jdbc-postgresql", "JDBC", "jdbc", "Data", 20);
  private static final List<ExtensionCatalogItem> ITEMS = List.of(REST, JDBC);

  @Test
  void appliesSelectedFavoritePresetAndCategoryFiltersTogether() {
    ExtensionCatalogFilters filters = new ExtensionCatalogFilters(null);
    filters.setPresetExtensionsByName(Map.of(" REST APIs ", List.of(REST.id()), " ", List.of("x")));
    filters.cyclePresetFilter(filters.availablePresetNames());
    filters.toggleSelectedOnly();
    filters.toggleFavoritesOnly();
    filters.cycleCategoryFilter(List.of("Web"));

    List<ExtensionCatalogItem> filtered =
        filters.apply(ITEMS, Set.of(REST.id()), Set.of(REST.id()));

    assertThat(filtered).containsExactly(REST);
    assertThat(filters.activePresetFilterName()).isEqualTo("rest apis");
    assertThat(filters.activeCategoryFilterTitle()).isEqualTo("Web");
  }

  @Test
  void clearingAndReconcilingFiltersDropsUnavailableSelections() {
    ExtensionCatalogFilters filters = new ExtensionCatalogFilters("rest");
    filters.updateQuery(null);
    filters.setPresetExtensionsByName(Map.of(" starter ", List.of(REST.id())));
    filters.cyclePresetFilter(filters.availablePresetNames());
    filters.cycleCategoryFilter(List.of("Web"));

    filters.setPresetExtensionsByName(Map.of("other", List.of(JDBC.id())));
    filters.reconcileAvailableCategories(Set.of("Data"));

    assertThat(filters.currentQuery()).isEmpty();
    assertThat(filters.activePresetFilterName()).isEmpty();
    assertThat(filters.activeCategoryFilterTitle()).isEmpty();
    assertThat(filters.clearPresetFilter()).isFalse();
    assertThat(filters.clearCategoryFilter()).isFalse();
  }

  @Test
  void categoryTitlesRespectPreCategoryFiltersAndPresetCyclingWraps() {
    ExtensionCatalogFilters filters = new ExtensionCatalogFilters("");
    filters.setPresetExtensionsByName(
        Map.of("rest", List.of(REST.id()), "data", List.of(JDBC.id())));
    filters.toggleFavoritesOnly();

    List<String> categories =
        filters.filterableCategoryTitles(
            new ExtensionCatalogIndex(ITEMS), Set.of(), Set.of(JDBC.id()));

    assertThat(categories).containsExactly("Data");

    List<String> presets = filters.availablePresetNames();
    assertThat(presets).containsExactly("data", "rest");

    filters.cyclePresetFilter(presets);
    assertThat(filters.activePresetFilterName()).isEqualTo("data");
    filters.cyclePresetFilter(presets);
    assertThat(filters.activePresetFilterName()).isEqualTo("rest");
    filters.cyclePresetFilter(presets);
    assertThat(filters.activePresetFilterName()).isEmpty();
  }

  @Test
  void emptyPresetAndCategoryListsResetFilters() {
    ExtensionCatalogFilters filters = new ExtensionCatalogFilters("");
    filters.setPresetExtensionsByName(Map.of("rest", List.of(REST.id())));
    filters.cyclePresetFilter(filters.availablePresetNames());
    filters.cycleCategoryFilter(List.of("Web"));

    filters.cyclePresetFilter(List.of());
    filters.cycleCategoryFilter(List.of());

    assertThat(filters.activePresetFilterName()).isEmpty();
    assertThat(filters.activeCategoryFilterTitle()).isEmpty();
  }
}
