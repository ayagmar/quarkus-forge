package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;

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

    assertThat(bindings.actionFor(KeyEvent.ofChar('j'))).contains(Actions.MOVE_DOWN);
    assertThat(bindings.actionFor(KeyEvent.ofChar('k'))).contains(Actions.MOVE_UP);
    assertThat(bindings.actionFor(KeyEvent.ofChar('h'))).contains(Actions.MOVE_LEFT);
    assertThat(bindings.actionFor(KeyEvent.ofChar('l'))).contains(Actions.MOVE_RIGHT);
    assertThat(bindings.actionFor(KeyEvent.ofChar('g'))).contains(Actions.HOME);
    assertThat(bindings.actionFor(KeyEvent.ofChar('G'))).contains(Actions.END);
  }

  @Test
  void profilePreservesGlobalShortcutBindings() {
    Bindings bindings = TuiBootstrapService.appBindingsProfile();

    assertThat(bindings.actionFor(KeyEvent.ofChar('/')))
        .contains(AppKeyActions.FOCUS_EXTENSION_SEARCH);
    assertThat(bindings.actionFor(KeyEvent.ofChar('f', KeyModifiers.CTRL)))
        .contains(AppKeyActions.FOCUS_EXTENSION_SEARCH);
    assertThat(bindings.actionFor(KeyEvent.ofChar('l', KeyModifiers.CTRL)))
        .contains(AppKeyActions.FOCUS_EXTENSION_LIST);
    assertThat(bindings.actionFor(KeyEvent.ofChar('r', KeyModifiers.CTRL)))
        .contains(AppKeyActions.RELOAD_CATALOG);
    assertThat(bindings.actionFor(KeyEvent.ofChar('p', KeyModifiers.CTRL)))
        .contains(AppKeyActions.OPEN_COMMAND_PALETTE);
    assertThat(bindings.actionFor(KeyEvent.ofChar('g', KeyModifiers.ALT)))
        .contains(AppKeyActions.SUBMIT_GENERATION);
  }

  @Test
  void profilePreservesExtensionShortcutBindings() {
    Bindings bindings = TuiBootstrapService.appBindingsProfile();

    assertThat(bindings.actionFor(KeyEvent.ofChar('v')))
        .contains(AppKeyActions.CATEGORY_FILTER_CYCLE);
    assertThat(bindings.actionFor(KeyEvent.ofChar('c'))).contains(AppKeyActions.TOGGLE_CATEGORY);
    assertThat(bindings.actionFor(KeyEvent.ofChar('C')))
        .contains(AppKeyActions.OPEN_ALL_CATEGORIES);
    assertThat(bindings.actionFor(KeyEvent.ofChar('x')))
        .contains(AppKeyActions.CLEAR_SELECTED_EXTENSIONS);
    assertThat(bindings.actionFor(KeyEvent.ofChar('f'))).contains(AppKeyActions.FAVORITE_TOGGLE);
    assertThat(bindings.actionFor(KeyEvent.ofChar('k', KeyModifiers.CTRL)))
        .contains(AppKeyActions.TOGGLE_FAVORITES_FILTER);
    assertThat(bindings.actionFor(KeyEvent.ofChar('j', KeyModifiers.CTRL)))
        .contains(AppKeyActions.JUMP_TO_FAVORITE);
    assertThat(bindings.actionFor(KeyEvent.ofChar('e', KeyModifiers.CTRL)))
        .contains(AppKeyActions.TOGGLE_ERROR_DETAILS);
  }
}
