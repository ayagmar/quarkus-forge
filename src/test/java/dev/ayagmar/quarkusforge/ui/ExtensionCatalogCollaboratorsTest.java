package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.persistence.ExtensionFavoritesStore;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ExtensionCatalogCollaboratorsTest {

  @Test
  void deselectInSelectedOnlyModeReappliesFiltersImmediately() {
    CatalogFixture fixture = createFixture();
    fixture.select("io.quarkus:quarkus-rest");
    assertThat(fixture.navigation.selectedExtensionCount()).isEqualTo(1);

    fixture.projection.toggleSelectedOnlyFilter(
        fixture.navigation, fixture.preferences, ignored -> {});
    assertThat(fixture.projection.filteredExtensions()).hasSize(1);

    fixture.deselect("io.quarkus:quarkus-rest");

    assertThat(fixture.navigation.selectedExtensionCount()).isZero();
    assertThat(fixture.projection.filteredExtensions()).isEmpty();
  }

  @Test
  void clearSelectionInSelectedOnlyModeReappliesFiltersImmediately() {
    CatalogFixture fixture = createFixture();
    fixture.select("io.quarkus:quarkus-rest");
    fixture.projection.toggleSelectedOnlyFilter(
        fixture.navigation, fixture.preferences, ignored -> {});
    assertThat(fixture.projection.filteredExtensions()).hasSize(1);

    int cleared = fixture.navigation.clearSelectedExtensions();
    fixture.projection.reapplyAfterSelectionMutation(fixture.navigation, fixture.preferences);

    assertThat(cleared).isEqualTo(1);
    assertThat(fixture.navigation.selectedExtensionCount()).isZero();
    assertThat(fixture.projection.filteredExtensions()).isEmpty();
  }

  @Test
  void toggleFavoriteAtSelectionReportsFilteredCount() {
    CatalogFixture fixture = createFixture();
    fixture.focus("io.quarkus:quarkus-rest");
    assertThat(fixture.handler.toggleFavoriteAtSelection(ignored -> {}))
        .isEqualTo("Favorited extension: REST");
    fixture.handler.toggleFavoritesOnlyFilter(ignored -> {});
    AtomicInteger filteredCount = new AtomicInteger(-1);

    String status = fixture.handler.toggleFavoriteAtSelection(filteredCount::set);

    assertThat(status).isEqualTo("Unfavorited extension: REST");
    assertThat(filteredCount).hasValue(0);
    assertThat(fixture.projection.filteredExtensions()).isEmpty();
  }

  private static CatalogFixture createFixture() {
    ExtensionCatalogPreferences preferences =
        new ExtensionCatalogPreferences(ExtensionFavoritesStore.inMemory(), Runnable::run);
    ExtensionCatalogNavigation navigation = new ExtensionCatalogNavigation();
    ExtensionCatalogProjection projection =
        new ExtensionCatalogProjection(UiScheduler.immediate(), Duration.ZERO, "");
    projection.initialize(navigation, preferences);
    ExtensionInteractionHandler handler =
        new ExtensionInteractionHandler(preferences, navigation, projection);

    List<ExtensionCatalogItem> items =
        List.of(
            new ExtensionCatalogItem("io.quarkus:quarkus-rest", "REST", "rest", "Web", 10),
            new ExtensionCatalogItem("io.quarkus:quarkus-jdbc", "JDBC", "jdbc", "Data", 20));
    Set<String> availableIds = new LinkedHashSet<>();
    for (ExtensionCatalogItem item : items) {
      availableIds.add(item.id());
    }
    navigation.retainAvailableSelections(availableIds);
    preferences.retainAvailable(availableIds);
    projection.replaceCatalog(items, "", navigation, preferences, ignored -> {});
    return new CatalogFixture(preferences, navigation, projection, handler);
  }

  private record CatalogFixture(
      ExtensionCatalogPreferences preferences,
      ExtensionCatalogNavigation navigation,
      ExtensionCatalogProjection projection,
      ExtensionInteractionHandler handler) {
    void focus(String extensionId) {
      navigation.listState().select(projection.rows().rowIndexByExtensionId(extensionId));
    }

    void select(String extensionId) {
      focus(extensionId);
      navigation.select(extensionId);
      projection.reapplyAfterSelectionMutation(navigation, preferences);
    }

    void deselect(String extensionId) {
      focus(extensionId);
      navigation.deselect(extensionId);
      projection.reapplyAfterSelectionMutation(navigation, preferences);
    }
  }
}
