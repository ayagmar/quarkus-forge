package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tamboui.tui.event.KeyEvent;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ExtensionCatalogStateTest {

  @Test
  void deselectInSelectedOnlyModeReappliesFiltersImmediately() {
    ExtensionCatalogState state =
        new ExtensionCatalogState(UiScheduler.immediate(), Duration.ZERO, "");

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
    ExtensionCatalogState state =
        new ExtensionCatalogState(UiScheduler.immediate(), Duration.ZERO, "");

    assertThat(state.handleListKeys(KeyEvent.ofChar(' '), ignored -> {})).isTrue();
    state.toggleSelectedOnlyFilter(ignored -> {});
    assertThat(state.filteredExtensions()).hasSize(1);

    int cleared = state.clearSelectedExtensions();

    assertThat(cleared).isEqualTo(1);
    assertThat(state.selectedExtensionCount()).isZero();
    assertThat(state.filteredExtensions()).isEmpty();
  }
}
