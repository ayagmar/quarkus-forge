package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import dev.ayagmar.quarkusforge.persistence.ExtensionFavoritesStore;
import dev.tamboui.widgets.input.TextInputState;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogEffectsTest {

  @Test
  void applyLoadSuccessRetainsOnlyAvailableSelectionsAndFavorites() {
    CatalogFixture fixture = CatalogFixture.create("");
    fixture.navigation.select("io.quarkus:quarkus-rest");
    fixture.navigation.select("io.quarkus:quarkus-junit5");
    fixture.preferences.toggleFavorite("io.quarkus:quarkus-rest");
    fixture.preferences.toggleFavorite("io.quarkus:quarkus-junit5");

    CatalogEffects.ApplyLoadSuccessResult result =
        fixture.effects.applyLoadSuccess(
            new CatalogLoadSuccess(
                List.of(
                    new ExtensionCatalogItem("io.quarkus:quarkus-rest", "REST", "rest", "Web", 10),
                    new ExtensionCatalogItem(
                        "io.quarkus:quarkus-hibernate-orm", "Hibernate ORM", "orm", "Data", 20)),
                null,
                java.util.Map.of(),
                CatalogLoadState.loaded("live", false),
                "Loaded extension catalog from live API"),
            fixture.metadataCompatibility,
            fixture.searchQuery);

    assertThat(result.metadataCompatibility()).isEqualTo(fixture.metadataCompatibility);
    assertThat(result.followUpIntents()).containsExactly(fixture.callbacks.extensionIntent);
    assertThat(fixture.navigation.selectedExtensionIds())
        .containsExactly("io.quarkus:quarkus-rest");
    assertThat(fixture.preferences.favoriteExtensionCount()).isEqualTo(1);
    assertThat(fixture.preferences.isFavorite("io.quarkus:quarkus-rest")).isTrue();
    assertThat(fixture.projection.totalCatalogExtensionCount()).isEqualTo(2);
    assertThat(fixture.projection.filteredExtensions())
        .extracting(ExtensionCatalogItem::id)
        .containsExactly("io.quarkus:quarkus-rest", "io.quarkus:quarkus-hibernate-orm");
  }

  @Test
  void applyLoadSuccessRefreshesFormWhenMetadataChanges() {
    ForgeUiState initialState = UiTestFixtureFactory.defaultForgeUiState();
    CatalogFixture fixture = CatalogFixture.create("rest");

    CatalogEffects.ApplyLoadSuccessResult result =
        fixture.effects.applyLoadSuccess(
            new CatalogLoadSuccess(
                List.of(
                    new ExtensionCatalogItem("io.quarkus:quarkus-arc", "CDI", "cdi", "Core", 10),
                    new ExtensionCatalogItem("io.quarkus:quarkus-rest", "REST", "rest", "Web", 20)),
                initialState.metadataCompatibility().metadataSnapshot(),
                java.util.Map.of("web", List.of("io.quarkus:quarkus-rest")),
                CatalogLoadState.loaded("live", false),
                "Loaded extension catalog from live API"),
            MetadataCompatibilityContext.failure("offline"),
            fixture.searchQuery);

    assertThat(result.metadataCompatibility().loadError()).isNull();
    assertThat(fixture.projection.filteredExtensions())
        .extracting(ExtensionCatalogItem::id)
        .containsExactly("io.quarkus:quarkus-rest");
    assertThat(result.followUpIntents())
        .containsExactly(
            fixture.callbacks.extensionIntent,
            new UiIntent.FormStateUpdatedIntent(initialState.request(), initialState.validation()));
  }

  @Test
  void clearSearchFilterResetsSearchStateAndProjection() {
    CatalogFixture fixture = CatalogFixture.create("");
    TextInputState searchState = new TextInputState("rest");
    fixture.projection.refreshNow("rest", fixture.navigation, fixture.preferences, ignored -> {});

    String status = fixture.effects.clearSearchFilter(searchState);

    assertThat(status).isEqualTo("Extension search cleared");
    assertThat(searchState.text()).isEmpty();
    assertThat(searchState.cursorPosition()).isZero();
    assertThat(fixture.projection.filteredExtensions())
        .extracting(ExtensionCatalogItem::id)
        .containsExactly(
            "io.quarkus:quarkus-arc",
            "io.quarkus:quarkus-rest",
            "io.quarkus:quarkus-rest-jackson",
            "io.quarkus:quarkus-smallrye-openapi",
            "io.quarkus:quarkus-hibernate-orm",
            "io.quarkus:quarkus-jdbc-postgresql",
            "io.quarkus:quarkus-junit5");
  }

  private record CatalogFixture(
      CatalogEffects effects,
      ExtensionCatalogPreferences preferences,
      ExtensionCatalogNavigation navigation,
      ExtensionCatalogProjection projection,
      RecordingCallbacks callbacks,
      MetadataCompatibilityContext metadataCompatibility,
      String searchQuery) {
    private static CatalogFixture create(String searchQuery) {
      ForgeUiState initialState = UiTestFixtureFactory.defaultForgeUiState();
      ExtensionCatalogPreferences preferences =
          new ExtensionCatalogPreferences(ExtensionFavoritesStore.inMemory(), Runnable::run);
      ExtensionCatalogNavigation navigation = new ExtensionCatalogNavigation();
      ExtensionCatalogProjection projection =
          new ExtensionCatalogProjection(UiScheduler.immediate(), Duration.ZERO, searchQuery);
      projection.initialize(navigation, preferences);
      RecordingCallbacks callbacks = new RecordingCallbacks(initialState);
      CatalogEffects effects =
          new CatalogEffects(
              new CatalogLoadCoordinator(), preferences, navigation, projection, callbacks);
      return new CatalogFixture(
          effects,
          preferences,
          navigation,
          projection,
          callbacks,
          initialState.metadataCompatibility(),
          searchQuery);
    }
  }

  private static final class RecordingCallbacks implements CatalogEffects.Callbacks {
    private final ProjectRequest request;
    private final ValidationReport validation;
    private final UiIntent.ExtensionStateUpdatedIntent extensionIntent =
        new UiIntent.ExtensionStateUpdatedIntent(UiTestFixtureFactory.defaultExtensionView());

    private RecordingCallbacks(ForgeUiState initialState) {
      request = initialState.request();
      validation = initialState.validation();
    }

    @Override
    public UiIntent.ExtensionStateUpdatedIntent extensionStateUpdatedIntent() {
      return extensionIntent;
    }

    @Override
    public ProjectRequest currentRequest() {
      return request;
    }

    @Override
    public ProjectRequest syncMetadataSelectors(ProjectRequest request) {
      return request;
    }

    @Override
    public ValidationReport validateRequest(ProjectRequest request) {
      return validation;
    }
  }
}
