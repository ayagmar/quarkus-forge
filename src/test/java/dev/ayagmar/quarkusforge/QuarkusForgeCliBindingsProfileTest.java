package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tamboui.tui.bindings.Actions;
import dev.tamboui.tui.bindings.Bindings;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.junit.jupiter.api.Test;

class QuarkusForgeCliBindingsProfileTest {
  @Test
  void profilePreservesNavigationBindings() {
    Bindings bindings = QuarkusForgeCli.appBindingsProfile();

    assertThat(bindings.actionFor(KeyEvent.ofChar('j'))).contains(Actions.MOVE_DOWN);
    assertThat(bindings.actionFor(KeyEvent.ofChar('k'))).contains(Actions.MOVE_UP);
    assertThat(bindings.actionFor(KeyEvent.ofChar('h'))).contains(Actions.MOVE_LEFT);
    assertThat(bindings.actionFor(KeyEvent.ofChar('l'))).contains(Actions.MOVE_RIGHT);
    assertThat(bindings.actionFor(KeyEvent.ofChar('g'))).contains(Actions.HOME);
    assertThat(bindings.actionFor(KeyEvent.ofChar('G'))).contains(Actions.END);
  }

  @Test
  void profilePreservesGlobalShortcutBindings() {
    Bindings bindings = QuarkusForgeCli.appBindingsProfile();

    assertThat(bindings.actionFor(KeyEvent.ofChar('/'))).contains("app_focus_extension_search");
    assertThat(bindings.actionFor(KeyEvent.ofChar('f', KeyModifiers.CTRL)))
        .contains("app_focus_extension_search");
    assertThat(bindings.actionFor(KeyEvent.ofChar('l', KeyModifiers.CTRL)))
        .contains("app_focus_extension_list");
    assertThat(bindings.actionFor(KeyEvent.ofChar('r', KeyModifiers.CTRL)))
        .contains("app_reload_catalog");
    assertThat(bindings.actionFor(KeyEvent.ofChar('p', KeyModifiers.CTRL)))
        .contains("app_command_palette");
    assertThat(bindings.actionFor(KeyEvent.ofChar('g', KeyModifiers.ALT)))
        .contains("app_submit_generation");
  }

  @Test
  void profilePreservesExtensionShortcutBindings() {
    Bindings bindings = QuarkusForgeCli.appBindingsProfile();

    assertThat(bindings.actionFor(KeyEvent.ofChar('v'))).contains("app_cycle_category_filter");
    assertThat(bindings.actionFor(KeyEvent.ofChar('c'))).contains("app_toggle_category");
    assertThat(bindings.actionFor(KeyEvent.ofChar('C'))).contains("app_open_all_categories");
    assertThat(bindings.actionFor(KeyEvent.ofChar('x'))).contains("app_clear_selected_extensions");
    assertThat(bindings.actionFor(KeyEvent.ofChar('f'))).contains("app_toggle_favorite");
    assertThat(bindings.actionFor(KeyEvent.ofChar('k', KeyModifiers.CTRL)))
        .contains("app_toggle_favorites_filter");
    assertThat(bindings.actionFor(KeyEvent.ofChar('j', KeyModifiers.CTRL)))
        .contains("app_jump_to_favorite");
    assertThat(bindings.actionFor(KeyEvent.ofChar('e', KeyModifiers.CTRL)))
        .contains("app_toggle_error_details");
  }
}
