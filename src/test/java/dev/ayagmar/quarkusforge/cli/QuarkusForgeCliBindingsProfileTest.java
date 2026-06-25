package dev.ayagmar.quarkusforge.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.runtime.TuiBootstrapService;
import dev.ayagmar.quarkusforge.ui.AppKeyActions;
import dev.tamboui.tui.bindings.Actions;
import dev.tamboui.tui.bindings.Bindings;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.junit.jupiter.api.Test;

class QuarkusForgeCliBindingsProfileTest {
  @Test
  void appTuiConfigUsesAppBindingsProfile() {
    assertThat(TuiBootstrapService.appTuiConfig().tickRate())
        .isEqualTo(java.time.Duration.ofMillis(40));
    assertThat(TuiBootstrapService.appTuiConfig().bindings().describeBindings(Actions.MOVE_DOWN))
        .contains("j");
  }

  @Test
  void profilePreservesNavigationBindings() {
    Bindings bindings = TuiBootstrapService.appBindingsProfile();

    assertThat(bindings.matches(KeyEvent.ofChar('j'), Actions.MOVE_DOWN)).isTrue();
    assertThat(bindings.matches(KeyEvent.ofChar('k'), Actions.MOVE_UP)).isTrue();
    assertThat(bindings.matches(KeyEvent.ofChar('h'), Actions.MOVE_LEFT)).isTrue();
    assertThat(bindings.matches(KeyEvent.ofChar('l'), Actions.MOVE_RIGHT)).isTrue();
    assertThat(bindings.matches(KeyEvent.ofChar('g'), Actions.HOME)).isTrue();
    assertThat(bindings.matches(KeyEvent.ofChar('G'), Actions.END)).isTrue();
  }

  @Test
  void profilePreservesGlobalShortcutBindings() {
    Bindings bindings = TuiBootstrapService.appBindingsProfile();

    assertThat(bindings.matches(KeyEvent.ofChar('/'), AppKeyActions.FOCUS_EXTENSION_SEARCH))
        .isTrue();
    assertThat(
            bindings.matches(
                KeyEvent.ofChar('f', KeyModifiers.CTRL), AppKeyActions.FOCUS_EXTENSION_SEARCH))
        .isTrue();
    assertThat(
            bindings.matches(
                KeyEvent.ofChar('l', KeyModifiers.CTRL), AppKeyActions.FOCUS_EXTENSION_LIST))
        .isTrue();
    assertThat(
            bindings.matches(KeyEvent.ofChar('r', KeyModifiers.CTRL), AppKeyActions.RELOAD_CATALOG))
        .isTrue();
    assertThat(
            bindings.matches(
                KeyEvent.ofChar('p', KeyModifiers.CTRL), AppKeyActions.OPEN_COMMAND_PALETTE))
        .isTrue();
    assertThat(
            bindings.matches(
                KeyEvent.ofChar('g', KeyModifiers.ALT), AppKeyActions.SUBMIT_GENERATION))
        .isTrue();
  }

  @Test
  void profilePreservesExtensionShortcutBindings() {
    Bindings bindings = TuiBootstrapService.appBindingsProfile();

    assertThat(bindings.matches(KeyEvent.ofChar('v'), AppKeyActions.CATEGORY_FILTER_CYCLE))
        .isTrue();
    assertThat(bindings.matches(KeyEvent.ofChar('c'), AppKeyActions.TOGGLE_CATEGORY)).isTrue();
    assertThat(bindings.matches(KeyEvent.ofChar('C'), AppKeyActions.OPEN_ALL_CATEGORIES)).isTrue();
    assertThat(bindings.matches(KeyEvent.ofChar('x'), AppKeyActions.CLEAR_SELECTED_EXTENSIONS))
        .isTrue();
    assertThat(bindings.matches(KeyEvent.ofChar('f'), AppKeyActions.FAVORITE_TOGGLE)).isTrue();
    assertThat(
            bindings.matches(
                KeyEvent.ofChar('k', KeyModifiers.CTRL), AppKeyActions.TOGGLE_FAVORITES_FILTER))
        .isTrue();
    assertThat(
            bindings.matches(
                KeyEvent.ofChar('j', KeyModifiers.CTRL), AppKeyActions.JUMP_TO_FAVORITE))
        .isTrue();
    assertThat(
            bindings.matches(
                KeyEvent.ofChar('e', KeyModifiers.CTRL), AppKeyActions.TOGGLE_ERROR_DETAILS))
        .isTrue();
    assertThat(
            bindings.matches(
                KeyEvent.ofChar('s', KeyModifiers.ALT), AppKeyActions.TOGGLE_SELECTED_FILTER))
        .isTrue();
    assertThat(
            bindings.matches(
                KeyEvent.ofChar('n', KeyModifiers.ALT), AppKeyActions.NEXT_INVALID_FIELD))
        .isTrue();
    assertThat(
            bindings.matches(
                KeyEvent.ofChar('p', KeyModifiers.ALT), AppKeyActions.PREVIOUS_INVALID_FIELD))
        .isTrue();
  }
}
