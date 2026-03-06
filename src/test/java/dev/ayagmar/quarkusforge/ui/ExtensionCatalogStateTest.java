package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.api.ExtensionFavoritesStore;
import dev.tamboui.tui.event.KeyEvent;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ExtensionCatalogStateTest {

  @Test
  void deselectInSelectedOnlyModeReappliesFiltersImmediately() {
    ExtensionCatalogState state = createDefaultState();

    assertThat(state.handleListKeys(KeyEvent.ofChar(' '), ignored -> {})).isTrue();
    assertThat(state.selectedExtensionCount()).isEqualTo(1);

    state.toggleSelectedOnlyFilter(ignored -> {});
    assertThat(state.filteredExtensions()).hasSize(1);

    assertThat(state.handleListKeys(KeyEvent.ofChar(' '), ignored -> {})).isTrue();

    assertThat(state.selectedExtensionCount()).isZero();
    assertThat(state.filteredExtensions()).isEmpty();
  }

  @Test
  void clearSelectionInSelectedOnlyModeReappliesFiltersImmediately() {
    ExtensionCatalogState state = createDefaultState();

    assertThat(state.handleListKeys(KeyEvent.ofChar(' '), ignored -> {})).isTrue();
    state.toggleSelectedOnlyFilter(ignored -> {});
    assertThat(state.filteredExtensions()).hasSize(1);

    int cleared = state.clearSelectedExtensions();

    assertThat(cleared).isEqualTo(1);
    assertThat(state.selectedExtensionCount()).isZero();
    assertThat(state.filteredExtensions()).isEmpty();
  }

  @Test
  void toggleFavoriteAtSelectionReportsFilteredCount() {
    ExtensionCatalogState state =
        new ExtensionCatalogState(
            UiScheduler.immediate(),
            Duration.ZERO,
            "",
            ExtensionFavoritesStore.inMemory(),
            Runnable::run);
    state.replaceCatalog(
        List.of(
            new ExtensionCatalogItem("io.quarkus:quarkus-rest", "REST", "rest", "Web", 10),
            new ExtensionCatalogItem("io.quarkus:quarkus-jdbc", "JDBC", "jdbc", "Data", 20)),
        "",
        ignored -> {});
    state.listState().select(1);
    assertThat(state.toggleFavoriteAtSelection(ignored -> {}).changed()).isTrue();
    state.toggleFavoritesOnlyFilter(ignored -> {});
    AtomicInteger filteredCount = new AtomicInteger(-1);

    FavoriteToggleResult result = state.toggleFavoriteAtSelection(filteredCount::set);

    assertThat(result.changed()).isTrue();
    assertThat(result.favoriteNow()).isFalse();
    assertThat(filteredCount).hasValue(0);
    assertThat(state.filteredExtensions()).isEmpty();
  }

  private static ExtensionCatalogState createDefaultState() {
    return new ExtensionCatalogState(UiScheduler.immediate(), Duration.ZERO, "");
  }
}
