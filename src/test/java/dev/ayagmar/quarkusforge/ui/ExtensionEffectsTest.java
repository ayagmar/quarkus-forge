package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import dev.ayagmar.quarkusforge.persistence.ExtensionFavoritesStore;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.input.TextInputState;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExtensionEffectsTest {

  @Test
  void toggleSelectionAtCursorUpdatesSelectionAndReturnsStatusIntent() {
    ExtensionFixture fixture = ExtensionFixture.create("");
    fixture.focus("io.quarkus:quarkus-rest");

    List<UiIntent> intents =
        fixture.effects.executeCommand(UiIntent.ExtensionCommand.TOGGLE_SELECTION_AT_CURSOR);

    assertThat(fixture.navigation.selectedExtensionIds())
        .containsExactly("io.quarkus:quarkus-rest");
    assertThat(intents)
        .containsExactly(
            fixture.callbacks.extensionIntent,
            new UiIntent.ExtensionStatusIntent("Toggled extension: REST"));
  }

  @Test
  void clearSearchDelegatesToCatalogEffectsAndRefreshesProjection() {
    ExtensionFixture fixture = ExtensionFixture.create("rest");
    fixture.searchState.setText("rest");
    fixture.projection.refreshNow("rest", fixture.navigation, fixture.preferences, ignored -> {});

    List<UiIntent> intents = fixture.effects.executeCommand(UiIntent.ExtensionCommand.CLEAR_SEARCH);

    assertThat(fixture.searchState.text()).isEmpty();
    assertThat(fixture.projection.filteredExtensions()).hasSize(7);
    assertThat(intents)
        .containsExactly(
            fixture.callbacks.extensionIntent,
            new UiIntent.ExtensionStatusIntent("Extension search cleared"));
  }

  @Test
  void disableSelectedFilterReturnsDisabledStatusWhenFilterIsAlreadyOff() {
    ExtensionFixture fixture = ExtensionFixture.create("");

    List<UiIntent> intents =
        fixture.effects.executeCommand(UiIntent.ExtensionCommand.DISABLE_SELECTED_FILTER);

    assertThat(fixture.projection.selectedOnlyFilterEnabled()).isFalse();
    assertThat(intents)
        .containsExactly(
            fixture.callbacks.extensionIntent,
            new UiIntent.ExtensionStatusIntent("Selected-only view disabled"));
  }

  @Test
  void applyNavigationKeyReturnsExtensionUpdateWhenHandled() {
    ExtensionFixture fixture = ExtensionFixture.create("");
    fixture.focus("io.quarkus:quarkus-rest");
    Integer initialRow = fixture.navigation.selectedRow();

    List<UiIntent> intents = fixture.effects.applyNavigationKey(KeyEvent.ofKey(KeyCode.DOWN));

    assertThat(intents).containsExactly(fixture.callbacks.extensionIntent);
    assertThat(fixture.navigation.selectedRow()).isNotEqualTo(initialRow);
  }

  private record ExtensionFixture(
      ExtensionEffects effects,
      ExtensionCatalogPreferences preferences,
      ExtensionCatalogNavigation navigation,
      ExtensionCatalogProjection projection,
      TextInputState searchState,
      RecordingExtensionCallbacks callbacks) {
    private static ExtensionFixture create(String searchQuery) {
      ForgeUiState initialState = UiTestFixtureFactory.defaultForgeUiState();
      ExtensionCatalogPreferences preferences =
          new ExtensionCatalogPreferences(ExtensionFavoritesStore.inMemory(), Runnable::run);
      ExtensionCatalogNavigation navigation = new ExtensionCatalogNavigation();
      ExtensionCatalogProjection projection =
          new ExtensionCatalogProjection(UiScheduler.immediate(), Duration.ZERO, searchQuery);
      projection.initialize(navigation, preferences);
      projection.replaceCatalog(
          catalogItems(), searchQuery, navigation, preferences, ignored -> {});

      TextInputState searchState = new TextInputState(searchQuery);
      RecordingExtensionCallbacks callbacks = new RecordingExtensionCallbacks(searchState);
      ExtensionEffects effects =
          new ExtensionEffects(
              new CatalogEffects(
                  new CatalogLoadCoordinator(),
                  preferences,
                  navigation,
                  projection,
                  new CatalogCallbacks(initialState)),
              new ExtensionInteractionHandler(preferences, navigation, projection),
              preferences,
              navigation,
              projection,
              callbacks);
      return new ExtensionFixture(
          effects, preferences, navigation, projection, searchState, callbacks);
    }

    private static List<ExtensionCatalogItem> catalogItems() {
      return List.of(
          new ExtensionCatalogItem("io.quarkus:quarkus-arc", "CDI", "cdi", "Core", 10),
          new ExtensionCatalogItem("io.quarkus:quarkus-rest", "REST", "rest", "Web", 20),
          new ExtensionCatalogItem(
              "io.quarkus:quarkus-rest-jackson", "REST Jackson", "rest-jackson", "Web", 30),
          new ExtensionCatalogItem(
              "io.quarkus:quarkus-smallrye-openapi", "OpenAPI", "openapi", "Web", 40),
          new ExtensionCatalogItem(
              "io.quarkus:quarkus-hibernate-orm", "Hibernate ORM", "orm", "Data", 50),
          new ExtensionCatalogItem(
              "io.quarkus:quarkus-jdbc-postgresql",
              "JDBC PostgreSQL",
              "jdbc-postgresql",
              "Data",
              60),
          new ExtensionCatalogItem(
              "io.quarkus:quarkus-junit5", "JUnit 5", "junit5", "Testing", 70));
    }

    private void focus(String extensionId) {
      navigation.listState().select(projection.rows().rowIndexByExtensionId(extensionId));
    }
  }

  private static final class RecordingExtensionCallbacks implements ExtensionEffects.Callbacks {
    private final TextInputState searchState;
    private final UiIntent.ExtensionStateUpdatedIntent extensionIntent =
        new UiIntent.ExtensionStateUpdatedIntent(UiTestFixtureFactory.defaultExtensionView());

    private RecordingExtensionCallbacks(TextInputState searchState) {
      this.searchState = searchState;
    }

    @Override
    public UiIntent.ExtensionStateUpdatedIntent extensionStateUpdatedIntent() {
      return extensionIntent;
    }

    @Override
    public TextInputState extensionSearchState() {
      return searchState;
    }

    @Override
    public String currentStatusMessage() {
      return "Ready";
    }
  }

  private static final class CatalogCallbacks implements CatalogEffects.Callbacks {
    private final ProjectRequest request;
    private final ValidationReport validation;

    private CatalogCallbacks(ForgeUiState initialState) {
      request = initialState.request();
      validation = initialState.validation();
    }

    @Override
    public UiIntent.ExtensionStateUpdatedIntent extensionStateUpdatedIntent() {
      return new UiIntent.ExtensionStateUpdatedIntent(UiTestFixtureFactory.defaultExtensionView());
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
